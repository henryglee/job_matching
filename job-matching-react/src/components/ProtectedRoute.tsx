import type { ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { RoleName } from '../types'

interface ProtectedRouteProps {
  roles?: RoleName[]
  children: ReactNode
}

export function ProtectedRoute({ roles, children }: ProtectedRouteProps) {
  const { user, loggedOut, sessionExpired } = useAuth()
  const location = useLocation()

  if (!user) {
    // Remember the page so login can return to it (see loginReturnPath), unless the user just
    // clicked Logout: logout() clears the user a moment before UserBadge navigates to /login,
    // so this page re-renders logged out first.
    const notice = sessionExpired ? 'Your session expired. Log in again to pick up where you left off.' : undefined
    return <Navigate to="/login" replace state={loggedOut ? undefined : { from: location, notice }} />
  }

  if (roles && !roles.includes(user.activeRole)) {
    return (
      <div className="container container-form">
        <div className="header">
          <h1>403</h1>
          <p>You don't have access to this page.</p>
        </div>
        <div className="content text-center">
          <Link className="btn btn-primary" to="/">
            Go home
          </Link>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
