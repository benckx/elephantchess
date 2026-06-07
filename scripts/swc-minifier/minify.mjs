// Minifies JavaScript read from stdin and writes the minified result to stdout.
//
// It uses SWC (https://swc.rs) directly, which is the same engine the previously
// used Toptal endpoint (https://www.toptal.com/developers/javascript-minifier) is
// based on. Running it locally avoids the rate-limited network round-trip and works
// with modern syntax such as ES6 private class fields.
import { minifySync } from "@swc/core";

function readStdin() {
    const chunks = [];
    return new Promise((resolve, reject) => {
        process.stdin.on("data", (chunk) => chunks.push(chunk));
        process.stdin.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
        process.stdin.on("error", reject);
    });
}

const source = await readStdin();

try {
    const { code } = minifySync(source, {
        compress: true,
        mangle: true,
    });
    process.stdout.write(code);
} catch (error) {
    process.stderr.write(`${String(error?.message ?? error)}\n`);
    process.exit(1);
}
