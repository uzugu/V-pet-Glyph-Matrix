import { test } from "node:test";
import assert from "node:assert/strict";
import ts from "typescript";
import { readFileSync } from "node:fs";
const url = (s) =>
  "data:text/javascript;base64," + Buffer.from(s).toString("base64");
const compile = (path) =>
  ts.transpileModule(readFileSync(new URL(path, import.meta.url), "utf8"), {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  }).outputText;
const waitFor = async (predicate) => {
  const end = Date.now() + 2500;
  while (!predicate()) {
    assert.ok(Date.now() < end, "Timed out waiting for client state");
    await new Promise((r) => setTimeout(r, 10));
  }
};
test("reconnect redraws an unchanged LCD, resets input session, and drops pre-loss queued inputs", async () => {
  const originals = {
    document: globalThis.document,
    fetch: globalThis.fetch,
    addEventListener: globalThis.addEventListener,
    window: globalThis.window,
  };
  const elements = new Map(),
    images = [];
  const make = () => ({
    textContent: "",
    value: "",
    disabled: false,
    children: [],
    onclick: null,
    replaceChildren() {
      this.children = [];
    },
    append(child) {
      this.children.push(child);
    },
  });
  for (const id of [
    "screen",
    "error",
    "status",
    "keys",
    "gestures",
    "connect",
    "disconnect",
    "glasses",
    "token",
  ])
    elements.set(id, make());
  elements.get("screen").getContext = () => ({
    createImageData: () => ({ data: new Uint8ClampedArray(80000) }),
    putImageData: (image) => images.push(image.data.slice()),
  });
  globalThis.document = {
    getElementById: (id) => elements.get(id),
    createElement: make,
  };
  globalThis.addEventListener = () => {};
  globalThis.window = globalThis;
  const value = {
    version: 1,
    width: 32,
    height: 24,
    rows: Array(24)
      .fill("p" + ".".repeat(31))
      .join("\n"),
    romHash: "a".repeat(64),
    dpower: false,
    ready: true,
    state: "RUNNING",
  };
  let frameRequests = 0,
    releaseInput,
    secondInputStarted = false;
  const commands = [];
  globalThis.fetch = async (address, options) => {
    if (address.endsWith("/press")) {
      commands.push(JSON.parse(options.body));
      if (commands.length === 2) {
        secondInputStarted = true;
        return new Promise((resolve) => {
          releaseInput = () => resolve({ ok: false, status: 503 });
        });
      }
      return { ok: true };
    }
    frameRequests++;
    if (frameRequests === 2) throw new Error("temporary connection loss");
    return { ok: true, json: async () => value };
  };
  const stub =
    "export class Display {active=false;async setDPower(){return false}async connect(){return false}async draw(){}}";
  let source = compile("../src/main.ts")
    .replace('import "./style.css";', "")
    .replace("./display", url(stub))
    .replace("./protocol", url(compile("../src/protocol.ts")));
  try {
    await import(url(source));
    elements.get("token").value = "b".repeat(32);
    elements.get("connect").onclick();
    await waitFor(() => elements.get("status").textContent === "RUNNING");
    elements.get("keys").children[0].onclick();
    await waitFor(() => commands.length === 1);
    elements.get("keys").children[1].onclick();
    await waitFor(() => secondInputStarted);
    // This third command is behind the in-flight request and must never cross a lost connection.
    elements.get("keys").children[2].onclick();
    await waitFor(() => elements.get("status").textContent === "RECONNECTING");
    assert.ok(elements.get("keys").children.every((b) => b.disabled));
    await waitFor(
      () =>
        frameRequests >= 3 && elements.get("status").textContent === "RUNNING",
    );
    assert.ok(
      elements.get("keys").children.every((b) => !b.disabled),
      "same LCD must restore controls",
    );
    assert.ok(
      images.at(-1).some((n, i) => i % 4 === 0 && n === 24),
      "same LCD must be repainted",
    );
    releaseInput();
    await new Promise((r) => setTimeout(r, 20));
    assert.equal(
      elements.get("status").textContent,
      "RUNNING",
      "late input failure must not disconnect recovered client",
    );
    assert.equal(commands.length, 2, "queued old command must not replay");
    elements.get("keys").children[0].onclick();
    await waitFor(() => commands.length === 3);
    assert.equal(commands[2].seq, 1);
    assert.notEqual(commands[2].session, commands[0].session);
  } finally {
    elements.get("disconnect").onclick?.();
    // Let the current poll notice its retired generation before replacing browser globals.
    await new Promise((r) => setTimeout(r, 250));
    Object.assign(globalThis, originals);
  }
});
