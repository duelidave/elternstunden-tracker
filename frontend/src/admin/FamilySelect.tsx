import { useQuery } from '@tanstack/react-query'
import { type ComponentProps, forwardRef, useId } from 'react'
import { getListFamiliesQueryKey, listFamilies } from '../api/generated/endpoints/families/families'
import type { FamilySummary } from '../api/generated/model'
import { texts } from '../i18n/de'

type FamilySelectProps = ComponentProps<'select'> & { label: string; allowNew?: boolean; emptyLabel?: string }

const PAGE_SIZE = 200

/** Every family, page by page (at most 200 per request, 8.1), so the list does not stop at 200 families. */
async function allFamilies(): Promise<FamilySummary[]> {
  const result: FamilySummary[] = []
  for (let page = 0; ; page++) {
    const chunk = await listFamilies({ page, size: PAGE_SIZE })
    result.push(...chunk.content)
    if (page + 1 >= chunk.totalPages) {
      return result
    }
  }
}

/** All families to pick from. */
export const FamilySelect = forwardRef<HTMLSelectElement, FamilySelectProps>(function FamilySelect(
  { label, allowNew = false, emptyLabel, ...select },
  ref,
) {
  const id = useId()
  const families = useQuery({ queryKey: [...getListFamiliesQueryKey(), 'all'], queryFn: allFamilies })
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <select id={id} ref={ref} {...select}>
        {allowNew || emptyLabel !== undefined ? (
          <option value="">{emptyLabel ?? texts.admin.children.newFamily}</option>
        ) : (
          <option value="" disabled />
        )}
        {families.data?.map((family) => (
          <option key={family.id} value={family.id}>
            {family.displayLabel || texts.admin.families.noLabel}
          </option>
        ))}
      </select>
    </div>
  )
})
