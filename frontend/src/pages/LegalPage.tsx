import { texts } from '../i18n/de'

/** Privacy notice and imprint (UI-13), reachable without login. */
export function LegalPage({ kind }: { kind: 'privacy' | 'imprint' }) {
  return (
    <section className="card">
      <h2>{kind === 'privacy' ? texts.legal.privacyTitle : texts.legal.imprintTitle}</h2>
      <p>{texts.legal.placeholder}</p>
    </section>
  )
}
