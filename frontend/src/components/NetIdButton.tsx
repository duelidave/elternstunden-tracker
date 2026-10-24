import '@fontsource/ibm-plex-sans/latin-600.css'
import { texts } from '../i18n/de'

/**
 * Login with netID (D-30) as a plain link to the backend entry point, like Google (DP-04, SEC-10). Look after the
 * netID styleguide: brand green, white logo. The visible text is the brand name "netID" (a styleguide option): white
 * on this green reaches only about 2.4:1, which WCAG allows for brand names only (UI-06, UI-09).
 */
export function NetIdButton({ label }: { label: string }) {
  return (
    <div>
      <a className="netid-button" href="/oauth2/authorization/netid" aria-label={label}>
        <NetIdLogo />
        <span aria-hidden="true">{texts.netid.brand}</span>
      </a>
      <p className="mt-2 mb-0 text-sm text-muted">{texts.netid.hint}</p>
    </div>
  )
}

/** The logo of the netID styleguide (white, 40 × 40). */
export function NetIdLogo() {
  return (
    <svg viewBox="0 0 40 40" aria-hidden="true" className="netid-button__logo">
      <path
        fill="currentColor"
        d="M30.3 4.1a14.5 14.5 0 00-6-2.7C20.7 1 17 2 13.6 5.3L7 12.1l-6.9 7 19.8 19.8 8.8-8.9 5-4.9c3-3.1 4.2-6.7 3.8-10a12.9 12.9 0 00-1-3.8l-2 2a10 10 0 01.6 2c.3 2.7-.5 5.4-3.2 8l-4.9 5-16.3-16.4L15.5 7c3-3 5.7-3.6 8.3-3.1 1.6.2 3.2 1 4.7 2z"
      />
      <path fill="currentColor" d="M37.7 1.1l-12.9 13-4.6-4.7-2.3 2.3 6.9 7L40 3.5z" />
    </svg>
  )
}
