import { useId, useState } from 'react'
import { Link } from 'react-router'
import { errorText } from '../api/errors'
import { useImportChildren } from '../api/generated/endpoints/children/children'
import type { ImportResult } from '../api/generated/model'
import { texts } from '../i18n/de'
import { useInvalidateAll } from './invalidate'

/** CSV import of children (7.7): preview first (dry run), then import. All or nothing on the server. */
export function ImportPage() {
  const fileId = useId()
  const [csv, setCsv] = useState<string | undefined>()
  const [result, setResult] = useState<ImportResult | undefined>()
  const importChildren = useImportChildren()
  const invalidate = useInvalidateAll()

  const run = async (dryRun: boolean) => {
    if (csv === undefined) {
      return
    }
    try {
      setResult(await importChildren.mutateAsync({ data: csv, params: { dryRun } }))
      if (!dryRun) {
        await invalidate()
      }
    } catch {
      setResult(undefined)
    }
  }

  return (
    <section className="card">
      <h2>{texts.admin.import.title}</h2>
      <p className="text-muted">{texts.admin.import.intro}</p>
      <div className="field">
        <label htmlFor={fileId}>{texts.admin.import.file}</label>
        <input
          id={fileId}
          type="file"
          accept=".csv,text/csv"
          onChange={(event) => {
            setResult(undefined)
            const file = event.target.files?.[0]
            if (file === undefined) {
              setCsv(undefined)
              return
            }
            void file.text().then(setCsv)
          }}
        />
      </div>
      <div className="actions">
        <button
          type="button"
          className="button button--secondary"
          disabled={csv === undefined || importChildren.isPending}
          onClick={() => void run(true)}
        >
          {texts.admin.import.preview}
        </button>
        <button
          type="button"
          className="button"
          disabled={csv === undefined || importChildren.isPending || result?.dryRun !== true || result.errors.length > 0}
          onClick={() => void run(false)}
        >
          {texts.admin.import.apply}
        </button>
      </div>
      {importChildren.isError && (
        <p className="alert" role="alert">
          {errorText(importChildren.error)}
        </p>
      )}
      {result && <ImportSummary result={result} />}
      <p className="card__footer">
        <Link to="/admin/children">{texts.admin.back}</Link>
      </p>
    </section>
  )
}

function ImportSummary({ result }: { result: ImportResult }) {
  return (
    <div className="panel mt-4" aria-live="polite">
      <p className="m-0">{texts.admin.import.result(result.created, result.updated, result.unchanged)}</p>
      {result.applied && <p className="notice">{texts.admin.import.applied}</p>}
      {result.errors.length > 0 && (
        <>
          <p className="field__error">{texts.admin.import.errorsTitle}</p>
          <ul>
            {result.errors.map((error) => (
              <li key={`${String(error.line)}-${error.code}`}>
                {texts.admin.import.errorLine(error.line, texts.admin.import.errors[error.code])}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
