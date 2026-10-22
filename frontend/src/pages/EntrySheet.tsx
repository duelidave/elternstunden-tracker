import { Navigate, useLocation, useNavigate, useOutletContext, useParams } from 'react-router'
import { errorText } from '../api/errors'
import { useCreateFamilyEntry, useUpdateFamilyEntry } from '../api/generated/endpoints/my-family/my-family'
import type { FamilyOverview } from '../api/generated/model'
import { EntryForm, type EntryValues } from '../components/EntryForm'
import { Sheet } from '../components/Sheet'
import { texts } from '../i18n/de'
import type { NoticeState } from './FamilyHome'
import { useRefreshFamily } from './familyData'

/** Entering or changing an entry as a bottom sheet over the overview (UI-04, 13.1 "/entries/new"). */
export function EntrySheet() {
  const overview = useOutletContext<FamilyOverview>()
  const { entryId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const create = useCreateFamilyEntry()
  const update = useUpdateFamilyEntry()
  const refresh = useRefreshFamily()
  const entry = entryId === undefined ? undefined : overview.entries.find((candidate) => candidate.id === entryId)

  const close = (notice?: string) => {
    const state: NoticeState = notice === undefined ? {} : { notice }
    void navigate({ pathname: '/', search: location.search }, { state })
  }

  // Only open entries of the open school year can be changed (INV-04 to INV-06)
  if (entryId === undefined ? !overview.canAddEntries : entry?.canEdit !== true) {
    return <Navigate to={{ pathname: '/', search: location.search }} replace />
  }

  const save = async (values: EntryValues) => {
    try {
      if (entry === undefined) {
        await create.mutateAsync({ data: values })
        await refresh()
        close(texts.family.saved)
      } else {
        await update.mutateAsync({
          entryId: entry.id,
          data: { ...values, startTime: values.startTime ?? '', version: entry.version },
        })
        await refresh()
        close(texts.family.updated)
      }
    } catch {
      // shown below
    }
  }

  const error = create.error ?? update.error
  return (
    <Sheet
      title={entry === undefined ? texts.entry.title : texts.entry.editTitle}
      onClose={() => {
        close()
      }}
    >
      {error !== null && (
        <p className="alert" role="alert">
          {errorText(error)}
        </p>
      )}
      <EntryForm
        initial={
          entry === undefined
            ? undefined
            : {
                workDate: entry.workDate,
                durationMinutes: entry.durationMinutes,
                description: entry.description,
                ...(entry.startTime === undefined ? {} : { startTime: entry.startTime }),
              }
        }
        minDate={overview.schoolYear.startsOn}
        submitLabel={entry === undefined ? texts.entry.submit : texts.entry.save}
        onSubmit={save}
      />
    </Sheet>
  )
}
