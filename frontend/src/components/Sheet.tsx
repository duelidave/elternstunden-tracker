import { type ReactNode, useEffect, useRef } from 'react'
import { texts } from '../i18n/de'

interface SheetProps {
  title: string
  onClose: () => void
  children: ReactNode
}

/** Bottom sheet (UI-04): a modal dialog at the bottom on phones, centered on larger screens. */
export function Sheet({ title, onClose, children }: SheetProps) {
  const dialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    // showModal: focus stays in the sheet, Escape closes it (not available in every test DOM). Unmounting removes
    // the dialog from the page without a close event.
    const current = dialog.current
    if (current?.open === false) {
      current.showModal?.()
      if (!current.open) {
        current.setAttribute('open', '')
      }
    }
  }, [])

  return (
    <dialog
      ref={dialog}
      className="sheet"
      aria-labelledby="sheet-title"
      onClose={onClose}
      onClick={(event) => {
        // A click on the backdrop (the dialog element itself) closes it
        if (event.target === dialog.current) {
          onClose()
        }
      }}
    >
      <div className="sheet__header">
        <h2 id="sheet-title">{title}</h2>
        <button type="button" className="sheet__close" aria-label={texts.entry.close} onClick={onClose}>
          <svg viewBox="0 0 24 24" aria-hidden="true" className="size-6">
            <path d="M6 6l12 12M18 6L6 18" fill="none" strokeWidth="2.5" strokeLinecap="round" className="stroke-text" />
          </svg>
        </button>
      </div>
      {children}
    </dialog>
  )
}
