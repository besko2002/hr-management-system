import { useCallback, useEffect, useRef, type ReactNode } from 'react';

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

interface ModalProps {
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Narrower dialog for confirmations. */
  size?: 'small' | 'large';
}

/**
 * Accessible dialog: labelled by its heading, focus moved inside on open, Tab cycles
 * within it, Escape closes, and focus returns to whatever opened it.
 */
export function Modal({ title, onClose, children, footer, size = 'small' }: ModalProps): JSX.Element {
  const panel = useRef<HTMLDivElement | null>(null);
  const opener = useRef<Element | null>(null);

  const focusables = useCallback((): HTMLElement[] => {
    const node = panel.current;
    if (node === null) {
      return [];
    }
    return [...node.querySelectorAll<HTMLElement>(FOCUSABLE)].filter(
      (element) => element.offsetParent !== null || element === document.activeElement,
    );
  }, []);

  useEffect(() => {
    opener.current = document.activeElement;
    const first = focusables()[0] ?? panel.current;
    first?.focus();
    return () => {
      if (opener.current instanceof HTMLElement) {
        opener.current.focus();
      }
    };
  }, [focusables]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const items = focusables();
      if (items.length === 0) {
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !panel.current?.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
    };
  }, [focusables, onClose]);

  return (
    <div className="modal-backdrop">
      <div
        className={`modal modal-${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        ref={panel}
        tabIndex={-1}
      >
        <div className="modal-head">
          <h2 id="modal-title">{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close dialog">
            ×
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer !== undefined && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}
