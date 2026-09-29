import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { resetPassword } from '../api/auth'
import { ApiError } from '../api/client'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { PASSWORD_MAX } from '../limits'

export default function ResetPasswordPage() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expired, setExpired] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }
    if (!token) return

    setLoading(true)
    try {
      await resetPassword(token, password, confirmPassword)
      navigate('/login', { state: { message: 'Password reset successful. Please log in.' } })
    } catch (err) {
      // A rate limit says nothing about the link, so keep the form and just show the wait.
      if (!(err instanceof ApiError && err.status === 429)) {
        setExpired(true)
      }
      setError(err instanceof ApiError ? err.message : 'This reset link is invalid or has expired.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container container-narrow">
      <div className="header">
        <h1>Reset Password</h1>
        <p>Choose a new password</p>
      </div>
      <div className="form-container">
        {error && <Alert type="error">{error}</Alert>}
        {expired ? (
          <div className="form-footer">
            <Link to="/forgot-password">Request a new reset link</Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="password">New Password</label>
              <input id="password" type="password" maxLength={PASSWORD_MAX} value={password} onChange={(e) => setPassword(e.target.value)} autoFocus />
              <p className="field-hint">8 to {PASSWORD_MAX} characters.</p>
            </div>
            <div className="form-group">
              <label htmlFor="confirmPassword">Confirm New Password</label>
              <input
                id="confirmPassword"
                type="password"
                maxLength={PASSWORD_MAX}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? <Spinner small /> : 'Reset Password'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
