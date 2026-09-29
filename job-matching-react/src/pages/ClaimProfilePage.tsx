import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { claimProfile, getUnclaimedCandidates } from '../api/candidates'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import type { UnclaimedCandidate } from '../types'

export default function ClaimProfilePage() {
  const { user, loginWithToken, setCandidateProfileId } = useAuth()
  const navigate = useNavigate()

  const [candidates, setCandidates] = useState<UnclaimedCandidate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [claimingId, setClaimingId] = useState<string | null>(null)

  useEffect(() => {
    getUnclaimedCandidates()
      .then((res) => setCandidates(res.candidates))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load unclaimed profiles.'))
      .finally(() => setLoading(false))
  }, [])

  if (!user) return <Navigate to="/login" replace />
  if (user.activeRole !== 'User') return <Navigate to="/" replace />
  if (user.candidateProfileId) return <Navigate to="/job-matches" replace />

  const handleClaim = async (candidateId: string) => {
    setError(null)
    setClaimingId(candidateId)
    try {
      const claimed = await claimProfile(candidateId)
      // Prefer the new token (it keeps the profile across a reload); fall back to in-memory only.
      if (claimed.token) loginWithToken(claimed.token)
      else setCandidateProfileId(candidateId)
      navigate('/job-matches')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to claim this profile.')
      setClaimingId(null)
    }
  }

  return (
    <div className="container container-form">
      <div className="header">
        <h1>Claim Your Profile</h1>
        <p>Profiles already in the system under your email address</p>
      </div>
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : candidates.length === 0 ? (
          <div className="empty-state">
            <h3>No profile found for {user.email}</h3>
            <p>
              Only profiles saved with the same email as your account can be claimed. If yours was saved with a
              different email, ask an administrator to link it — or create a new profile.
            </p>
            <Link to="/apply" className="btn btn-primary">
              Create Profile
            </Link>
          </div>
        ) : (
          <div className="stack">
            {candidates.map((candidate) => (
              <div key={candidate.id} className="option-card text-center">
                <h2>{candidate.candidate_name}</h2>
                <p>
                  {candidate.email && <>{candidate.email}</>}
                  {candidate.preferred_job_title && <> · {candidate.preferred_job_title}</>}
                  {candidate.college_degree && <> · {candidate.college_degree}</>}
                </p>
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={claimingId === candidate.id}
                  onClick={() => handleClaim(candidate.id)}
                >
                  {claimingId === candidate.id ? <Spinner small /> : 'This is me'}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
