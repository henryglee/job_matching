import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function ProfileSetupPage() {
  const { user } = useAuth()

  if (!user) return <Navigate to="/login" replace />
  if (user.activeRole !== 'User') return <Navigate to="/" replace />
  if (user.candidateProfileId) return <Navigate to="/job-matches" replace />

  return (
    <div className="container container-form">
      <div className="header">
        <h1>Set Up Your Profile</h1>
        <p>Choose how you'd like to get started</p>
      </div>
      <div className="content">
        <div className="option-card">
          <div className="option-icon">📝</div>
          <h2>Create Your Profile</h2>
          <p>Fill out a new candidate profile from scratch.</p>
          <Link to="/apply" className="btn btn-primary">
            Get Started
          </Link>
        </div>
        <div className="option-card">
          <div className="option-icon">🔗</div>
          <h2>I'm Already in the System</h2>
          <p>An admin may have already entered your information.</p>
          <Link to="/claim-profile" className="btn btn-secondary">
            Find My Profile
          </Link>
        </div>
      </div>
    </div>
  )
}
