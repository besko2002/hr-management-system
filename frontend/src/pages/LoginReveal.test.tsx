import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installFetchMock, renderApp } from '../test/helpers';

function page(): HTMLElement {
  const element = document.querySelector<HTMLElement>('.auth-page');
  if (element === null) {
    throw new Error('login page not rendered');
  }
  return element;
}

function setup() {
  installFetchMock({});
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  renderApp('/login');
  return user;
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('sign-in page: form revealed on hover or click', () => {
  it('starts with the form hidden behind a single "Enter workspace" button', async () => {
    setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });
    expect(trigger).toHaveAttribute('aria-expanded', 'false');
    expect(trigger).toHaveAttribute('aria-controls', 'auth-login-card');
    expect(page()).not.toHaveClass('auth-page--open');
  });

  it('is one page: no separate form panel next to the hero', async () => {
    setup();
    await screen.findByRole('button', { name: /enter workspace/i });
    expect(document.querySelector('.auth-panel')).toBeNull();
    expect(document.querySelectorAll('main')).toHaveLength(1);
    expect(page()).toHaveClass('auth-page--one');
  });

  it('opens on hover and closes shortly after the pointer leaves', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });

    await user.hover(trigger);
    expect(page()).not.toHaveClass('auth-page--open'); // not at once: hover intent
    act(() => {
      vi.advanceTimersByTime(700);
    });
    expect(page()).toHaveClass('auth-page--open');
    expect(trigger).toHaveAttribute('aria-expanded', 'true');

    await user.unhover(trigger);
    expect(page()).toHaveClass('auth-page--open'); // not instantly: there is a grace period
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(page()).not.toHaveClass('auth-page--open');
  });

  it('stays open after a click, even when the pointer leaves, and focuses the email field', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });

    await user.click(trigger);
    expect(page()).toHaveClass('auth-page--open');
    expect(screen.getByLabelText(/^work email/i)).toHaveFocus();

    await user.unhover(trigger);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(page()).toHaveClass('auth-page--open');
  });

  it('closes with Escape and returns focus to the entrance button', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });
    await user.click(trigger);
    expect(page()).toHaveClass('auth-page--open');

    await user.keyboard('{Escape}');

    expect(page()).not.toHaveClass('auth-page--open');
    expect(trigger).toHaveFocus();
  });

  it('closes with the close button', async () => {
    const user = setup();
    await user.click(await screen.findByRole('button', { name: /enter workspace/i }));
    await user.click(screen.getByRole('button', { name: 'Close form' }));
    expect(page()).not.toHaveClass('auth-page--open');
  });

  it('opens for keyboard users who tab onto the entrance button', async () => {
    const user = setup();
    await screen.findByRole('button', { name: /enter workspace/i });
    await user.tab();
    // revealed, but focus stays on the button: tabbing must not jump into the form
    expect(screen.getByRole('button', { name: /enter workspace/i })).toHaveFocus();
    expect(page()).toHaveClass('auth-page--open');
    await user.keyboard('{Enter}');
    expect(screen.getByLabelText(/^work email/i)).toHaveFocus();
  });

  it('keeps the animated "Sign in" title accessible as plain text', async () => {
    setup();
    const heading = await screen.findByRole('heading', { name: 'Sign in' });
    const letters = heading.querySelectorAll('.auth-title-letter');
    expect(letters).toHaveLength(7);
    letters.forEach((letter) => {
      expect(letter).toHaveAttribute('aria-hidden', 'true');
    });
    expect(Array.from(letters, (letter) => letter.textContent).join('').replace('\u00a0', ' ')).toBe('Sign in');
  });

  it('does not let a hover-peek close the form while the visitor is typing in it', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });
    await user.hover(trigger);
    act(() => {
      vi.advanceTimersByTime(700);
    });
    await user.click(screen.getByLabelText(/^work email/i));
    await user.unhover(trigger);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(page()).toHaveClass('auth-page--open');
  });

  it('pins the form when the visitor presses anywhere inside it after a hover-peek', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });
    await user.hover(trigger);
    act(() => {
      vi.advanceTimersByTime(700);
    });
    // the form pops up over the button, so the visitor's click lands on the card, not the button
    await user.click(screen.getByText(/accounts are created by hr/i));
    await user.unhover(trigger);
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(page()).toHaveClass('auth-page--open');
    expect(screen.getByLabelText(/^work email/i)).toHaveFocus();
  });

  it('does not open when the pointer only sweeps across the button', async () => {
    const user = setup();
    const trigger = await screen.findByRole('button', { name: /enter workspace/i });

    await user.hover(trigger);
    act(() => {
      vi.advanceTimersByTime(300); // less than the hover-intent delay
    });
    await user.unhover(trigger);
    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(page()).not.toHaveClass('auth-page--open');
  });

  it('a click opens immediately, without waiting for the hover delay', async () => {
    const user = setup();
    await user.click(await screen.findByRole('button', { name: /enter workspace/i }));
    expect(page()).toHaveClass('auth-page--open');
  });
});
