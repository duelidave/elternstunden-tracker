import { useId, useState } from 'react'
import { errorText } from '../api/errors'
import { useConfirmTotp, useSetupTotp } from '../api/generated/endpoints/me/me'
import { texts } from '../i18n/de'
import { QrCode } from './QrCode'

/**
 * Setting up the admin TOTP (7.2, SEC-05): secret as QR code and text, confirm with a code, then show the recovery
 * codes once. Used in the profile and, when TOTP is required, during the login (MFA_PENDING).
 */
export function TotpSetup({ onDone }: { onDone: () => void }) {
  const codeId = useId()
  const setup = useSetupTotp()
  const confirm = useConfirmTotp()
  const [code, setCode] = useState('')

  if (confirm.data) {
    return (
      <div className="panel" aria-live="polite">
        <h3>{texts.totp.recoveryTitle}</h3>
        <p>{texts.totp.recoveryIntro}</p>
        <ul className="recovery-codes">
          {confirm.data.recoveryCodes.map((recovery) => (
            <li key={recovery}>
              <code>{recovery}</code>
            </li>
          ))}
        </ul>
        <button type="button" className="button w-full" onClick={onDone}>
          {texts.totp.recoveryDone}
        </button>
      </div>
    )
  }

  if (!setup.data) {
    return (
      <>
        {setup.isError && (
          <p className="alert" role="alert">
            {errorText(setup.error)}
          </p>
        )}
        <button
          type="button"
          className="button"
          disabled={setup.isPending}
          onClick={() => {
            setup.mutate()
          }}
        >
          {texts.totp.start}
        </button>
      </>
    )
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        confirm.mutate({ data: { code: code.trim() } })
      }}
      noValidate
    >
      <QrCode value={setup.data.otpauthUri} label={texts.totp.qrLabel} />
      <p className="text-muted">{texts.totp.manual}</p>
      <p>
        <code className="secret">{setup.data.secret}</code>
      </p>
      <div className="field">
        <label htmlFor={codeId}>{texts.totp.code}</label>
        <input
          id={codeId}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          value={code}
          onChange={(event) => {
            setCode(event.target.value)
          }}
        />
      </div>
      {confirm.isError && (
        <p className="alert" role="alert">
          {errorText(confirm.error)}
        </p>
      )}
      <button type="submit" className="button w-full" disabled={confirm.isPending || code.trim().length !== 6}>
        {texts.totp.confirm}
      </button>
    </form>
  )
}
