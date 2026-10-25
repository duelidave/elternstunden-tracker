import { lazy, Suspense } from 'react'
import { Link, Outlet, useMatch } from 'react-router'
import { useSession } from './api/session'
import { HortMark } from './components/Icons'
import { OfflineBanner } from './components/OfflineBanner'
import { texts } from './i18n/de'
import { links } from './links'

// FS-DEV (D-26): only in the Vite dev server. In the production build this is `false`, so the bundler drops the
// import and the toolbar chunk entirely (AC-DEV-03, checked by scripts/check-no-dev-toolbar.mjs).
const DevToolbar = import.meta.env.DEV ? lazy(() => import('./dev/DevToolbar')) : undefined

export function Layout() {
  // Admin lists need room for tables (UI-03); everything else stays a narrow single column
  const width = useMatch('/admin/*') === null ? 'max-w-xl' : 'max-w-5xl'
  const signedIn = useSession().me !== undefined
  return (
    <div className="flex min-h-dvh flex-col">
      <OfflineBanner />
      <header className={`mx-auto w-full ${width} px-4 pt-5`}>
        <div className="flex items-center gap-3">
          <HortMark />
          <div className="min-w-0 flex-1">
            <h1>
              <Link to="/" className="inline-flex min-h-tap items-center no-underline">
                {texts.app.title}
              </Link>
            </h1>
            <p className="-mt-1 mb-0 text-sm text-muted">{texts.app.subtitle}</p>
          </div>
          {signedIn && (
            <Link to="/profile" className="profile-link">
              {texts.profile.link}
            </Link>
          )}
        </div>
        <div className="brand-stripe mt-3" />
      </header>
      <main className={`mx-auto w-full ${width} flex-1 px-4 pt-5 pb-12`}>
        <Outlet />
      </main>
      <footer className="site-footer">
        <svg className="site-footer__wave" viewBox="0 0 1440 80" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 48C240 8 480 8 720 40s480 40 720 0v40H0z" />
        </svg>
        <nav aria-label={texts.common.legalNav} className={`mx-auto flex ${width} flex-wrap justify-center gap-x-6 px-4 text-sm`}>
          <a className="inline-flex min-h-tap items-center" href={links.privacy}>
            {texts.common.privacy}
          </a>
          <a className="inline-flex min-h-tap items-center" href={links.imprint}>
            {texts.common.imprint}
          </a>
          <a className="inline-flex min-h-tap items-center" href={links.source}>
            {texts.common.source}
          </a>
        </nav>
        <p className="m-0 pb-3 text-center text-xs text-muted">{texts.common.version(__APP_VERSION__)}</p>
      </footer>
      {DevToolbar !== undefined && (
        <Suspense fallback={null}>
          <DevToolbar />
        </Suspense>
      )}
    </div>
  )
}
