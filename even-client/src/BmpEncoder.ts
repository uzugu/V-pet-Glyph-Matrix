// Adapted from DigiviceEven/src/render/BmpEncoder.ts (local reference project).
/**
 * Encode a firmware-compatible, uncompressed 1-bit BMP.
 *
 * Even G2 image containers reliably accept this format on real hardware.
 * Pixels are row-major, top-to-bottom: 0 is black and any non-zero value is
 * white. BMP rows are written bottom-up and padded to four-byte boundaries.
 */
export function encode1BitBmp(
  width: number,
  height: number,
  pixels: Uint8Array,
): Uint8Array {
  const rowBytes = Math.ceil(width / 8);
  const stride = (rowBytes + 3) & ~3;
  const pixelDataSize = stride * height;
  const headerSize = 62;
  const fileSize = headerSize + pixelDataSize;
  const bytes = new Uint8Array(fileSize);
  const view = new DataView(bytes.buffer);

  bytes[0] = 0x42;
  bytes[1] = 0x4d;
  view.setUint32(2, fileSize, true);
  view.setUint32(10, headerSize, true);

  view.setUint32(14, 40, true);
  view.setInt32(18, width, true);
  view.setInt32(22, height, true);
  view.setUint16(26, 1, true);
  view.setUint16(28, 1, true);
  view.setUint32(30, 0, true);
  view.setUint32(34, pixelDataSize, true);
  view.setUint32(38, 2835, true);
  view.setUint32(42, 2835, true);
  view.setUint32(46, 2, true);
  view.setUint32(50, 2, true);

  bytes[58] = 255;
  bytes[59] = 255;
  bytes[60] = 255;

  for (let row = 0; row < height; row += 1) {
    const sourceY = height - 1 - row;
    const destinationOffset = headerSize + row * stride;
    for (let x = 0; x < width; x += 1) {
      if (pixels[sourceY * width + x] === 0) continue;
      bytes[destinationOffset + Math.floor(x / 8)] |= 1 << (7 - (x % 8));
    }
  }

  return bytes;
}
