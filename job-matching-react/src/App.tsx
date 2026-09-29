import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import { postLoginPath, ROUTE_ROLES } from './routing'
import { ProtectedRoute } from './components/ProtectedRoute'
import { PublicOnlyRoute } from './components/PublicOnlyRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ProfileSetupPage from './pages/ProfileSetupPage'
import ClaimProfilePage from './pages/ClaimProfilePage'
import HomeDashboard from './pages/HomeDashboard'
import CandidateFormPage from './pages/CandidateFormPage'
import CandidatesListPage from './pages/CandidatesListPage'
import EditProfilePage from './pages/EditProfilePage'
import JobMatchesPage from './pages/JobMatchesPage'
import EmailDraftsPage from './pages/EmailDraftsPage'
import AppliedJobsPage from './pages/AppliedJobsPage'
import AdminUsersPage from './pages/AdminUsersPage'
import AdminRolesPage from './pages/AdminRolesPage'
import AdminAiUsagePage from './pages/AdminAiUsagePage'

// "/" is the Admin/Staff dashboard. Like app.py's index route, anyone else is sent on to
// their job matches (or profile setup) instead of a 403 page. That also covers the 403
// page's "Go home" link and the catch-all redirect below, which both lead here.
function HomeRoute() {
  const { user, isAdminOrStaff } = useAuth()
  if (user && !isAdminOrStaff) {
    return <Navigate to={postLoginPath(user)} replace />
  }
  return <HomeDashboard />
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <PublicOnlyRoute>
            <LoginPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/register"
        element={
          <PublicOnlyRoute>
            <RegisterPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/forgot-password"
        element={
          <PublicOnlyRoute>
            <ForgotPasswordPage />
          </PublicOnlyRoute>
        }
      />
      <Route path="/reset-password/:token" element={<ResetPasswordPage />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <HomeRoute />
          </ProtectedRoute>
        }
      />
      <Route
        path="/apply"
        element={
          <ProtectedRoute>
            <CandidateFormPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/candidates"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/candidates']}>
            <CandidatesListPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/edit-profile"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/edit-profile']}>
            <EditProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/job-matches"
        element={
          <ProtectedRoute>
            <JobMatchesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/email-drafts"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/email-drafts']}>
            <EmailDraftsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/applied-jobs"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/applied-jobs']}>
            <AppliedJobsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile-setup"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/profile-setup']}>
            <ProfileSetupPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/claim-profile"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/claim-profile']}>
            <ClaimProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/admin']}>
            <AdminUsersPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/roles"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/admin/roles']}>
            <AdminRolesPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin/ai-usage"
        element={
          <ProtectedRoute roles={ROUTE_ROLES['/admin/ai-usage']}>
            <AdminAiUsagePage />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
