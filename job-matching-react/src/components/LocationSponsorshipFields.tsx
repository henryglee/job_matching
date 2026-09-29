import { SPONSORSHIP_TYPES, type LocationSponsorshipValue } from '../locationSponsorship'
import { LOCATION_MAX } from '../limits'
import { LimitHint } from './LimitHint'

interface LocationSponsorshipFieldsProps {
  value: LocationSponsorshipValue
  onChange: (value: LocationSponsorshipValue) => void
  idPrefix: string
}

export function LocationSponsorshipFields({ value, onChange, idPrefix }: LocationSponsorshipFieldsProps) {
  const set = <K extends keyof LocationSponsorshipValue>(key: K, fieldValue: LocationSponsorshipValue[K]) => {
    onChange({ ...value, [key]: fieldValue })
  }

  const toggleType = (type: string) => {
    const types = value.sponsorship_type.includes(type)
      ? value.sponsorship_type.filter((t) => t !== type)
      : [...value.sponsorship_type, type]
    set('sponsorship_type', types)
  }

  return (
    <>
      <fieldset className="form-group fieldset-plain">
        <legend>Location</legend>
        <div className="form-row form-row-3">
          <input
            id={`${idPrefix}-city`}
            type="text"
            aria-label="City"
            placeholder="City"
            maxLength={LOCATION_MAX}
            value={value.location_city}
            onChange={(e) => set('location_city', e.target.value)}
          />
          <input
            id={`${idPrefix}-state`}
            type="text"
            aria-label="State / Province"
            placeholder="State / Province"
            maxLength={LOCATION_MAX}
            value={value.location_state}
            onChange={(e) => set('location_state', e.target.value)}
          />
          <input
            id={`${idPrefix}-country`}
            type="text"
            aria-label="Country"
            placeholder="Country"
            maxLength={LOCATION_MAX}
            value={value.location_country}
            onChange={(e) => set('location_country', e.target.value)}
          />
        </div>
        <LimitHint value={value.location_city} max={LOCATION_MAX} label="City" />
        <LimitHint value={value.location_state} max={LOCATION_MAX} label="State / Province" />
        <LimitHint value={value.location_country} max={LOCATION_MAX} label="Country" />
      </fieldset>

      <div className="form-group">
        <div className="checkbox-row">
          <input
            id={`${idPrefix}-requires-sponsorship`}
            type="checkbox"
            checked={value.requires_sponsorship}
            onChange={(e) =>
              // Unchecking clears the types, as on the Flask form.
              onChange({ ...value, requires_sponsorship: e.target.checked, sponsorship_type: e.target.checked ? value.sponsorship_type : [] })
            }
          />
          <label htmlFor={`${idPrefix}-requires-sponsorship`} style={{ margin: 0 }}>
            Do you require any sponsorships to work in the United States?
          </label>
        </div>
        {value.requires_sponsorship && (
          <div className="sponsorship-types" role="group" aria-labelledby={`${idPrefix}-sponsorship-heading`}>
            <p id={`${idPrefix}-sponsorship-heading`} className="sponsorship-types-heading">
              Select sponsorship type(s):
            </p>
            {SPONSORSHIP_TYPES.map((type, i) => (
              <div key={type} className="checkbox-row">
                <input
                  id={`${idPrefix}-sponsorship-${i}`}
                  type="checkbox"
                  checked={value.sponsorship_type.includes(type)}
                  onChange={() => toggleType(type)}
                />
                <label htmlFor={`${idPrefix}-sponsorship-${i}`} style={{ margin: 0 }}>
                  {type}
                </label>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}
