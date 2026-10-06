import { useEffect, useState } from 'react';
import { prefersReducedMotion } from '../lib/useHeroMotion';

interface Props {
  words: readonly string[];
  intervalMs?: number;
}

/**
 * Cycles through short words with a CSS slide/fade. Screen readers get the first word once
 * (the cycling copy is aria-hidden), and nothing moves for visitors who asked for less motion.
 */
export function RotatingWord({ words, intervalMs = 2400 }: Props): JSX.Element {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (words.length < 2 || prefersReducedMotion()) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      setIndex((current) => (current + 1) % words.length);
    }, intervalMs);
    return () => {
      window.clearInterval(timer);
    };
  }, [words, intervalMs]);

  return (
    <span className="rotating-word">
      <span className="sr-only">{words[0]}</span>
      <span key={index} className="rotating-word-item" aria-hidden="true">
        {words[index]}
      </span>
    </span>
  );
}
