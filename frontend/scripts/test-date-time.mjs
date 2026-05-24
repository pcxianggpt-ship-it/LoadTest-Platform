import assert from "node:assert/strict";
import { mkdir, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import * as esbuild from "esbuild";

const outdir = join(tmpdir(), "loadtest-platform-date-time-test");
await rm(outdir, { recursive: true, force: true });
await mkdir(outdir, { recursive: true });

await esbuild.build({
  entryPoints: [fileURLToPath(new URL("../src/utils/dateTime.ts", import.meta.url))],
  bundle: true,
  format: "esm",
  platform: "node",
  outfile: join(outdir, "dateTime.mjs"),
});

const { formatDisplayDateTime } = await import(`file:///${join(outdir, "dateTime.mjs").replaceAll("\\", "/")}`);

assert.equal(formatDisplayDateTime("2026-05-23T14:22:05.000Z"), "2026-05-23 22:22:05");
assert.equal(formatDisplayDateTime("2026-05-23T22:21:21.129755584+08:00"), "2026-05-23 22:21:21");
assert.equal(formatDisplayDateTime(""), "");
assert.equal(formatDisplayDateTime(null), "");

await rm(outdir, { recursive: true, force: true });
