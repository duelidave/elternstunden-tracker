import { useGetStatus } from './api/generated/endpoints/system/system'
import { texts } from './i18n/de'

function statusText(isPending: boolean, isUp: boolean): string {
  if (isPending) {
    return texts.status.checking
  }
  return isUp ? texts.status.reachable : texts.status.unreachable
}

export default function App() {
  const status = useGetStatus()
  const isUp = status.isSuccess && status.data.status === 'UP'

  return (
    <main className="shell">
      <header className="shell__header">
        <h1 className="shell__title">{texts.app.title}</h1>
        <p className="shell__subtitle">{texts.app.subtitle}</p>
      </header>
      <p className="status" role="status" aria-live="polite">
        {statusText(status.isPending, isUp)}
      </p>
    </main>
  )
}
