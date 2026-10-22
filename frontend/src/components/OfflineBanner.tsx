import { useSyncExternalStore } from 'react'
import { texts } from '../i18n/de'

function subscribe(onChange: () => void) {
  window.addEventListener('online', onChange)
  window.addEventListener('offline', onChange)
  return () => {
    window.removeEventListener('online', onChange)
    window.removeEventListener('offline', onChange)
  }
}

/** UI-08: a banner while the device is offline; there is no offline writing. */
export function OfflineBanner() {
  const online = useSyncExternalStore(
    subscribe,
    () => navigator.onLine,
    () => true,
  )
  if (online) {
    return null
  }
  return (
    <p className="offline-banner" role="status">
      {texts.common.offline}
    </p>
  )
}
