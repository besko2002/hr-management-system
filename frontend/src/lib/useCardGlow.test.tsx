import { act, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useCardGlow } from './useCardGlow';

function Probe(): JSX.Element {
  useCardGlow();
  return (
    <div>
      <div className="card" data-testid="card">
        <span data-testid="inner">content</span>
      </div>
      <p data-testid="outside">no card here</p>
    </div>
  );
}

function stubMotion(reduce: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query: string) => ({
      matches: reduce && query.includes('prefers-reduced-motion'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
    callback(0);
    return 1;
  });
}

function move(target: Element, x: number, y: number): void {
  act(() => {
    target.dispatchEvent(new MouseEvent('pointermove', { bubbles: true, clientX: x, clientY: y }));
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useCardGlow', () => {
  it('writes the pointer position inside the card under the cursor', () => {
    stubMotion(false);
    const { getByTestId } = render(<Probe />);
    const card = getByTestId('card');
    vi.spyOn(card, 'getBoundingClientRect').mockReturnValue({ left: 100, top: 40 } as DOMRect);

    move(getByTestId('inner'), 160, 95);

    expect(card.style.getPropertyValue('--mx')).toBe('60px');
    expect(card.style.getPropertyValue('--my')).toBe('55px');
  });

  it('ignores the pointer when it is not over a card', () => {
    stubMotion(false);
    const { getByTestId } = render(<Probe />);
    move(getByTestId('outside'), 10, 10);
    expect(getByTestId('card').style.getPropertyValue('--mx')).toBe('');
  });

  it('does nothing for visitors who asked for reduced motion', () => {
    stubMotion(true);
    const { getByTestId } = render(<Probe />);
    move(getByTestId('inner'), 160, 95);
    expect(getByTestId('card').style.getPropertyValue('--mx')).toBe('');
  });

  it('removes its listener on unmount', () => {
    stubMotion(false);
    const remove = vi.spyOn(document, 'removeEventListener');
    const { unmount } = render(<Probe />);
    unmount();
    expect(remove).toHaveBeenCalledWith('pointermove', expect.any(Function));
  });
});
