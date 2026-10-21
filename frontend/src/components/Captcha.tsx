// JSX typings of <altcha-widget>; types only, the runtime module does not resolve outside the bundler
/// <reference types="altcha/types/react" />
import 'altcha/altcha.css'
import 'altcha/external'
import 'altcha/i18n/de'
import Pbkdf2Worker from 'altcha/workers/pbkdf2?worker'
import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'

// CSP-friendly build (SEC-10): no blob: workers, the PBKDF2 worker is a bundled file of our own origin
$altcha.algorithms.set('PBKDF2/SHA-256', () => new Pbkdf2Worker())

export interface CaptchaHandle {
  /** A solution is single-use: reset after every submit so the next one gets a fresh challenge. */
  reset: () => void
}

interface CaptchaProps {
  onSolved: (payload: string | undefined) => void
}

interface WidgetElement extends HTMLElement {
  reset?: () => void
}

/** ALTCHA proof of work (D-07, 9.1). Solves automatically, no puzzle for the user. */
export const Captcha = forwardRef<CaptchaHandle, CaptchaProps>(function Captcha({ onSolved }, ref) {
  const widget = useRef<WidgetElement>(null)

  useImperativeHandle(ref, () => ({
    reset: () => {
      widget.current?.reset?.()
      onSolved(undefined)
    },
  }))

  useEffect(() => {
    const element = widget.current
    if (element === null) {
      return
    }
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ state?: string; payload?: string }>).detail
      onSolved(detail.state === 'verified' ? detail.payload : undefined)
    }
    element.addEventListener('statechange', listener)
    return () => {
      element.removeEventListener('statechange', listener)
    }
  }, [onSolved])

  return (
    <altcha-widget
      ref={widget}
      challenge="/api/v1/auth/captcha"
      auto="onload"
      language="de"
      name="captcha"
    />
  )
})
