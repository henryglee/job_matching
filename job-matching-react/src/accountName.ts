export interface AccountNameValue {
  firstName: string
  lastName: string
}

export function isSameAccountName(a: AccountNameValue, b: AccountNameValue): boolean {
  return a.firstName.trim() === b.firstName.trim() && a.lastName.trim() === b.lastName.trim()
}

// Returns an error message, or null when the name can be saved. Both parts are required
// together — the backend rejects a blank half.
export function validateAccountName({ firstName, lastName }: AccountNameValue): string | null {
  return firstName.trim() && lastName.trim() ? null : 'First name and last name are required.'
}
