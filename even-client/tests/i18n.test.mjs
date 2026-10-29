import { test } from "node:test";
import assert from "node:assert/strict";
import ts from "typescript";
import { readFileSync } from "node:fs";
const source = ts.transpileModule(
  readFileSync(new URL("../src/i18n.ts", import.meta.url), "utf8"),
  { compilerOptions: { module: ts.ModuleKind.ESNext } },
).outputText;
const { resolveLanguage, t, localizePage, language } = await import(
  "data:text/javascript;base64," + Buffer.from(source).toString("base64")
);

test("Japanese language variants and English fallback", () => {
  for (const locale of ["ja", "ja-JP", "ja-jp", "JA-JP"])
    assert.equal(resolveLanguage([locale]), "ja");
  for (const locales of [[], ["en-US"], ["es-ES", "ja-JP"], ["javanese"]])
    assert.equal(resolveLanguage(locales), "en");
  assert.equal(t("Connect phone", "ja"), "スマートフォンに接続");
  assert.equal(t("Card input", "ja"), "カード入力");
  assert.equal(t("RUNNING", "ja"), "動作中");
  assert.equal(t("Connect phone", "en"), "Connect phone");
  assert.equal(t("unknown diagnostic", "ja"), "unknown diagnostic");
});

test("page localization preserves form controls and translates accessibility text", () => {
  const original = globalThis.document;
  const label = { textContent: "\n  Pairing   key  " };
  const input = {
    value: "a".repeat(32),
    dataset: { i18nAttribute: "placeholder" },
    placeholder: "Paste key from V-pet Glyph",
    getAttribute(name) { return this[name]; },
    setAttribute(name, value) { this[name] = value; },
  };
  globalThis.document = {
    documentElement: { lang: "" },
    title: "V-pet · G2 link",
    querySelectorAll(selector) {
      return selector === "[data-i18n]" ? [label] : [input];
    },
  };
  try {
    localizePage();
    assert.equal(document.documentElement.lang, language);
    assert.equal(label.textContent, t("Pairing key"));
    assert.equal(input.placeholder, t("Paste key from V-pet Glyph"));
    assert.equal(input.value, "a".repeat(32));
  } finally {
    globalThis.document = original;
  }
});

test("Japanese host translates the page and accessibility labels without changing a pairing key", async () => {
  const originalDocument = globalThis.document;
  const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  const label = { textContent: "Connect phone" };
  const input = {
    value: "b".repeat(32),
    dataset: { i18nAttribute: "aria-label" },
    "aria-label": "Live Digivice screen",
    getAttribute(name) { return this[name]; },
    setAttribute(name, value) { this[name] = value; },
  };
  Object.defineProperty(globalThis, "navigator", {
    configurable: true,
    value: { languages: ["ja-JP"] },
  });
  globalThis.document = {
    documentElement: { lang: "en" },
    title: "V-pet · G2 link",
    querySelectorAll(selector) {
      return selector === "[data-i18n]" ? [label] : [input];
    },
  };
  try {
    const japaneseHost = await import(
      "data:text/javascript;base64," + Buffer.from(source + "\n// Japanese host").toString("base64")
    );
    japaneseHost.localizePage();
    assert.equal(document.documentElement.lang, "ja");
    assert.equal(document.title, "Vペット · G2接続");
    assert.equal(label.textContent, "スマートフォンに接続");
    assert.equal(input["aria-label"], "デジヴァイスのライブ画面");
    assert.equal(input.value, "b".repeat(32));
  } finally {
    globalThis.document = originalDocument;
    if (originalNavigator) Object.defineProperty(globalThis, "navigator", originalNavigator);
    else delete globalThis.navigator;
  }
});
