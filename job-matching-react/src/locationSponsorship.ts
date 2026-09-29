// Same choices as the Flask candidate forms.
export const SPONSORSHIP_TYPES = ['Need H1B', 'U.S. Citizen with green card'] as const

export interface LocationSponsorshipValue {
  location_city: string
  location_state: string
  location_country: string
  requires_sponsorship: boolean
  sponsorship_type: string[]
}

export const EMPTY_LOCATION_SPONSORSHIP: LocationSponsorshipValue = {
  location_city: '',
  location_state: '',
  location_country: '',
  requires_sponsorship: false,
  sponsorship_type: [],
}

export function toLocationSponsorshipValue(candidate: {
  location_city: string | null
  location_state: string | null
  location_country: string | null
  requires_sponsorship: boolean | null
  sponsorship_type: string[] | null
}): LocationSponsorshipValue {
  return {
    location_city: candidate.location_city ?? '',
    location_state: candidate.location_state ?? '',
    location_country: candidate.location_country ?? '',
    requires_sponsorship: !!candidate.requires_sponsorship,
    sponsorship_type: candidate.sponsorship_type ?? [],
  }
}

// API shape: blank location parts become null, and types are only kept while
// sponsorship is required.
export function toLocationSponsorshipPayload(value: LocationSponsorshipValue) {
  return {
    location_city: value.location_city.trim() || null,
    location_state: value.location_state.trim() || null,
    location_country: value.location_country.trim() || null,
    requires_sponsorship: value.requires_sponsorship,
    sponsorship_type: value.requires_sponsorship ? value.sponsorship_type : [],
  }
}
