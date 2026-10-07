import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RotatingWord, WORD_EXIT_MS } from '../components/RotatingWord';
import { installFetchMock, renderApp } from '../test/helpers';

function mockMotion(reduce: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation((query: string) => ({
      matches: reduce && query.includes('prefers-reduced-motion'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

afterEach(() => {
  vi.useRealTimers();
});

describe('sign-in page extras', () => {
  it('shows and hides the typed password with an accessible toggle', async () => {
    installFetchMock({});
    renderApp('/login');
    const user = userEvent.setup();

    const field = await screen.findByLabelText(/^password/i);
    expect(field).toHaveAttribute('type', 'password');

    const toggle = screen.getByRole('button', { name: 'Show characters' });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await user.type(field, 'S3cret!pass');
    await user.click(toggle);

    expect(field).toHaveAttribute('type', 'text');
    expect(field).toHaveValue('S3cret!pass');
    expect(screen.getByRole('button', { name: 'Hide characters' })).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByRole('button', { name: 'Hide characters' }));
    expect(field).toHaveAttribute('type', 'password');
  });

  it('keeps "Sign in" as the page heading even with the animated hero', async () => {
    installFetchMock({});
    renderApp('/login');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });
});

describe('RotatingWord', () => {
  it('eases through the words (fade out, then fade in), wraps around, and tells screen readers only the first word', () => {
    mockMotion(false);
    vi.useFakeTimers();
    const { container } = render(<RotatingWord words={['people', 'leave', 'payroll']} intervalMs={1000} />);

    const item = (): Element | null => container.querySelector('.rotating-word-item');
    expect(item()?.textContent).toBe('people');
    expect(container.querySelector('.sr-only')).toHaveTextContent('people');
    expect(item()).toHaveAttribute('aria-hidden', 'true');

    // after the dwell time the word starts fading out, but is still the same word
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(item()?.textContent).toBe('people');
    expect(item()).toHaveClass('rotating-word-item--out');

    // once the exit animation is over the next word takes its place
    act(() => {
      vi.advanceTimersByTime(WORD_EXIT_MS);
    });
    expect(item()?.textContent).toBe('leave');
    expect(item()).not.toHaveClass('rotating-word-item--out');

    // t = 1520 now. The next swaps land at 2520 (payroll) and 3520 (back to the first word).
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(item()?.textContent).toBe('payroll');
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(item()?.textContent).toBe('people');
  });

  it('keeps each word on screen for several seconds by default', () => {
    mockMotion(false);
    vi.useFakeTimers();
    const { container } = render(<RotatingWord words={['people', 'leave']} />);
    act(() => {
      vi.advanceTimersByTime(3500);
    });
    expect(container.querySelector('.rotating-word-item')?.textContent).toBe('people');
    expect(container.querySelector('.rotating-word-item')).not.toHaveClass('rotating-word-item--out');
  });

  it('does not animate for visitors who asked for reduced motion', () => {
    mockMotion(true);
    vi.useFakeTimers();
    const { container } = render(<RotatingWord words={['people', 'leave']} intervalMs={1000} />);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(container.querySelector('.rotating-word-item')?.textContent).toBe('people');
  });

  it('stops its timer when it unmounts', () => {
    mockMotion(false);
    vi.useFakeTimers();
    const clear = vi.spyOn(window, 'clearInterval');
    const { unmount } = render(<RotatingWord words={['a', 'b']} intervalMs={500} />);
    unmount();
    expect(clear).toHaveBeenCalled();
  });

  it('shows a single word without starting a timer', () => {
    mockMotion(false);
    vi.useFakeTimers();
    const set = vi.spyOn(window, 'setInterval');
    render(<RotatingWord words={['solo']} />);
    expect(set).not.toHaveBeenCalled();
  });
});
