import { type ComponentProps, forwardRef, useId } from 'react'
import { useListFamilies } from '../api/generated/endpoints/families/families'
import { texts } from '../i18n/de'

type FamilySelectProps = ComponentProps<'select'> & { label: string; allowNew?: boolean }

/** All families to pick from (at most 200, enough for the Hort, 1.2). */
export const FamilySelect = forwardRef<HTMLSelectElement, FamilySelectProps>(function FamilySelect(
  { label, allowNew = false, ...select },
  ref,
) {
  const id = useId()
  const families = useListFamilies({ size: 200 })
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <select id={id} ref={ref} {...select}>
        {allowNew ? <option value="">{texts.admin.children.newFamily}</option> : <option value="" disabled />}
        {families.data?.content.map((family) => (
          <option key={family.id} value={family.id}>
            {family.displayLabel || texts.admin.families.noLabel}
          </option>
        ))}
      </select>
    </div>
  )
})
