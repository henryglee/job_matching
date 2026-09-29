// In dev, requests stay relative and go through the Vite proxy (vite.config.ts) — no env
// var needed. A production build has no dev proxy, so it needs an absolute backend origin;
// set VITE_API_BASE_URL at build time and make sure that origin is in the backend's
// app.cors.allowed-origins / CORS_ALLOWED_ORIGINS (see SecurityConfig.corsConfigurationSource).
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export const TOKEN_KEY = 'jobmatching.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

// Absolute URL of a backend path, for handing to something outside this app (e.g. an MCP
// client). In dev that's the Vite origin, which proxies to the backend.
export function backendUrl(path: string): string {
  return new URL(`${API_BASE_URL}${path}`, window.location.origin).href
}

// Called when an authenticated request comes back 401, i.e. the token expired or is no longer
// valid. AuthContext registers a handler that ends the session, so ProtectedRoute sends the
// user to /login and they're brought back to the same page afterwards.
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  isFormData?: boolean
  auth?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, isFormData = false, auth = true } = options

  const headers: Record<string, string> = {}
  if (!isFormData && body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = auth ? getToken() : null
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : isFormData ? (body as FormData) : JSON.stringify(body),
  })

  const contentType = response.headers.get('content-type') ?? ''
  const data = contentType.includes('application/json') ? await response.json() : undefined

  // Only if the token that failed is still the current one: a slow request from an old session
  // mustn't log out a user who has since logged in again.
  if (response.status === 401 && token && getToken() === token) {
    unauthorizedHandler?.()
  }

  if (!response.ok) {
    const message = (data && (data as { error?: string }).error) || response.statusText || 'Request failed'
    throw new ApiError(response.status, message)
  }

  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  postForm: <T>(path: string, formData: FormData) =>
    request<T>(path, { method: 'POST', body: formData, isFormData: true }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  // Public (unauthenticated) requests — auth endpoints must not send a stale Authorization header.
  postPublic: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body, auth: false }),
}
