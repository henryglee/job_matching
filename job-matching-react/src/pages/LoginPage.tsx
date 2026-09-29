import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { checkRoles, login as loginRequest } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import type { Role } from '../types'
import { loginReturnPath, type ReturnLocation } from '../routing'
import { LOGIN_PASSWORD_MAX } from '../limits'

export default function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { loginWithToken } = useAuth()
  const locationState = location.state as { message?: string; notice?: string; from?: ReturnLocation } | null
  const successMessage = locationState?.message
  // Neutral information, e.g. that their session expired (set by ProtectedRoute).
  const notice = locationState?.notice

  const [step, setStep] = useState<1 | 2>(1)
  const [loginField, setLoginField] = useState('')
  const [password, setPassword] = useState('')
  const [roles, setRoles] = useState<Role[]>([])
  const [selectedRole, setSelectedRole] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const completeLogin = async (role?: string) => {
    setLoading(true)
    setError(null)
    try {
      // Trimmed like the role check above, so a pasted "me@example.com " that passed that step
      // isn't then rejected as invalid credentials.
      const response = await loginRequest(loginField.trim(), password, role)
      const user = loginWithToken(response.token)
      navigate(user ? loginReturnPath(user, locationState?.from) : '/login')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
      setLoading(false)
    }
  }

  const checkRolesStep = async () => {
    // Already signing in: a second Enter or click would send another attempt, and each one counts
    // towards the server's login rate limit.
    if (loading) return
    if (!loginField.trim() || !password) {
      setError('Please enter your email/username and password.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const response = await checkRoles(loginField.trim(), password)
      if (response.roles.length <= 1) {
        const role = response.roles[0]?.role_name
        await completeLogin(role)
        return
      }
      setRoles(response.roles)
      setSelectedRole(response.roles[0].role_name)
      setStep(2)
      setLoading(false)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 401
          ? 'Invalid credentials.'
          : err instanceof ApiError && err.status === 429
            ? err.message
            : 'Something went wrong. Please try again.',
      )
      setLoading(false)
    }
  }

  const handleStep1Submit = (e: FormEvent) => {
    e.preventDefault()
    void checkRolesStep()
  }

  const handlePasswordKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      void checkRolesStep()
    }
  }

  const handleStep2Submit = (e: FormEvent) => {
    e.preventDefault()
    if (loading) return
    void completeLogin(selectedRole)
  }

  return (
    <div className="container container-narrow">
      <div className="header">
        <h1>Job Matching</h1>
        <p>Sign in to continue</p>
      </div>
      <div className="form-container">
        {error && <Alert type="error">{error}</Alert>}
        {!error && successMessage && <Alert type="success">{successMessage}</Alert>}
        {!error && notice && <Alert type="info">{notice}</Alert>}

        {step === 1 && (
          <form onSubmit={handleStep1Submit}>
            <div className="form-group">
              <label htmlFor="login">Email or Username</label>
              <input
                id="login"
                type="text"
                value={loginField}
                onChange={(e) => setLoginField(e.target.value)}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                maxLength={LOGIN_PASSWORD_MAX}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={handlePasswordKeyDown}
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? <Spinner small /> : 'Continue'}
            </button>
          </form>
        )}

        {step === 2 && (
          <form onSubmit={handleStep2Submit} style={{ animation: 'fadeIn 0.15s ease' }}>
            <div className="form-group">
              <label htmlFor="role">Select Role</label>
              <select id="role" value={selectedRole} onChange={(e) => setSelectedRole(e.target.value)}>
                {roles.map((role) => (
                  <option key={role.role_id} value={role.role_name}>
                    {role.role_name}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-actions">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => {
                  setStep(1)
                  setError(null)
                }}
              >
                Back
              </button>
              <button type="submit" className="btn btn-primary" disabled={loading} style={{ flex: 1 }}>
                {loading ? <Spinner small /> : 'Log In'}
              </button>
            </div>
          </form>
        )}

        <div className="form-footer">
          <Link to="/forgot-password">Forgot password?</Link> &nbsp;·&nbsp; <Link to="/register">Create an account</Link>
        </div>
      </div>
    </div>
  )
}
