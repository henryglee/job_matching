import type { AuthUser, RoleName } from './types'

// Mirrors app.py's post-login routing (see API_DOCUMENTATION.md's POST /login notes),
// re-implemented client-side since the Spring backend just returns a token. Like app.py,
// anyone who isn't Admin/Staff (including an account with no role) is routed as a User,
// never to the Admin/Staff home page.
export function postLoginPath(user: AuthUser): string {
  if (user.activeRole === 'Admin' || user.activeRole === 'Staff') {
    return '/'
  }
  return user.candidateProfileId ? '/job-matches' : '/profile-setup'
}

// Which roles may open each protected page. App.tsx's ProtectedRoutes read their roles from
// here too, so the login redirect below can't drift from what the pages actually allow.
// Protected pages not listed here are open to any logged-in user.
export const ROUTE_ROLES: Partial<Record<string, RoleName[]>> = {
  '/candidates': ['Admin', 'Staff'],
  '/edit-profile': ['User'],
  '/email-drafts': ['User'],
  // Admin/Staff get a view-only version, for any candidate (?candidate_id=).
  '/applied-jobs': ['User', 'Admin', 'Staff'],
  '/profile-setup': ['User'],
  '/claim-profile': ['User'],
  '/admin': ['Admin'],
  '/admin/roles': ['Admin'],
  '/admin/ai-usage': ['Admin'],
}

// Pages a User without a linked candidate profile can use. Anywhere else, onboarding comes
// first, as it does after a normal login.
const PROFILE_SETUP_PAGES = ['/profile-setup', '/claim-profile', '/apply']

export interface ReturnLocation {
  pathname: string
  search?: string
  hash?: string
}

// Where to go after logging in: back to the page the user was trying to open when
// ProtectedRoute sent them to /login (location.state.from), query string included, e.g. a
// review link from their AI assistant (/email-drafts?highlight=<id>) or an Admin's
// /job-matches?candidate_id=<id>. Falls back to postLoginPath when there's nowhere to resume,
// when the role they logged in with can't open that page (rather than showing a 403), or when
// a User still has to set up their profile.
export function loginReturnPath(user: AuthUser, from: ReturnLocation | undefined): string {
  if (!from || !from.pathname.startsWith('/') || from.pathname.startsWith('//') || from.pathname === '/login') {
    return postLoginPath(user)
  }
  const allowedRoles = ROUTE_ROLES[from.pathname]
  if (allowedRoles && !allowedRoles.includes(user.activeRole)) {
    return postLoginPath(user)
  }
  const isAdminOrStaff = user.activeRole === 'Admin' || user.activeRole === 'Staff'
  if (!isAdminOrStaff && !user.candidateProfileId && !PROFILE_SETUP_PAGES.includes(from.pathname)) {
    return postLoginPath(user)
  }
  return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
}
