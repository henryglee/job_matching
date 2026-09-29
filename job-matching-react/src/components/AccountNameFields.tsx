import type { AccountNameValue } from '../accountName'

interface AccountNameFieldsProps {
  value: AccountNameValue
  onChange: (value: AccountNameValue) => void
  idPrefix: string
}

// First/last name of the user account (separate from the candidate profile's full name).
export function AccountNameFields({ value, onChange, idPrefix }: AccountNameFieldsProps) {
  return (
    <div className="form-row">
      <div className="form-group">
        <label htmlFor={`${idPrefix}-first-name`}>First Name</label>
        <input
          id={`${idPrefix}-first-name`}
          type="text"
          maxLength={200}
          value={value.firstName}
          onChange={(e) => onChange({ ...value, firstName: e.target.value })}
        />
      </div>
      <div className="form-group">
        <label htmlFor={`${idPrefix}-last-name`}>Last Name</label>
        <input
          id={`${idPrefix}-last-name`}
          type="text"
          maxLength={200}
          value={value.lastName}
          onChange={(e) => onChange({ ...value, lastName: e.target.value })}
        />
      </div>
    </div>
  )
}
