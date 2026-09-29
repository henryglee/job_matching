import { api } from './client'
import type { Role } from '../types'

export interface CheckRolesResponse {
  roles: Role[]
}

export function checkRoles(login: string, password: string) {
  return api.postPublic<CheckRolesResponse>('/api/auth/check-roles', { login, password })
}

export interface LoginResponse {
  token: string
  active_role: string
  candidate_profile_id: string | null
}

export function login(login_: string, password: string, role?: string) {
  return api.postPublic<LoginResponse>('/login', { login: login_, password, role })
}

// Step 1 of sign-up: no account or token yet — a 6-digit code is emailed instead.
export interface RegisterResponse {
  message: string
  email: string
}

export interface VerifyEmailResponse {
  message: string
  token: string
}

// Step 2 of sign-up: confirms the emailed code, creates the account and returns a token.
export function verifyEmail(email: string, code: string) {
  return api.postPublic<VerifyEmailResponse>('/verify-email', { email, code })
}

export function register(input: {
  email: string
  username: string
  firstName: string
  lastName: string
  password: string
  confirmPassword: string
}) {
  return api.postPublic<RegisterResponse>('/register', {
    email: input.email,
    username: input.username,
    first_name: input.firstName,
    last_name: input.lastName,
    password: input.password,
    confirm_password: input.confirmPassword,
  })
}

export function forgotPassword(email: string) {
  return api.postPublic<{ message: string }>('/forgot-password', { email })
}

export function resetPassword(token: string, password: string, confirmPassword: string) {
  return api.postPublic<{ message: string }>(`/reset-password/${encodeURIComponent(token)}`, {
    password,
    confirm_password: confirmPassword,
  })
}
