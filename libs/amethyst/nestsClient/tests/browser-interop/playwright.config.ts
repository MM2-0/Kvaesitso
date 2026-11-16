import { defineConfig } from "@playwright/test";

// Phase 4 (T16) browser-interop Playwright config.
//
// One-off Chromium spawn per Kotlin test. We disable the default test
// projects + reporters (the runner is invoked headlessly from the
// PlaywrightDriver Kotlin shim with `--reporter list` for stdout
// streaming).
//
// Chromium flags:
//   --enable-quic                              — required for WebTransport.
//   --ignore-certificate-errors                — accept the self-signed
//                                                 cert moq-relay generates
//                                                 with --tls-generate.
//   --enable-features=AutoplayPolicy=NoUserGestureRequired
//                                              — let AudioContext.resume()
//                                                 succeed without a user
//                                                 gesture (we're headless).
//   --enable-blink-features=WebTransport
//                                              — defensively re-enable in
//                                                 case the build disables
//                                                 the blink feature flag
//                                                 by default.

export default defineConfig({
    testDir: "./tests",
    fullyParallel: false,
    workers: 1,
    forbidOnly: !!process.env.CI,
    retries: 0,
    reporter: process.env.PLAYWRIGHT_REPORTER ?? "list",
    timeout: 120_000,
    use: {
        headless: true,
        trace: "off",
        video: "off",
        screenshot: "off",
        launchOptions: {
            args: [
                "--enable-quic",
                "--ignore-certificate-errors",
                "--enable-features=AutoplayPolicy=NoUserGestureRequired",
                "--enable-blink-features=WebTransport",
                // Disable network sandbox so WebTransport over loopback
                // doesn't trip the network service sandbox in headless.
                "--disable-features=IsolateOrigins,site-per-process",
            ],
        },
    },
    projects: [
        {
            name: "chromium",
            use: { browserName: "chromium" },
        },
    ],
});
