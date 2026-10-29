/** Interface language only; firmware pixels and wire values stay unchanged. */
export function resolveLanguage(languages: readonly string[]): "en" | "ja" {
  // Follow the host's primary language, matching Android's English fallback.
  return /^ja(?:-|$)/i.test(languages[0] ?? "") ? "ja" : "en";
}
export const language = resolveLanguage(
  globalThis.navigator?.languages?.length
    ? globalThis.navigator.languages
    : [globalThis.navigator?.language ?? "en"],
);
const japanese: Record<string, string> = {
  "V-pet · G2 link": "Vペット · G2接続",
  "DIGITAL MONSTER / REMOTE TERMINAL": "デジタルモンスター / リモート端末",
  "Your pet.": "あなたのデジモンを。",
  "Within sight.": "いつも目の前に。",
  "Your phone runs the Digivice. Your glasses carry its screen.": "スマートフォンで動くデジヴァイスの画面を、グラスに表示します。",
  "LIVE LCD": "LCDライブ表示",
  "Live Digivice screen": "デジヴァイスのライブ画面",
  "01 / PHONE LINK": "01 / スマートフォン接続",
  "Pairing key": "ペアリングキー",
  "Paste key from V-pet Glyph": "V-pet Glyphのキーを貼り付け",
  "In the phone app, start the pet and enable Even G2 streaming. Copy its pairing key here.": "スマートフォンのアプリでペットを起動し、Even G2への配信を有効にしてください。ペアリングキーをここに貼り付けます。",
  "Connect phone": "スマートフォンに接続",
  "Connect glasses": "グラスに接続",
  "Disconnect": "切断",
  "Phone-local connection · no ROMs on glasses": "同じスマートフォン内で接続 · グラスにROMは保存しません",
  "DISCONNECTED": "未接続",
  "CONNECTING": "接続中",
  "RECONNECTING": "再接続中",
  "RUNNING": "動作中",
  "STARTING": "起動中",
  "STOPPED": "停止中",
  "FROZEN": "一時停止中",
  "ERROR": "エラー",
  "Step": "歩数",
  "Card": "カード",
  "Card input": "カード入力",
  "Press → A · swipe up → B · swipe down → C": "押す → A · 上スワイプ → B · 下スワイプ → C",
  "Press → A · swipe up → B · swipe down → C · double press → B": "押す → A · 上スワイプ → B · 下スワイプ → C · 2回押す → B",
  "Press → Up · swipe → Left / Right · double press → Down": "押す → 上 · スワイプ → 左／右 · 2回押す → 下",
  "Input rejected; reconnect before playing": "入力を受け付けられませんでした。再接続してから操作してください",
  "Phone returned ": "スマートフォンの応答：",
  "Paste the 32-character key from the phone app": "スマートフォンのアプリから32文字のキーを貼り付けてください",
  "Even bridge unavailable": "Evenブリッジを利用できません",
  "Even bridge not detected — browser preview available": "Evenブリッジが見つかりません。ブラウザーのプレビューは使用できます",
  "G2 page rebuild failed": "G2画面を更新できませんでした",
  "G2 page creation failed": "G2画面を作成できませんでした",
  "G2 input menu rebuild failed": "G2操作メニューを更新できませんでした",
  "Glasses image update failed: ": "グラスの画像を更新できませんでした：",
};
export function t(text: string, locale: "en" | "ja" = language): string {
  return locale === "ja" ? japanese[text] ?? text : text;
}
export function localizePage(): void {
  document.documentElement.lang = language;
  document.title = t(document.title);
  document.querySelectorAll<HTMLElement>("[data-i18n]").forEach((element) => {
    const key = element.textContent?.trim().replace(/\s+/g, " ") ?? "";
    element.textContent = t(key);
  });
  document.querySelectorAll<HTMLElement>("[data-i18n-attribute]").forEach((element) => {
    const attribute = element.dataset.i18nAttribute!;
    element.setAttribute(attribute, t(element.getAttribute(attribute) ?? ""));
  });
}
