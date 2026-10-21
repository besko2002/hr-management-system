import { useEffect, useState } from 'react';
import { prefersReducedMotion } from '../lib/useHeroMotion';

interface Props {
  words: readonly string[];
  /** Time each word stays on screen, in ms. */
  intervalMs?: number;
}

/** How long the current word takes to fade out before the next one fades in (matches the CSS). */
export const WORD_EXIT_MS = 520;

/**
 * Cycles through short words, easing each one out and the next one in. Screen readers get the first
 * word once (the cycling copy is aria-hidden), and nothing moves for visitors who asked for less motion.
 */
export function RotatingWord({ words, intervalMs = 4200 }: Props): JSX.Element {
  const [index, setIndex] = useState(0);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    if (words.length < 2 || prefersReducedMotion()) {
      return undefined;
    }
    let swap: number | undefined;
    const timer = window.setInterval(() => {
      setLeaving(true);
      swap = window.setTimeout(() => {
        setIndex((current) => (current + 1) % words.length);
        setLeaving(false);
      }, WORD_EXIT_MS);
    }, intervalMs);
    return () => {
      window.clearInterval(timer);
      window.clearTimeout(swap);
    };
  }, [words, intervalMs]);

  return (
    <span className="rotating-word">
      <span className="sr-only">{words[0]}</span>
      <span
        key={index}
        className={leaving ? 'rotating-word-item rotating-word-item--out' : 'rotating-word-item'}
        aria-hidden="true"
      >
        {words[index]}
      </span>
    </span>
  );
}
