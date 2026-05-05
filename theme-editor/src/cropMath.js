export function normalizeCropSelection(start, end, bounds) {
  const left = Math.max(0, Math.min(start.x, end.x, bounds.width));
  const right = Math.max(0, Math.min(Math.max(start.x, end.x), bounds.width));
  const top = Math.max(0, Math.min(start.y, end.y, bounds.height));
  const bottom = Math.max(0, Math.min(Math.max(start.y, end.y), bounds.height));

  return {
    x: left,
    y: top,
    width: right - left,
    height: bottom - top,
  };
}
