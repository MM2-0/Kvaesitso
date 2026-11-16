// Phase 4 (T16) bun static + WebSocket back-channel server.
//
// One process per Kotlin test, bound to a random port; the PlaywrightDriver
// passes the port back to the harness pages as `?wsPort=…`. PCM frames sent
// over the WS as binary messages get appended to `--out-pcm`. A textual
// `done` message flips the server's `done` flag so the test driver can
// poll it via the `/state` endpoint and tear down cleanly.
//
// Argv:
//   --port  <int>   listen port; 0 picks a random one (logged on stdout)
//   --root  <dir>   directory to serve static files from (= dist/)
//   --out-pcm <path>  file to append received PCM frames to
//
// Stdout (machine-readable, single line then blank line):
//   port=<int>
//   ready
//
// Errors go to stderr; non-zero exit code on fatal startup failure.

import { type ServerWebSocket } from "bun";
import { mkdirSync, openSync, closeSync, writeSync, existsSync } from "node:fs";
import { dirname, resolve, join } from "node:path";

interface Args {
    port: number;
    root: string;
    outPcm: string;
}

function parseArgs(): Args {
    const args = process.argv.slice(2);
    let port = 0;
    let root = "";
    let outPcm = "";
    for (let i = 0; i < args.length; i++) {
        const a = args[i];
        if (a === "--port") port = Number(args[++i]);
        else if (a === "--root") root = args[++i];
        else if (a === "--out-pcm") outPcm = args[++i];
        else throw new Error(`unknown arg: ${a}`);
    }
    if (!root) throw new Error("--root is required");
    if (!outPcm) throw new Error("--out-pcm is required");
    return { port, root, outPcm: resolve(outPcm) };
}

const args = parseArgs();
mkdirSync(dirname(args.outPcm), { recursive: true });
// Truncate any prior file: the harness page reopens each run.
const fd = openSync(args.outPcm, "w");

let done = false;
const wsClients = new Set<ServerWebSocket<unknown>>();

const contentType = (path: string): string => {
    if (path.endsWith(".html")) return "text/html; charset=utf-8";
    if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
    if (path.endsWith(".mjs")) return "application/javascript; charset=utf-8";
    if (path.endsWith(".json")) return "application/json; charset=utf-8";
    if (path.endsWith(".css")) return "text/css; charset=utf-8";
    if (path.endsWith(".wasm")) return "application/wasm";
    return "application/octet-stream";
};

const server = Bun.serve({
    port: args.port,
    hostname: "127.0.0.1",
    fetch(req, srv) {
        const url = new URL(req.url);
        if (url.pathname === "/pcm") {
            // WebSocket upgrade for the PCM back-channel.
            if (srv.upgrade(req)) return;
            return new Response("expected websocket upgrade", { status: 400 });
        }
        if (url.pathname === "/state") {
            return new Response(JSON.stringify({ done }), {
                headers: { "content-type": "application/json" },
            });
        }
        // Static file serve out of root/.
        let path = url.pathname === "/" ? "/listen.html" : url.pathname;
        const filePath = join(args.root, path.replace(/^\/+/, ""));
        // Reject path traversal attempts.
        if (!filePath.startsWith(resolve(args.root))) {
            return new Response("forbidden", { status: 403 });
        }
        if (!existsSync(filePath)) {
            return new Response("not found: " + path, { status: 404 });
        }
        const file = Bun.file(filePath);
        return new Response(file, {
            headers: {
                "content-type": contentType(filePath),
                // No-cache so a `bun build` rebuild between Playwright runs
                // is picked up immediately.
                "cache-control": "no-store",
            },
        });
    },
    websocket: {
        message(ws, message) {
            wsClients.add(ws);
            if (typeof message === "string") {
                if (message === "done") {
                    done = true;
                    console.log("[server] received `done`");
                }
                return;
            }
            // Binary PCM frame — append raw bytes to the out file.
            const buf = message instanceof ArrayBuffer ? new Uint8Array(message) : new Uint8Array(message.buffer, message.byteOffset, message.byteLength);
            writeSync(fd, buf);
        },
        open(ws) {
            wsClients.add(ws);
        },
        close(ws) {
            wsClients.delete(ws);
        },
    },
});

// Machine-readable handshake for PlaywrightDriver.
process.stdout.write(`port=${server.port}\n`);
process.stdout.write("ready\n");

// Clean up the fd on Ctrl-C / parent kill so we don't leak it.
const shutdown = () => {
    try {
        closeSync(fd);
    } catch { /* ignore */ }
    server.stop(true);
    process.exit(0);
};
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
