import { api } from './client'

// The signed-in user's own account name.
export function updateOwnName(firstName: string, lastName: string) {
  return api.put<{ message: string }>('/api/user/name', { first_name: firstName, last_name: lastName })
}

// Any user's account name (Admin/Staff only).
export function updateUserName(userId: string, firstName: string, lastName: string) {
  return api.put<{ message: string }>(`/api/admin/users/${userId}/name`, {
    first_name: firstName,
    last_name: lastName,
  })
}
