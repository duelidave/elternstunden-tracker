import { forwardRef, useEffect, useImperativeHandle } from 'react'
import type { CaptchaHandle } from '../components/Captcha'

export const CAPTCHA_PAYLOAD = 'solved-captcha'

/** Stand-in for the ALTCHA widget: "solves" right away (use with vi.mock of ../components/Captcha). */
export const FakeCaptcha = forwardRef<CaptchaHandle, { onSolved: (payload: string | undefined) => void }>(
  function FakeCaptcha({ onSolved }, ref) {
    useImperativeHandle(ref, () => ({
      reset: () => {
        onSolved(CAPTCHA_PAYLOAD)
      },
    }))
    useEffect(() => {
      onSolved(CAPTCHA_PAYLOAD)
    }, [onSolved])
    return null
  },
)
