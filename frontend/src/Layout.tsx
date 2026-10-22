import { lazy, Suspense } from 'react'
import { Link, Outlet } from 'react-router'
import { LogoMark } from './components/Icons'
import { texts } from './i18n/de'

// FS-DEV (D-26): only in the Vite dev server. In the production build this is `false`, so the bundler drops the
// import and the toolbar chunk entirely (AC-DEV-03, checked by scripts/check-no-dev-toolbar.mjs).
const DevToolbar = import.meta.env.DEV ? lazy(() => import('./dev/DevToolbar')) : undefined

export function Layout() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-xl flex-col">
      <header className="mx-4 flex items-center gap-3 pt-5 pb-1">
        <LogoMark />
        <div className="min-w-0">
          <h1>
            <Link to="/" className="inline-flex min-h-tap items-center no-underline">
              {texts.app.title}
            </Link>
          </h1>
          <p className="-mt-1 mb-0 text-sm text-muted">{texts.app.subtitle}</p>
        </div>
      </header>
      <main className="flex-1 px-4 pt-4 pb-8">
        <Outlet />
      </main>
      <footer className="mx-4 border-t border-line py-2">
        <nav aria-label={texts.common.legalNav} className="flex justify-center gap-6 text-sm">
          <Link className="inline-flex min-h-tap items-center text-muted" to="/legal/privacy">
            {texts.common.privacy}
          </Link>
          <Link className="inline-flex min-h-tap items-center text-muted" to="/legal/imprint">
            {texts.common.imprint}
          </Link>
        </nav>
      </footer>
      {DevToolbar !== undefined && (
        <Suspense fallback={null}>
          <DevToolbar />
        </Suspense>
      )}
    </div>
  )
}
