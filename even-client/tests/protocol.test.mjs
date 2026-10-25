import { test } from "node:test";
import assert from "node:assert/strict";
import ts from "typescript";
import { readFileSync } from "node:fs";
async function load(path) {
  const source = ts.transpileModule(
    readFileSync(new URL(path, import.meta.url), "utf8"),
    {
      compilerOptions: {
        module: ts.ModuleKind.ESNext,
        target: ts.ScriptTarget.ES2022,
      },
    },
  ).outputText;
  return import(
    "data:text/javascript;base64," + Buffer.from(source).toString("base64")
  );
}
const { frame, imagePixels, gesture } = await load("../src/protocol.ts");
const { encode1BitBmp } = await load("../src/BmpEncoder.ts");
const base = {
  version: 1,
  width: 32,
  height: 24,
  romHash: "a".repeat(64),
    inputEpoch: "12345678-1234-1234-1234-123456789abc:0",
  dpower: false,
  ready: true,
  state: "RUNNING",
  rows: Array(24).fill(".".repeat(32)).join("\n"),
};
test("strict frame dimensions and marker vocabulary", () => {
  assert.deepEqual(frame(base), base);
  for (const value of [
    null,
    {},
    { ...base, width: 33 },
    { ...base, rows: base.rows + "\n" },
    { ...base, rows: base.rows.replace(".", "x") },
    { ...base, romHash: "wrong" },
    { ...base, inputEpoch: undefined },
    { ...base, inputEpoch: "wrong" },
  ])
    assert.throws(() => frame(value));
});
test("classic icons fit without interpolation, D-Power crops to actual LCD", () => {
  const rows = base.rows.split("\n");
  rows[0] = "i" + ".".repeat(31);
  rows[4] = "p" + ".".repeat(31);
  rows[23] = "d" + ".".repeat(31);
  assert.equal(
    imagePixels({ ...base, rows: rows.join("\n") }).reduce((a, b) => a + b),
    32,
  );
  assert.equal(
    imagePixels({ ...base, dpower: true, rows: rows.join("\n") }).reduce(
      (a, b) => a + b,
    ),
    36,
  );
});
test("one-bit BMP has correct header, dimensions and padded bottom-up rows", () => {
  const pixels = new Uint8Array(20000);
  pixels[0] = 1;
  const bmp = Uint8Array.from(encode1BitBmp(200, 100, pixels));
  const v = new DataView(bmp.buffer);
  assert.equal(String.fromCharCode(...bmp.slice(0, 2)), "BM");
  assert.equal(v.getUint32(2, true), bmp.length);
  assert.equal(v.getInt32(18, true), 200);
  assert.equal(v.getInt32(22, true), 100);
  assert.equal(v.getUint16(28, true), 1);
  const offset = v.getUint32(10, true);
  assert.equal(bmp[offset + 99 * 28], 128);
  assert.equal(bmp[offset], 0);
});
test("gesture mapping uses only actual device buttons", () => {
  assert.deepEqual(
    ["press", "up", "down", "double"].map((x) => gesture(x, false)),
    ["a", "b", "c", "b"],
  );
  assert.deepEqual(
    ["press", "up", "down", "double"].map((x) => gesture(x, true)),
    ["up", "left", "right", "down"],
  );
});
