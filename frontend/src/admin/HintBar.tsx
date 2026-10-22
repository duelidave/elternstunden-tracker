import { Link, useSearchParams } from 'react-router'
import { hasCode } from '../api/errors'
import { useGetAdminOverview } from '../api/generated/endpoints/overview/overview'
import { texts } from '../i18n/de'
import { formatDuration } from '../time'

/**
 * Hint bar on every admin page (FS-ADMOV, D-11): how many families still miss confirmed hours in the school year of
 * the URL (?year=…), by default the current one. A click opens the filtered list.
 */
export function HintBar() {
  const [params] = useSearchParams()
  const year = params.get('year') ?? undefined
  const overview = useGetAdminOverview({ ...(year === undefined ? {} : { schoolYearId: year }), size: 1 })
  if (hasCode(overview.error, 'NO_CURRENT_SCHOOL_YEAR')) {
    return (
      <Link className="hint-bar" to="/admin/school-years">
        {texts.admin.overview.noYear}
      </Link>
    )
  }
  if (overview.data === undefined) {
    return null
  }
  const { missingCount, familyCount, schoolYear, requiredMinutes } = overview.data
  const search = new URLSearchParams({ onlyMissing: 'true' })
  if (year !== undefined) {
    search.set('year', year)
  }
  return (
    <Link className="hint-bar" to={{ pathname: '/admin/families', search: search.toString() }}>
      <span className="count" aria-hidden="true">
        {missingCount}
      </span>
      <span>
        {texts.admin.overview.hint(missingCount, familyCount, schoolYear.label, formatDuration(requiredMinutes))}
      </span>
    </Link>
  )
}
