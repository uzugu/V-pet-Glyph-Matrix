export interface Frame {
  version: 1;
  width: 32;
  height: 24;
  rows: string;
  romHash: string;
  inputEpoch: string;
  dpower: boolean;
  ready: boolean;
  state: string;
}
export function frame(value: unknown): Frame {
  const f = value as Frame;
  if (
    !f ||
    f.version !== 1 ||
    f.width !== 32 ||
    f.height !== 24 ||
    typeof f.rows !== "string" ||
    !/^[a-f0-9]{64}$/.test(f.romHash) ||
    typeof f.inputEpoch !== "string" ||
    !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}:[0-9]{1,19}$/.test(f.inputEpoch) ||
    typeof f.ready !== "boolean" ||
    typeof f.dpower !== "boolean" ||
    typeof f.state !== "string" ||
    f.rows.split("\n").length !== 24 ||
    !f.rows.split("\n").every((r) => /^[pid.]{32}$/.test(r))
  )
    throw new Error("Host has no compatible frame yet");
  return f;
}
export function imagePixels(f: Frame): Uint8Array {
  // A 6x scale preserves each original pixel; 24 rows include classic care icons.
  const result = new Uint8Array(200 * 100);
  const rows = f.rows.split("\n");
  // Full LCD 32x16 at 6x:192x96. Classic icons use a separate 4x fit, 128x96.
  const scale = f.dpower ? 6 : 4,
    start = f.dpower ? 4 : 0,
    end = f.dpower ? 20 : 24;
  const ox = (200 - 32 * scale) / 2,
    oy = (100 - (end - start) * scale) / 2;
  for (let y = start; y < end; y++)
    for (let x = 0; x < 32; x++)
      if (rows[y][x] === "p" || rows[y][x] === "i")
        for (let dy = 0; dy < scale; dy++)
          for (let dx = 0; dx < scale; dx++)
            result[
              (oy + (y - start) * scale + dy) * 200 + ox + x * scale + dx
            ] = 1;
  return result;
}
export function gesture(
  kind: "press" | "up" | "down" | "double",
  dpower: boolean,
): string {
  return dpower
    ? { press: "up", up: "left", down: "right", double: "down" }[kind]
    : { press: "a", up: "b", down: "c", double: "b" }[kind];
}
