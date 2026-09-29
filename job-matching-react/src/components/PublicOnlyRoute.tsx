import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { loginReturnPath, type ReturnLocation } from '../routing'

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user) {
    // This re-renders the moment LoginPage logs the user in, so it has to pick the same
    // destination LoginPage would: the page they were sent to /login from, if they can open it.
    const from = (location.state as { from?: ReturnLocation } | null)?.from
    return <Navigate to={loginReturnPath(user, from)} replace />
  }
  return <>{children}</>
}
