import { test, expect } from "@playwright/test";

// Driver test that the Kotlin `PlaywrightDriver` invokes once per
// scenario via `npx playwright test`. Every parameter is passed via
// environment variables (NPM_BROWSER_HARNESS_*) so the same single test
// can serve every BrowserInteropTest scenario without us writing one
// playwright spec per scenario.
//
// Required env:
//   NESTS_HARNESS_URL — http://127.0.0.1:<bunPort>/listen.html (or publish.html)
//   NESTS_TIMEOUT_MS  — overall page timeout (default 60_000)
//
// The test:
//   1. opens the URL,
//   2. waits for `body[data-state="done"]` (or "error", which fails),
//   3. dumps the status text + console logs back as the test failure message
//      so `--reporter list` surfaces them in stdout the Kotlin caller reads.

const harnessUrl = process.env.NESTS_HARNESS_URL;
const timeoutMs = Number(process.env.NESTS_TIMEOUT_MS ?? "60000");

test.describe("nests-browser-interop", () => {
    test.skip(!harnessUrl, "NESTS_HARNESS_URL not set");

    test("harness runs to completion", async ({ page }) => {
        const consoleLines: string[] = [];
        page.on("console", (msg) => {
            consoleLines.push(`[${msg.type()}] ${msg.text()}`);
        });
        page.on("pageerror", (err) => {
            consoleLines.push(`[pageerror] ${err.message}\n${err.stack ?? ""}`);
        });

        await page.goto(harnessUrl!, { waitUntil: "domcontentloaded" });
        // Wait for the harness page to flip to either "done" (success)
        // or "error" (page-side fatal). Don't rely on `waitForFunction`'s
        // own polling cadence because Chromium on a busy CI runner can
        // miss a transient status; spin in 100 ms ticks ourselves.
        const finalState = await page.waitForFunction(
            () => {
                const s = (document.body as HTMLBodyElement).dataset.state;
                return s === "done" || s === "error" ? s : null;
            },
            null,
            { timeout: timeoutMs, polling: 100 },
        );
        const state = await finalState.evaluate((v) => v as string);
        const status = await page.locator("#status").textContent();
        const meta = await page.evaluate(() => ({
            framesDecoded: (window as any).__framesDecoded,
            moqVersion: (window as any).__moqVersion,
            // I14 instrumentation: total WebCodecs `output()` callbacks
            // (warmup frames included) and total `error()` callbacks.
            // A T8 regression that leaks `OpusHead` into a normal audio
            // frame trips `decoderErrors` deterministically; the FFT
            // peak in I1 catches the silent-tolerance variant.
            decoderOutputs: (window as any).__decoderOutputs,
            decoderErrors: (window as any).__decoderErrors,
            // Browser I7 / publish-baseline instrumentation: total
            // encoded frames the publisher pumped, and the count of
            // moq-lite session reconnect cycles the page completed.
            framesIn: (window as any).__framesIn,
            cycles: (window as any).__publishCycle,
        }));
        // Always print a summary line — Kotlin parses this for follow-up
        // assertions (e.g. moq-lite-03 ALPN echo for I15).
        console.log(
            JSON.stringify({
                state,
                status,
                meta,
                logs: consoleLines.slice(-50),
            }),
        );
        if (state === "error") {
            throw new Error(`harness reached error state: ${status}\n\nlogs:\n${consoleLines.join("\n")}`);
        }
        expect(state).toBe("done");
    });
});
