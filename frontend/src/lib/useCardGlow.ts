import { useEffect } from 'react';
import { prefersReducedMotion } from './useHeroMotion';

/**
 * One delegated pointer listener for the whole app: the card under the cursor gets `--mx` / `--my`
 * (the pointer position inside it), which the stylesheet turns into a soft light that follows the mouse.
 * Nothing is attached for visitors who asked for reduced motion.
 */
export function useCardGlow(): void {
  useEffect(() => {
    if (prefersReducedMotion()) {
      return undefined;
    }
    let frame = 0;

    function onMove(event: PointerEvent): void {
      const { clientX, clientY } = event;
      const origin = event.target instanceof Element ? event.target : null;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const card = origin?.closest<HTMLElement>('.card, .balance-card');
        if (card === null || card === undefined) {
          return;
        }
        const box = card.getBoundingClientRect();
        card.style.setProperty('--mx', `${String(clientX - box.left)}px`);
        card.style.setProperty('--my', `${String(clientY - box.top)}px`);
      });
    }

    document.addEventListener('pointermove', onMove, { passive: true });
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener('pointermove', onMove);
    };
  }, []);
}
