import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import {
  CreateFamilyEntryBody,
  createFamilyEntryBodyDescriptionMax,
} from '../api/generated/zod/my-family/my-family.zod'
import { texts } from '../i18n/de'
import { formatDuration, normalizeTime, todayInBerlin } from '../time'
import { DateField } from './DateField'
import { TimeField } from './TimeField'

/** What an entry consists of (7.4); `startTime` only in the exact mode. */
export interface EntryValues {
  workDate: string
  startTime?: string
  durationMinutes: number
  description: string
}

const QUICK_MINUTES = [30, 60, 120, 180] as const
const STEP = 15
const MIN_MINUTES = 15
const MAX_MINUTES = 720

const body = CreateFamilyEntryBody.shape
// Same rules as the server (UI-08): the generated contract schema plus the date and trim rules of 7.4
const schema = z
  .object({
    exact: z.boolean(),
    timeRequired: z.boolean(),
    workDate: body.workDate,
    // "9" or "9:00" count as 09:00 (UI-12)
    startTime: z.string().transform((text) => normalizeTime(text) ?? text.trim()),
    durationMinutes: body.durationMinutes,
    description: z.string().trim().pipe(body.description),
    minDate: z.string(),
  })
  .superRefine((values, context) => {
    if (values.workDate > todayInBerlin() || values.workDate < values.minDate) {
      context.addIssue({ code: 'custom', path: ['workDate'], message: 'range' })
    }
    const time = values.startTime.trim()
    const needed = values.exact && values.timeRequired
    if ((needed || time !== '') && !body.startTime.unwrap().safeParse(time).success) {
      context.addIssue({ code: 'custom', path: ['startTime'], message: 'time' })
    }
  })
type EntryForm = z.input<typeof schema>

interface EntryFormProps {
  initial?: EntryValues | undefined
  /** Earliest date, e.g. the start of the school year. */
  minDate: string
  submitLabel: string
  onSubmit: (values: EntryValues) => Promise<void>
}

/**
 * Entering hours (FS-ENTRY 7.4, D-09): quick mode with duration chips and today as date, or the exact mode with date,
 * start time and a 15-minute stepper. The standard case takes four interactions (UI-04): open, chip, text, submit.
 */
export function EntryForm({ initial, minDate, submitLabel, onSubmit }: EntryFormProps) {
  const id = useId()
  const startsExact =
    initial !== undefined &&
    (initial.startTime !== undefined || !(QUICK_MINUTES as readonly number[]).includes(initial.durationMinutes))
  const [exact, setExact] = useState(startsExact)
  const form = useForm<EntryForm>({
    resolver: zodResolver(schema),
    // Errors show once a field was left or the form sent, then follow every keystroke (UI-08)
    mode: 'onTouched',
    defaultValues: {
      exact: startsExact,
      // An existing entry without a time keeps working without one
      timeRequired: initial === undefined || initial.startTime !== undefined,
      workDate: initial?.workDate ?? todayInBerlin(),
      startTime: initial?.startTime ?? '',
      durationMinutes: initial?.durationMinutes,
      description: initial?.description ?? '',
      minDate,
    },
  })
  const errors = form.formState.errors
  const duration = useWatch({ control: form.control, name: 'durationMinutes' })
  const description = useWatch({ control: form.control, name: 'description' })

  const chooseExact = () => {
    setExact(true)
    form.setValue('exact', true)
    form.setValue('durationMinutes', duration ?? 60, { shouldValidate: form.formState.isSubmitted })
  }

  const submit = form.handleSubmit(async (values) => {
    const time = values.startTime.trim()
    await onSubmit({
      workDate: values.workDate,
      durationMinutes: values.durationMinutes,
      description: values.description.trim(),
      ...(values.exact && time !== '' ? { startTime: time } : {}),
    })
  })

  return (
    <form onSubmit={(event) => void submit(event)} noValidate>
      <fieldset className="entry-duration" aria-describedby={errors.durationMinutes ? `${id}-duration` : undefined}>
        <legend>{texts.entry.duration}</legend>
        {!exact && (
          <div className="chips">
            {QUICK_MINUTES.map((minutes) => (
              <button
                key={minutes}
                type="button"
                className="chip-button"
                aria-pressed={duration === minutes}
                onClick={() => {
                  form.setValue('durationMinutes', minutes, { shouldValidate: form.formState.isSubmitted })
                }}
              >
                {formatDuration(minutes)}
              </button>
            ))}
            <button type="button" className="chip-button" aria-pressed={false} onClick={chooseExact}>
              {texts.entry.otherDuration}
            </button>
          </div>
        )}
        {exact && (
          <div className="stepper">
            <button
              type="button"
              className="stepper__button"
              aria-label={texts.entry.less}
              disabled={(duration ?? 0) <= MIN_MINUTES}
              onClick={() => {
                form.setValue('durationMinutes', Math.max(MIN_MINUTES, (duration ?? 60) - STEP))
              }}
            >
              −
            </button>
            <output className="stepper__value" aria-live="polite">
              {formatDuration(duration ?? 60)}
            </output>
            <button
              type="button"
              className="stepper__button"
              aria-label={texts.entry.more}
              disabled={(duration ?? 0) >= MAX_MINUTES}
              onClick={() => {
                form.setValue('durationMinutes', Math.min(MAX_MINUTES, (duration ?? 60) + STEP))
              }}
            >
              +
            </button>
          </div>
        )}
        {errors.durationMinutes && (
          <p id={`${id}-duration`} className="field__error">
            {texts.fields.duration}
          </p>
        )}
      </fieldset>
      <Controller
        control={form.control}
        name="workDate"
        render={({ field }) => (
          <DateField
            label={texts.entry.date}
            name={field.name}
            value={field.value}
            onChange={field.onChange}
            onBlur={field.onBlur}
            min={minDate}
            max={todayInBerlin()}
            error={errors.workDate ? texts.fields.entryDate : undefined}
          />
        )}
      />
      {exact && (
        <Controller
          control={form.control}
          name="startTime"
          render={({ field }) => (
            <TimeField
              label={texts.entry.startTime}
              name={field.name}
              value={field.value}
              onChange={field.onChange}
              onBlur={field.onBlur}
              error={errors.startTime ? texts.fields.time : undefined}
            />
          )}
        />
      )}
      <div className="field">
        <label htmlFor={`${id}-description`}>
          {texts.entry.description} <span className="field__required">{texts.fields.requiredMark}</span>
        </label>
        <textarea
          id={`${id}-description`}
          rows={3}
          // The browser stops typing at the limit of the contract (D-09)
          maxLength={createFamilyEntryBodyDescriptionMax}
          required
          aria-invalid={errors.description !== undefined}
          aria-describedby={`${id}-hint ${id}-counter${errors.description ? ` ${id}-description-error` : ''}`}
          {...form.register('description')}
        />
        <div className="field__meta">
          <p id={`${id}-hint`} className="m-0">
            {texts.entry.descriptionHint}
          </p>
          <p
            id={`${id}-counter`}
            className={
              description.length >= createFamilyEntryBodyDescriptionMax ? 'field__counter field__counter--full' : 'field__counter'
            }
          >
            {texts.fields.counter(description.length, createFamilyEntryBodyDescriptionMax)}
          </p>
        </div>
        {errors.description && (
          <p id={`${id}-description-error`} className="field__error">
            {description.trim() === '' ? texts.fields.description : texts.fields.descriptionShort}
          </p>
        )}
      </div>
      <button type="submit" className="button w-full" disabled={form.formState.isSubmitting}>
        {submitLabel}
      </button>
    </form>
  )
}
