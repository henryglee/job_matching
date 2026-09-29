import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register, verifyEmail } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { PASSWORD_MAX } from '../limits'

export default function RegisterPage() {
  const navigate = useNavigate()
  const { loginWithToken } = useAuth()

  const [email, setEmail] = useState('')
  const [username, setUsername] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Set once /register has emailed a code; switches the page to the verification step.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [info, setInfo] = useState<string | null>(null)

  const sendCode = async () => {
    const response = await register({ email, username, firstName, lastName, password, confirmPassword })
    setPendingEmail(response.email)
    setCode('')
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!email || !username || !firstName || !lastName || !password || !confirmPassword) {
      setError('Please fill in all fields.')
      return
    }
    if (username.includes('@')) {
      setError('Username cannot contain @.')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (password.includes(' ')) {
      setError('Password cannot contain spaces.')
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    setLoading(true)
    try {
      await sendCode()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleVerify = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setInfo(null)
    if (!pendingEmail) return

    if (!/^\d{6}$/.test(code)) {
      setError('Please enter the 6-digit code.')
      return
    }

    setLoading(true)
    try {
      const response = await verifyEmail(pendingEmail, code)
      loginWithToken(response.token)
      navigate('/profile-setup')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Verification failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  // Re-running /register replaces the pending sign-up with a fresh code (and resets the
  // attempt limit), which is also the way back after an expired or exhausted code.
  const handleResend = async () => {
    setError(null)
    setInfo(null)
    setLoading(true)
    try {
      await sendCode()
      setInfo('A new code has been sent.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resend the code. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleStartOver = () => {
    setPendingEmail(null)
    setCode('')
    setError(null)
    setInfo(null)
  }

  if (pendingEmail) {
    return (
      <div className="container container-narrow">
        <div className="header">
          <h1>Verify Your Email</h1>
          <p>One more step to create your account</p>
        </div>
        <div className="form-container">
          {error && <Alert type="error">{error}</Alert>}
          {info && <Alert type="success">{info}</Alert>}
          <Alert type="info">
            A 6-digit verification code has been sent to <strong>{pendingEmail}</strong>. Enter it below to activate
            your account. The code expires in 10 minutes.
          </Alert>
          <form onSubmit={handleVerify}>
            <div className="form-group">
              <label htmlFor="code">Verification Code</label>
              <input
                id="code"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                placeholder="123456"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                autoFocus
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? <Spinner small /> : 'Verify Account'}
            </button>
          </form>
          <div className="form-footer">
            Didn&apos;t get a code?{' '}
            <button type="button" className="btn-link" onClick={handleResend} disabled={loading}>
              Resend
            </button>
            {' · '}
            Wrong email?{' '}
            <button type="button" className="btn-link" onClick={handleStartOver} disabled={loading}>
              Start over
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="container container-narrow">
      <div className="header">
        <h1>Create Account</h1>
        <p>Join the Job Matching platform</p>
      </div>
      <div className="form-container">
        {error && <Alert type="error">{error}</Alert>}
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="firstName">First Name</label>
              <input id="firstName" type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </div>
            <div className="form-group">
              <label htmlFor="lastName">Last Name</label>
              <input id="lastName" type="text" value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </div>
          </div>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input id="username" type="text" value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" maxLength={PASSWORD_MAX} value={password} onChange={(e) => setPassword(e.target.value)} />
            <p className="field-hint">8 to {PASSWORD_MAX} characters, no spaces.</p>
          </div>
          <div className="form-group">
            <label htmlFor="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              maxLength={PASSWORD_MAX}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
            />
          </div>
          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? <Spinner small /> : 'Create Account'}
          </button>
        </form>
        <div className="form-footer">
          Already have an account? <Link to="/login">Log in</Link>
        </div>
      </div>
    </div>
  )
}
