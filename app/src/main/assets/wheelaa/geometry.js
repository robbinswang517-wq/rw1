/** Map the contained video image, excluding any letterboxing, to normalized raw-frame coordinates. */
export function framePoint(rect, width, height, clientX, clientY, clamp = false) {
  if (!(rect.width > 0 && rect.height > 0 && width > 0 && height > 0)) return null;
  const scale = Math.min(rect.width / width, rect.height / height);
  const w = width * scale, h = height * scale;
  let x = (clientX - rect.left - (rect.width - w) / 2) / w;
  let y = (clientY - rect.top - (rect.height - h) / 2) / h;
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  if (!clamp && (x < 0 || x > 1 || y < 0 || y > 1)) return null;
  return { x: Math.max(0, Math.min(1, x)), y: Math.max(0, Math.min(1, y)) };
}
