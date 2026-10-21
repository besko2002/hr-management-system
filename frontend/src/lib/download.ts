/**
 * Saves a Blob the browser already holds. Downloads cannot go through a plain link
 * because the API needs the bearer header, so the bytes are fetched first and handed
 * here. jsdom has no `createObjectURL`, hence the guard.
 */
export function saveBlob(blob: Blob, filename: string): void {
  const factory = URL.createObjectURL as ((blob: Blob) => string) | undefined;
  if (typeof factory !== 'function') {
    return;
  }
  const href = factory(blob);
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  if (typeof URL.revokeObjectURL === 'function') {
    URL.revokeObjectURL(href);
  }
}
