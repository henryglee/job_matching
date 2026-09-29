// "480 / 500" under a field, shown only once the text is close to its limit (80%), so it stays out
// of the way until it matters. maxLength stops typing at the limit without saying why.
export function LimitHint({ value, max, label }: { value: string; max: number; label?: string }) {
  if (value.length < max * 0.8) return null
  const atLimit = value.length >= max
  return (
    <p className="field-hint">
      {label ? `${label}: ` : ''}
      {value.length} / {max} characters{atLimit ? ' (limit reached)' : ''}
    </p>
  )
}
