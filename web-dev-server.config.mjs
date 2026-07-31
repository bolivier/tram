import { chromeLauncher } from "@web/test-runner-chrome";
import fs from "fs";

// In manual mode the flag keeps the browser open across shadow hot-reloads
// instead of the session completing and killing it.
const SKIP_COMPLETING = !!process.env.RHIZOME__SKIP_COMPLETING_TEST_RUNNER;

export default {
    rootDir: "test/browser",
    testRunnerHtml: () => {
        const html = fs.readFileSync(
            "./test/browser/test-runner-index.html",
            "utf8",
        );
        if (SKIP_COMPLETING) {
            return html.replace(
                /RHIZOME__SKIP_COMPLETING_TEST_RUNNER:\s*false/,
                "RHIZOME__SKIP_COMPLETING_TEST_RUNNER: true",
            );
        }
        return html;
    },
    // Options must go under launchOptions; chromeLauncher ignores unknown
    // top-level keys silently (the old repo's `headless: false` never worked).
    browsers: [
        chromeLauncher({
            launchOptions: {
                headless: !process.env.RHIZOME__HEADED,
            },
        }),
    ],
    nodeResolve: true,
    debug: false,
    browserLogs: true,
    files: ["test/browser/browser-tests/**/*_test.js"],
};
