export type ExplainLanguage = '' | 'EN' | 'DE'

const MATCH_OPTIONS: { value: ExplainLanguage; label: string }[] = [
  { value: '', label: 'Match my question' },
  { value: 'EN', label: 'English' },
  { value: 'DE', label: 'Deutsch' },
]

const FIXED_OPTIONS = MATCH_OPTIONS.filter((option) => option.value !== '')

export function ExplainLanguageField({
  name,
  value,
  onChange,
  matchQuestion = false,
}: {
  name: string
  value: ExplainLanguage
  onChange: (value: ExplainLanguage) => void
  matchQuestion?: boolean
}) {
  const options = matchQuestion ? MATCH_OPTIONS : FIXED_OPTIONS
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium text-ink-soft">Explain in</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <label
            key={option.value || 'match'}
            className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-line bg-mist/70 px-3 py-1.5 text-sm text-ink-soft has-[:checked]:border-accent/40 has-[:checked]:bg-white has-[:checked]:text-ink"
          >
            <input
              type="radio"
              name={name}
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
