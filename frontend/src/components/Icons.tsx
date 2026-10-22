// Small decorative drawings. Hidden from assistive technology, colours come from the tokens (UI-01).

/** App mark next to the name in the header, the same drawing as the favicon. */
export function LogoMark() {
  return (
    <svg viewBox="0 0 32 34" aria-hidden="true" className="size-12 flex-none">
      <rect y="2" width="32" height="32" rx="8" className="fill-accent-strong" />
      <rect width="32" height="32" rx="8" className="fill-accent" />
      <path d="M10 8h12v3h-8.5v3.5H21v3h-7.5V21H22v3H10z" className="fill-text" />
    </svg>
  )
}

/** Envelope with a small dot, above the "check your inbox" confirmations. */
export function MailIcon() {
  return (
    <svg viewBox="0 0 56 56" aria-hidden="true" className="card__icon">
      <circle cx="28" cy="28" r="28" className="fill-accent-surface" />
      <rect x="14" y="18" width="28" height="21" rx="3" strokeWidth="2" className="fill-surface stroke-text" />
      <path
        d="m15.5 20 12.5 9.5L40.5 20"
        fill="none"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="stroke-text"
      />
      <circle cx="41" cy="18" r="5.5" strokeWidth="2" className="fill-accent stroke-surface" />
    </svg>
  )
}
