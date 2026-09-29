// A date with no time ("2026-09-28", e.g. a posting date) is a calendar day, not a moment. Parsing
// it with new Date() reads it as midnight UTC, which is the previous evening in the Americas.
const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const dateOnly = DATE_ONLY.exec(value)
  const date = dateOnly ? new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3])) : new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString()
}

// Timestamps from the server (created, updated, sent, applied) are UTC but carry no zone
// ("2026-09-29T21:05:00"), which new Date() would read as the viewer's local time and show hours off,
// sometimes on the wrong day. Read them as UTC. Not for calendar dates such as a posting date or a
// graduation date: those have no time zone, and formatDate shows them as written.
export function formatTimestamp(value: string | null | undefined): string {
  if (!value) return '—'
  const hasZone = /(Z|[+-]\d{2}:?\d{2})$/i.test(value)
  return formatDate(value.includes('T') && !hasZone ? `${value}Z` : value)
}

export function toDateInputValue(value: string | null | undefined): string {
  if (!value) return ''
  if (DATE_ONLY.test(value)) return value
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toISOString().slice(0, 10)
}

export function formatSalaryRange(min: number | null, max: number | null, currency: string | null): string | null {
  if (min == null && max == null) return null
  const prefix = currency && currency !== 'USD' ? `${currency} ` : '$'
  if (min != null && max != null) return `${prefix}${min.toLocaleString()} - ${prefix}${max.toLocaleString()}`
  const value = (min ?? max) as number
  return `${prefix}${value.toLocaleString()}`
}
