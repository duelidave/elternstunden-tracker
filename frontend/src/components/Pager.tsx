import { texts } from '../i18n/de'

interface PagerProps {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}

/** Paging below admin lists; pages are 0-based in the API (8.1), 1-based on screen. */
export function Pager({ page, totalPages, totalElements, onPage }: PagerProps) {
  return (
    <div className="pager">
      <span className="text-muted">{texts.admin.total(totalElements)}</span>
      {totalPages > 1 && (
        <div className="actions mt-0">
          <button
            type="button"
            className="button button--secondary"
            disabled={page === 0}
            onClick={() => {
              onPage(page - 1)
            }}
          >
            {texts.admin.previous}
          </button>
          <span className="self-center">{texts.admin.page(page + 1, totalPages)}</span>
          <button
            type="button"
            className="button button--secondary"
            disabled={page + 1 >= totalPages}
            onClick={() => {
              onPage(page + 1)
            }}
          >
            {texts.admin.next}
          </button>
        </div>
      )}
    </div>
  )
}
