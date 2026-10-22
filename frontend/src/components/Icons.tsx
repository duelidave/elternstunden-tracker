// Small decorative drawings. Hidden from assistive technology, colours come from the tokens (UI-01).

/**
 * The mark of the Hort logo (OPEN-19: redrawn from the logo on the website, 2026-10-22): an orange frame with the
 * colourful shapes of "Lernen mit allen Sinnen". Also used as favicon (public/favicon.svg).
 */
export function HortMark({ className = 'h-12 w-auto flex-none' }: { className?: string }) {
  return (
    <svg viewBox="0 0 66 81" aria-hidden="true" className={className}>
      <circle cx="21" cy="6" r="5.5" className="fill-brand-yellow" />
      <circle cx="55.5" cy="6" r="5.5" className="fill-brand-lime" />
      <rect x="1" y="14" width="65" height="7" rx="1.5" className="fill-accent" />
      <rect x="17" y="21" width="7" height="58" rx="1" className="fill-accent" />
      <rect x="52" y="21" width="7" height="58" rx="1" className="fill-accent" />
      <rect x="17" y="47.5" width="42" height="7" className="fill-accent" />
      <circle cx="38" cy="34.25" r="11.6" className="fill-brand-teal" />
      <rect x="27" y="57" width="23" height="9.5" rx="1" className="fill-brand-green" />
      <rect x="27" y="69" width="23" height="10.5" rx="1" className="fill-brand-purple" />
    </svg>
  )
}

/** Envelope with a small dot, above the "check your inbox" confirmations. */
export function MailIcon() {
  return (
    <svg viewBox="0 0 56 56" aria-hidden="true" className="card__icon">
      <circle cx="28" cy="28" r="28" className="fill-brand-teal-surface" />
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
