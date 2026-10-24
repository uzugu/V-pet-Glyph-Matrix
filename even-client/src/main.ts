import "./style.css";
import { Display } from "./display";
import { frame, imagePixels, gesture, type Frame } from "./protocol";
const $ = <T extends HTMLElement>(id: string) =>
  document.getElementById(id) as T;
const display = new Display(),
  canvas = $<HTMLCanvasElement>("screen"),
  ctx = canvas.getContext("2d")!;
let running = false,
  generation = 0,
  current: Frame | null = null,
  session = crypto.randomUUID(),
  seq = 0,
  key = "",
  inputChain = Promise.resolve(),
  last = "",
  glassesLast = "";
const error = (message: string) => {
  $("error").textContent = message;
};
const status = (message: string) => {
  $("status").textContent = message;
};
function paint(pixels: Uint8Array) {
  const image = ctx.createImageData(200, 100);
  for (let i = 0; i < pixels.length; i++) {
    const c = pixels[i] ? [24, 50, 39] : [167, 188, 142];
    image.data.set([...c, 255], i * 4);
  }
  ctx.putImageData(image, 0, 0);
}
paint(new Uint8Array(20000));
async function request(path: string, init: RequestInit = {}) {
  return fetch("http://127.0.0.1:8766/v1/" + path, {
    ...init,
    credentials: "omit",
    referrerPolicy: "no-referrer",
    cache: "no-store",
    signal: AbortSignal.timeout(3000),
    headers: {
      Authorization: "Bearer " + key,
      "Content-Type": "application/json",
    },
  });
}
function keys() {
  const panel = $("keys");
  panel.replaceChildren();
  for (const [label, value] of current?.dpower
    ? [
        ["◀", "left"],
        ["▲", "up"],
        ["▶", "right"],
        ["▼", "down"],
        ["Step", "step"],
        ["Card", "card"],
      ]
    : [
        ["A", "a"],
        ["B", "b"],
        ["C", "c"],
      ]) {
    const button = document.createElement("button");
    button.textContent = label;
    button.disabled = !current?.ready;
    button.onclick = () => press(value);
    panel.append(button);
  }
  $("gestures").textContent = current?.dpower
    ? "Press → Up · swipe → Left / Right · double press → Down"
    : "Press → A · swipe up → B · swipe down → C · double press → B";
}
function press(button: string) {
  const captured = current,
    epoch = generation;
  if (!running || !captured?.ready) return;
  inputChain = inputChain
    .then(async () => {
      if (epoch !== generation || current?.romHash !== captured.romHash) return;
      const res = await request("press", {
        method: "POST",
        body: JSON.stringify({
          session,
          seq: ++seq,
          button,
          romHash: captured.romHash,
        }),
      });
      if (!res.ok) throw new Error("Input rejected; reconnect before playing");
    })
    .catch((e) => {
      if (epoch === generation) {
        error(String(e));
        disconnect();
      }
    });
}
function disconnect() {
  running = false;
  ++generation;
  current = null;
  last = "";
  glassesLast = "";
  keys();
  status("DISCONNECTED");
  paint(new Uint8Array(20000));
  void display.draw(new Uint8Array(20000)).catch(() => {});
}
async function poll(epoch: number) {
  while (running && generation === epoch) {
    try {
      const response = await request("frame");
      if (!response.ok) throw new Error("Phone returned " + response.status);
      const value = frame(await response.json());
      if (generation !== epoch) return;
      const changed = JSON.stringify(value);
      current = value;
      if (last !== changed) {
        last = changed;
        keys();
        paint(imagePixels(value));
      }
      status(value.state);
      error("");
      if (display.active && glassesLast !== changed) {
        await display.draw(
          value.ready ? imagePixels(value) : new Uint8Array(20000),
        );
        if (epoch !== generation) return;
        glassesLast = changed;
      }
    } catch (e) {
      if (generation !== epoch) return;
      current = null;
      keys();
      status("RECONNECTING");
      error(String(e));
      paint(new Uint8Array(20000));
      if (glassesLast !== "blank") {
        try {
          await display.draw(new Uint8Array(20000));
          glassesLast = "blank";
        } catch {}
      }
    }
    await new Promise((r) => setTimeout(r, 200));
  }
}
$("connect").onclick = () => {
  disconnect();
  key = $<HTMLInputElement>("token").value.trim();
  if (!/^[a-f0-9]{32}$/.test(key)) {
    error("Paste the 32-character key from the phone app");
    return;
  }
  running = true;
  session = crypto.randomUUID();
  seq = 0;
  status("CONNECTING");
  void poll(++generation);
};
$("disconnect").onclick = disconnect;
$("glasses").onclick = async () => {
  try {
    await display.connect((kind) => {
      if (current) press(gesture(kind, current.dpower));
    }, disconnect);
    glassesLast = "";
    error(display.active ? "" : "Even bridge unavailable");
  } catch (e) {
    error(String(e));
  }
};
// Background/lock is supported by the Even host; do not end a session on visibility alone.
window.addEventListener("pagehide", disconnect);
keys();
