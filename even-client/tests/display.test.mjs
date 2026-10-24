import { test } from "node:test";
import assert from "node:assert/strict";
import ts from "typescript";
import { readFileSync } from "node:fs";
const stub = `export const waitForEvenAppBridge=async()=>globalThis.__vpetBridge;
export const OsEventTypeList={CLICK_EVENT:0,SCROLL_TOP_EVENT:1,SCROLL_BOTTOM_EVENT:2,DOUBLE_CLICK_EVENT:3,FOREGROUND_ENTER_EVENT:4,FOREGROUND_EXIT_EVENT:5,ABNORMAL_EXIT_EVENT:6,SYSTEM_EXIT_EVENT:7};
export const StartUpPageCreateResult={success:0},ImageRawDataUpdateResult={success:'success'};
export class ImageContainerProperty{constructor(p){Object.assign(this,p)}}
export class TextContainerProperty{constructor(p){Object.assign(this,p)}}
export class CreateStartUpPageContainer{constructor(p){Object.assign(this,p)}}
export class RebuildPageContainer{constructor(p){Object.assign(this,p)}}
export class ImageRawDataUpdate{constructor(p){Object.assign(this,p)}}`;
const url = (s) =>
  "data:text/javascript;base64," + Buffer.from(s).toString("base64");
const bmp = ts.transpileModule(
  readFileSync(new URL("../src/BmpEncoder.ts", import.meta.url), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.ESNext } },
).outputText;
let source = ts.transpileModule(
  readFileSync(new URL("../src/display.ts", import.meta.url), "utf8"),
  {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  },
).outputText;
source = source
  .replace("@evenrealities/even_hub_sdk", url(stub))
  .replace("./BmpEncoder", url(bmp));
const { Display } = await import(url(source));
test("display serializes images, rebuilds once and isolates native menu input", async () => {
  let handler,
    created = 0,
    rebuilds = 0,
    inflight = 0,
    peak = 0,
    sends = 0,
    exit = 0,
    unsubscribed = 0;
  globalThis.__vpetBridge = {
    onEvenHubEvent(fn) {
      handler = fn;
      return () => {
        unsubscribed++;
      };
    },
    async createStartUpPageContainer() {
      created++;
      return 0;
    },
    async rebuildPageContainer() {
      rebuilds++;
      return true;
    },
    async updateImageRawData() {
      inflight++;
      peak = Math.max(peak, inflight);
      sends++;
      await new Promise((r) => setTimeout(r, 5));
      inflight--;
      return "success";
    },
  };
  const d = new Display(),
    inputs = [];
  await d.connect(
    (x) => inputs.push(x),
    () => exit++,
  );
  await Promise.all([
    d.draw(new Uint8Array(20000)),
    d.draw(new Uint8Array(20000)),
  ]);
  assert.equal(peak, 1);
  assert.equal(sends, 2);
  handler({ sysEvent: { eventType: 4 } });
  assert.equal(d.active, false);
  handler({ sysEvent: { eventType: 0 } });
  assert.deepEqual(inputs, []);
  handler({ sysEvent: { eventType: 5 } });
  handler({ sysEvent: { eventType: 1 } });
  assert.deepEqual(inputs, ["up"]);
  await d.connect(
    (x) => inputs.push(x),
    () => exit++,
  );
  assert.equal(created, 1);
  assert.equal(rebuilds, 1);
  handler({ sysEvent: { eventType: 7 } });
  assert.equal(d.active, false);
  assert.equal(exit, 1);
  assert.equal(unsubscribed, 2);
  await d.draw(new Uint8Array(20000));
  assert.equal(sends, 2);
});
