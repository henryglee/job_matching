import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { forgotPassword } from '../api/auth'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await forgotPassword(email)
      setSubmitted(true)
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        // The limit is per address typed in, whether or not it's registered, so saying so leaks nothing.
        setError(err.message)
      } else {
        // Any other outcome shows the same message, to avoid email enumeration.
        setSubmitted(true)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container container-narrow">
      <div className="header">
        <h1>Forgot Password</h1>
        <p>We'll email you a reset link</p>
      </div>
      <div className="form-container">
        {submitted ? (
          <Alert type="info">If that email is registered, a reset link has been sent.</Alert>
        ) : (
          <form onSubmit={handleSubmit}>
            {error && <Alert type="error">{error}</Alert>}
            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? <Spinner small /> : 'Send Reset Link'}
            </button>
          </form>
        )}
        <div className="form-footer">
          <Link to="/login">Back to login</Link>
        </div>
      </div>
    </div>
  )
}
