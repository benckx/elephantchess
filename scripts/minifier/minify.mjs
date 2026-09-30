// Minifies a web asset read from stdin and writes the minified result to stdout.
//
// The asset type is given as the first CLI argument:
//   node minify.mjs js   -> minifies JavaScript with SWC (https://swc.rs)
//   node minify.mjs css  -> minifies CSS with Lightning CSS (https://lightningcss.dev)
//
// SWC is the same engine the previously used Toptal JavaScript endpoint
// (https://www.toptal.com/developers/javascript-minifier) is based on, and Lightning CSS
// replaces the Toptal CSS endpoint. Running both locally avoids the rate-limited network
// round-trips and works with modern syntax such as ES6 private class fields.
import { minifySync } from "@swc/core";
import { transform } from "lightningcss";

function readStdin() {
    const chunks = [];
    return new Promise((resolve, reject) => {
        process.stdin.on("data", (chunk) => chunks.push(chunk));
        process.stdin.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
        process.stdin.on("error", reject);
    });
}

function minifyJs(source) {
    const { code } = minifySync(source, {
        compress: true,
        mangle: true,
    });
    return code;
}

function minifyCss(source) {
    const { code } = transform({
        filename: "input.css",
        code: Buffer.from(source),
        minify: true,
    });
    return code.toString("utf8");
}

const type = process.argv[2];

if (type !== "js" && type !== "css") {
    process.stderr.write(`usage: node minify.mjs <js|css>\n`);
    process.exit(1);
}

const source = await readStdin();

try {
    const code = type === "js" ? minifyJs(source) : minifyCss(source);
    process.stdout.write(code);
} catch (error) {
    process.stderr.write(`${String(error?.message ?? error)}\n`);
    process.exit(1);
}
