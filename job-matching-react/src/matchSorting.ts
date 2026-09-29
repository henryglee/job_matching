// Client-side sorting for the job matches page, ported from the Flask job_matches.html.
import type { CandidateLocation, JobMatch } from './types'

export type MatchSort = 'match' | 'location' | 'skills' | 'experience'

export const MATCH_SORTS: { value: MatchSort; label: string }[] = [
  { value: 'match', label: 'Best Match' },
  { value: 'location', label: 'Location' },
  { value: 'skills', label: 'Skills Match' },
  { value: 'experience', label: 'Years of Experience' },
]

// Approximate geographic centroids for US states (lat, lon).
const STATE_CENTROIDS: Record<string, [number, number]> = {
  AL: [32.8, -86.8], AK: [64.2, -153.4], AZ: [34.3, -111.1], AR: [34.9, -92.4], CA: [36.8, -119.4],
  CO: [39.0, -105.5], CT: [41.6, -72.7], DE: [39.0, -75.5], FL: [27.8, -81.7], GA: [32.7, -83.4],
  HI: [20.9, -157.0], ID: [44.4, -114.6], IL: [40.0, -89.2], IN: [40.3, -86.1], IA: [42.0, -93.2],
  KS: [38.5, -98.4], KY: [37.7, -85.0], LA: [31.2, -91.8], ME: [45.4, -69.0], MD: [39.1, -76.8],
  MA: [42.3, -71.8], MI: [44.3, -85.4], MN: [46.4, -93.1], MS: [32.7, -89.7], MO: [38.5, -92.5],
  MT: [47.0, -110.5], NE: [41.5, -99.9], NV: [39.3, -116.6], NH: [43.7, -71.6], NJ: [40.1, -74.5],
  NM: [34.8, -106.2], NY: [42.7, -74.2], NC: [35.6, -79.4], ND: [47.5, -100.5], OH: [40.4, -82.8],
  OK: [35.6, -97.5], OR: [44.1, -120.5], PA: [40.9, -77.8], RI: [41.7, -71.5], SC: [33.9, -80.9],
  SD: [44.4, -100.2], TN: [35.9, -86.7], TX: [31.5, -99.3], UT: [39.3, -111.1], VT: [44.1, -72.7],
  VA: [37.8, -78.2], WA: [47.4, -120.6], WV: [38.6, -80.6], WI: [44.3, -89.6], WY: [43.0, -107.6],
  DC: [38.9, -77.0],
}

const STATE_ABBR: Record<string, string> = {
  alabama: 'AL', alaska: 'AK', arizona: 'AZ', arkansas: 'AR', california: 'CA', colorado: 'CO',
  connecticut: 'CT', delaware: 'DE', florida: 'FL', georgia: 'GA', hawaii: 'HI', idaho: 'ID',
  illinois: 'IL', indiana: 'IN', iowa: 'IA', kansas: 'KS', kentucky: 'KY', louisiana: 'LA',
  maine: 'ME', maryland: 'MD', massachusetts: 'MA', michigan: 'MI', minnesota: 'MN',
  mississippi: 'MS', missouri: 'MO', montana: 'MT', nebraska: 'NE', nevada: 'NV',
  'new hampshire': 'NH', 'new jersey': 'NJ', 'new mexico': 'NM', 'new york': 'NY',
  'north carolina': 'NC', 'north dakota': 'ND', ohio: 'OH', oklahoma: 'OK', oregon: 'OR',
  pennsylvania: 'PA', 'rhode island': 'RI', 'south carolina': 'SC', 'south dakota': 'SD',
  tennessee: 'TN', texas: 'TX', utah: 'UT', vermont: 'VT', virginia: 'VA', washington: 'WA',
  'west virginia': 'WV', wisconsin: 'WI', wyoming: 'WY', 'district of columbia': 'DC',
}

// Longest names first, so "west virginia" wins over "virginia" and "arkansas" over
// "kansas" (the Flask page checked in alphabetical order and got West Virginia wrong).
const STATE_NAMES_LONGEST_FIRST = Object.keys(STATE_ABBR).sort((a, b) => b.length - a.length)

function haversineKm([lat1, lon1]: [number, number], [lat2, lon2]: [number, number]): number {
  const R = 6371
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLon = toRad(lon2 - lon1)
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

// "San Francisco, CA", "Austin, TX, US" or a full state name -> two-letter code.
export function extractStateAbbr(location: string | null | undefined): string | null {
  if (!location) return null
  const s = location.trim()
  const short = s.match(/,\s*([A-Z]{2})(?:\s*,|$)/)
  if (short) return short[1]
  const lower = s.toLowerCase()
  const name = STATE_NAMES_LONGEST_FIRST.find((n) => lower.includes(n))
  return name ? STATE_ABBR[name] : null
}

function isRemote(job: JobMatch): boolean {
  return !!job.is_remote || (job.location ?? '').toLowerCase().includes('remote')
}

// Tiers: 0 remote, 1 same city, 2 same state, 3 elsewhere (then by state-centroid distance).
function locationSortKey(job: JobMatch, candCity: string, candState: string): { tier: number; dist: number } {
  if (isRemote(job)) return { tier: 0, dist: 0 }
  const jobLocation = job.location ?? ''
  if (candCity && jobLocation.toLowerCase().includes(candCity.toLowerCase())) return { tier: 1, dist: 0 }
  const jobState = extractStateAbbr(jobLocation)
  if (candState && jobState && jobState === candState) return { tier: 2, dist: 0 }
  const from = STATE_CENTROIDS[candState]
  const to = jobState ? STATE_CENTROIDS[jobState] : undefined
  return { tier: 3, dist: from && to ? haversineKm(from, to) : Infinity }
}

/**
 * Minimum years of experience a job asks for: 0 for entry-level, Infinity when unknown
 * (sorted last). Checks experience_range, then description, then title; falls back to
 * 4 for "Senior" and 2 for "Junior" titles.
 */
export function extractMinYears(job: JobMatch): number {
  const title = job.title ?? ''
  for (const source of [job.experience_range ?? '', job.description ?? '', title]) {
    if (!source) continue
    if (/entry[\s-]?level|no experience required/i.test(source)) return 0
    const m =
      source.match(/(\d+)\s*(?:\+|to|-)\s*\d*\s*\+?\s*years?/i) ??
      source.match(/(\d+)\s*\+?\s*years?\s+(?:of\s+)?experience/i) ??
      source.match(/experience\D{0,20}?(\d+)\s*\+?\s*years?/i)
    if (m) return parseInt(m[1], 10)
  }
  if (/\bsenior\b/i.test(title)) return 4
  if (/\bjunior\b/i.test(title)) return 2
  return Infinity
}

const bySimilarity = (a: JobMatch, b: JobMatch) => b.similarity_score - a.similarity_score

// Numeric ascending compare that treats two Infinities as equal (Infinity - Infinity is NaN).
const ascending = (a: number, b: number) => (a === b ? 0 : a - b)

export function sortMatches(matches: JobMatch[], sortBy: MatchSort, candidateLocation: CandidateLocation): JobMatch[] {
  const sorted = [...matches]
  switch (sortBy) {
    case 'experience':
      return sorted.sort((a, b) => ascending(extractMinYears(a), extractMinYears(b)) || bySimilarity(a, b))
    case 'location': {
      const candCity = candidateLocation.city ?? ''
      const rawState = candidateLocation.state ?? ''
      const candState = extractStateAbbr(rawState) ?? rawState.trim().toUpperCase()
      return sorted.sort((a, b) => {
        const ka = locationSortKey(a, candCity, candState)
        const kb = locationSortKey(b, candCity, candState)
        return ascending(ka.tier, kb.tier) || ascending(ka.dist, kb.dist) || bySimilarity(a, b)
      })
    }
    case 'skills':
      return sorted.sort(
        (a, b) => (b.match_breakdown?.skills ?? 0) - (a.match_breakdown?.skills ?? 0) || bySimilarity(a, b),
      )
    default:
      return sorted.sort(bySimilarity)
  }
}
