// Job links come from scraped listings, so they're data, not something we wrote. Only web links
// are followed: a "javascript:" or "data:" URL in an href would run when clicked.
export function safeHttpUrl(url: string | null | undefined): string | undefined {
  if (!url) return undefined
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : undefined
  } catch {
    return undefined
  }
}
