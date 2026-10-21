import { Link, Outlet } from 'react-router'
import { texts } from './i18n/de'

export function Layout() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col">
      <header className="mx-4 border-b-4 border-accent py-4">
        <h1>
          <Link to="/" className="no-underline">
            {texts.app.title}
          </Link>
        </h1>
        <p className="m-0 text-muted">{texts.app.subtitle}</p>
      </header>
      <main className="flex-1 px-4 py-6">
        <Outlet />
      </main>
      <footer className="mx-4 border-t border-line py-2">
        <nav aria-label={texts.common.legalNav} className="flex gap-4">
          <Link className="inline-flex min-h-tap items-center" to="/legal/privacy">
            {texts.common.privacy}
          </Link>
          <Link className="inline-flex min-h-tap items-center" to="/legal/imprint">
            {texts.common.imprint}
          </Link>
        </nav>
      </footer>
    </div>
  )
}
