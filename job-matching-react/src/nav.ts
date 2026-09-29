import type { NavItem } from './components/NavBar'
import type { AuthUser } from './types'

// Role-conditional nav links shared by the authenticated pages, per REACT_IMPLEMENTATION.md's
// per-page "Nav" notes. Individual pages may prepend/append page-specific items (e.g. the
// CandidateFormPage "← Back" link for a User with no profile yet).
export function getNavItems(user: AuthUser | null): NavItem[] {
  if (!user) return []

  if (user.activeRole === 'Admin' || user.activeRole === 'Staff') {
    const items: NavItem[] = [
      { to: '/', label: 'Home' },
      { to: '/apply', label: 'Add Candidate' },
      { to: '/candidates', label: 'View Candidates' },
      { to: '/job-matches', label: 'Job Matches' },
      { to: '/applied-jobs', label: 'Applied Jobs' },
    ]
    if (user.activeRole === 'Admin') {
      items.push(
        { to: '/admin', label: 'Admin' },
        { to: '/admin/roles', label: 'Roles' },
        { to: '/admin/ai-usage', label: 'AI Usage' },
      )
    }
    return items
  }

  // User role
  if (user.candidateProfileId) {
    return [
      { to: '/edit-profile', label: 'Edit Profile' },
      { to: '/job-matches', label: 'Job Matches' },
      { to: '/applied-jobs', label: 'Applied Jobs' },
      { to: '/email-drafts', label: 'Email Drafts' },
    ]
  }
  return []
}
