import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { jwtDecode } from 'jwt-decode'
import { clearToken, getToken, setToken as persistToken, setUnauthorizedHandler, TOKEN_KEY } from '../api/client'
import { clearBackups } from '../formBackup'
import type { AuthUser, JwtPayload } from '../types'

function decodeUser(token: string): AuthUser | null {
  try {
    const payload = jwtDecode<JwtPayload>(token)
    if (payload.exp * 1000 < Date.now()) {
      return null
    }
    return {
      id: payload.sub,
      email: payload.email,
      username: payload.username,
      activeRole: payload.activeRole,
      candidateProfileId: payload.candidateProfileId ?? null,
    }
  } catch {
    return null
  }
}

interface AuthContextValue {
  user: AuthUser | null
  isAdminOrStaff: boolean
  isUser: boolean
  isAdmin: boolean
  hasProfile: boolean
  // True after logout() until the next login. ProtectedRoute uses it so a deliberate logout
  // doesn't send the next person to log in on this browser back to the page it happened on.
  loggedOut: boolean
  // True when the session ended because the token expired or was rejected (a stored token
  // that had expired by page load, or any API call answering 401), until the next login.
  // ProtectedRoute then tells the login page to say so.
  sessionExpired: boolean
  loginWithToken: (token: string) => AuthUser | null
  logout: () => void
  setCandidateProfileId: (candidateProfileId: string) => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  // A stored token that has expired since the last visit counts as an expired session, so
  // the login page can say why the user is there. The dead token is cleared either way.
  const [initial] = useState(() => {
    const token = getToken()
    const decoded = token ? decodeUser(token) : null
    if (token && !decoded) clearToken()
    return { user: decoded, expired: !!token && !decoded }
  })
  const [user, setUser] = useState<AuthUser | null>(initial.user)
  const [loggedOut, setLoggedOut] = useState(false)
  const [sessionExpired, setSessionExpired] = useState(initial.expired)

  const loginWithToken = useCallback((token: string) => {
    persistToken(token)
    const decoded = decodeUser(token)
    // Unsaved form edits from a different account that used this tab aren't theirs to see.
    if (decoded) clearBackups(decoded.id)
    setUser(decoded)
    setLoggedOut(false)
    setSessionExpired(false)
    return decoded
  }, [])

  const logout = useCallback(() => {
    clearToken()
    // Deliberate logout: drop unsaved form edits too. expireSession() keeps them to restore.
    clearBackups()
    setLoggedOut(true)
    setSessionExpired(false)
    setUser(null)
  }, [])

  // Unlike logout(), not deliberate: the page they were on is kept so they return to it.
  const expireSession = useCallback(() => {
    clearToken()
    setLoggedOut(false)
    setSessionExpired(true)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(expireSession)
    return () => setUnauthorizedHandler(null)
  }, [expireSession])

  // The token is shared by every tab (localStorage) and every request reads it fresh, so a login,
  // logout or account switch in another tab changes who this tab's requests act as. Follow it,
  // or this tab would keep showing the old user (and their pages) while acting as someone else.
  // The storage event only fires in the other tabs, never the one that made the change.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && e.key !== TOKEN_KEY) return // null: storage was cleared
      const token = getToken()
      const decoded = token ? decodeUser(token) : null
      if (decoded) {
        setUser(decoded)
        setLoggedOut(false)
        setSessionExpired(false)
      } else {
        setUser(null)
        setLoggedOut(true) // like a deliberate logout: the next login shouldn't resume this page
        setSessionExpired(false)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const setCandidateProfileId = useCallback((candidateProfileId: string) => {
    setUser((prev) => (prev ? { ...prev, candidateProfileId } : prev))
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAdminOrStaff: user?.activeRole === 'Admin' || user?.activeRole === 'Staff',
      isUser: user?.activeRole === 'User',
      isAdmin: user?.activeRole === 'Admin',
      hasProfile: !!user?.candidateProfileId,
      loggedOut,
      sessionExpired,
      loginWithToken,
      logout,
      setCandidateProfileId,
    }),
    [user, loggedOut, sessionExpired, loginWithToken, logout, setCandidateProfileId],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
