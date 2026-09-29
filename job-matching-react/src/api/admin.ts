import { api } from './client'
import type { AdminUser, AiUsageReport, Role } from '../types'

export function getUsers() {
  return api.get<{ users: AdminUser[] }>('/api/admin/users')
}

export function getRoles() {
  return api.get<{ roles: Role[] }>('/api/roles')
}

export function assignRole(userId: string, roleId: string) {
  return api.post<{ message: string }>(`/api/admin/users/${userId}/roles`, { role_id: roleId })
}

export function removeRole(userId: string, roleId: string) {
  return api.delete<{ message: string }>(`/api/admin/users/${userId}/roles/${roleId}`)
}

export function linkCandidateProfile(userId: string, candidateId: string) {
  return api.put<{ message: string }>(`/api/admin/users/${userId}/candidate-profile`, {
    candidate_id: candidateId,
  })
}

export function unlinkCandidateProfile(userId: string) {
  return api.delete<{ message: string }>(`/api/admin/users/${userId}/candidate-profile`)
}

export function deleteUser(userId: string) {
  return api.delete<{ message: string }>(`/api/admin/users/${userId}`)
}

export function createRole(roleName: string) {
  return api.post<Role>('/api/roles', { role_name: roleName })
}

export function updateRole(roleId: string, roleName: string) {
  return api.put<{ message: string }>(`/api/roles/${roleId}`, { role_name: roleName })
}

export function deleteRole(roleId: string) {
  return api.delete<{ message: string }>(`/api/roles/${roleId}`)
}

// AI usage per candidate since the server started (Admin only). Counts only.
export function getAiUsage() {
  return api.get<AiUsageReport>('/api/admin/ai-usage')
}
