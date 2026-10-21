import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach } from 'vitest';
import { setUnauthorizedHandler } from '../api/api';

/**
 * Node 26 ships an experimental `localStorage` global that is `undefined` unless the
 * process is started with --localstorage-file, and it shadows the jsdom one. Install a
 * real in-memory Storage so the token module behaves exactly as it does in a browser.
 */
function installLocalStorage(): void {
  const existing: Storage | undefined = window.localStorage;
  if (existing !== undefined && typeof existing.getItem === 'function') {
    return;
  }
  const store = new Map<string, string>();
  const storage: Storage = {
    get length(): number {
      return store.size;
    },
    clear: () => {
      store.clear();
    },
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => [...store.keys()][index] ?? null,
    removeItem: (key: string) => {
      store.delete(key);
    },
    setItem: (key: string, value: string) => {
      store.set(key, String(value));
    },
  };
  Object.defineProperty(window, 'localStorage', { configurable: true, value: storage });
}

installLocalStorage();

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
  setUnauthorizedHandler(null);
  window.localStorage.clear();
});
