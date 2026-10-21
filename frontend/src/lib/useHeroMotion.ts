import { useEffect, useRef, type RefObject } from 'react';

/** True when the visitor asked the OS for less motion: every animation below is then skipped. */
export function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false;
}

/**
 * Pointer-driven polish for the sign-in hero. It only writes CSS custom properties; all the
 * visuals live in the stylesheet:
 *  - `--px` / `--py` (-1..1) on the hero: the floating orbs drift against the cursor (parallax);
 *  - `--mx` / `--my` (px) on the card under the cursor: a soft light follows the pointer.
 */
export function useHeroMotion(): RefObject<HTMLElement> {
  const ref = useRef<HTMLElement>(null);

  useEffect(() => {
    const hero = ref.current;
    if (hero === null || prefersReducedMotion()) {
      return undefined;
    }
    let frame = 0;

    function onMove(event: PointerEvent): void {
      if (hero === null) {
        return;
      }
      const { clientX, clientY } = event;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const box = hero.getBoundingClientRect();
        const px = ((clientX - box.left) / box.width - 0.5) * 2;
        const py = ((clientY - box.top) / box.height - 0.5) * 2;
        hero.style.setProperty('--px', px.toFixed(3));
        hero.style.setProperty('--py', py.toFixed(3));

        const target = event.target instanceof Element ? event.target.closest('li') : null;
        if (target !== null && hero.contains(target)) {
          const rect = target.getBoundingClientRect();
          target.style.setProperty('--mx', `${String(clientX - rect.left)}px`);
          target.style.setProperty('--my', `${String(clientY - rect.top)}px`);
        }
      });
    }

    function onLeave(): void {
      if (hero === null) {
        return;
      }
      hero.style.setProperty('--px', '0');
      hero.style.setProperty('--py', '0');
    }

    hero.addEventListener('pointermove', onMove);
    hero.addEventListener('pointerleave', onLeave);
    return () => {
      cancelAnimationFrame(frame);
      hero.removeEventListener('pointermove', onMove);
      hero.removeEventListener('pointerleave', onLeave);
    };
  }, []);

  return ref;
}
