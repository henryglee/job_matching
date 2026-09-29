import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { createCandidate } from '../api/candidates'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { SkillPicker } from '../components/SkillPicker'
import { LocationSponsorshipFields } from '../components/LocationSponsorshipFields'
import { EMPTY_LOCATION_SPONSORSHIP, toLocationSponsorshipPayload, type LocationSponsorshipValue } from '../locationSponsorship'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import type { NavItem } from '../components/NavBar'
import { DEGREE_MAX, EMAIL_MAX, JOB_TITLE_MAX, NAME_MAX } from '../limits'
import { LimitHint } from '../components/LimitHint'

const MAX_RESUME_BYTES = 16 * 1024 * 1024

export default function CandidateFormPage() {
  const { user, isUser, hasProfile, loginWithToken, setCandidateProfileId } = useAuth()
  const navigate = useNavigate()

  const [candidateName, setCandidateName] = useState('')
  // A User's profile email is their account email and isn't editable; Admin/Staff enter the candidate's.
  const [email, setEmail] = useState(isUser ? (user?.email ?? '') : '')
  const [collegeDegree, setCollegeDegree] = useState('')
  const [graduationDate, setGraduationDate] = useState('')
  const [preferredJobTitle, setPreferredJobTitle] = useState('')
  const [skills, setSkills] = useState<string[]>([])
  const [locationSponsorship, setLocationSponsorship] = useState<LocationSponsorshipValue>(EMPTY_LOCATION_SPONSORSHIP)
  const [resume, setResume] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (!success || !user || isUser) return
    const timer = setTimeout(() => navigate('/candidates'), 2000)
    return () => clearTimeout(timer)
  }, [success, user, isUser, navigate])

  if (!user) return <Navigate to="/login" replace />
  if (isUser && hasProfile) return <Navigate to="/edit-profile" replace />

  const navItems: NavItem[] = isUser && !hasProfile ? [{ to: '/profile-setup', label: '← Back' }, ...getNavItems(user)] : getNavItems(user)

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] ?? null
    setFileError(null)
    if (!file) {
      setResume(null)
      return
    }
    if (file.type !== 'application/pdf') {
      setFileError('Please upload a PDF file.')
      setResume(null)
      e.target.value = ''
      return
    }
    if (file.size > MAX_RESUME_BYTES) {
      setFileError('File must be 16MB or smaller.')
      setResume(null)
      e.target.value = ''
      return
    }
    setResume(file)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!candidateName.trim()) {
      setError('Full name is required.')
      return
    }

    setLoading(true)
    try {
      const created = await createCandidate({
        candidate_name: candidateName.trim(),
        email: email.trim() || undefined,
        college_degree: collegeDegree.trim() || undefined,
        graduation_date: graduationDate || undefined,
        preferred_job_title: preferredJobTitle.trim() || undefined,
        skills,
        ...toLocationSponsorshipPayload(locationSponsorship),
        resume,
      })

      if (isUser) {
        // Make the client see the new profile now and after a reload: the profile id lives in
        // the login token, which was issued before the profile existed.
        if (created.token) loginWithToken(created.token)
        else setCandidateProfileId(created.candidate_id)
        navigate('/job-matches')
        return
      }

      setSuccess(true)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to save candidate. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container container-form">
      <AppHeader title="Add Candidate" subtitle="Submit a new candidate profile" navItems={navItems} />
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}
        {success && <Alert type="success">Candidate information saved successfully. Redirecting…</Alert>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="candidate_name">Full Name *</label>
            <input id="candidate_name" type="text" maxLength={NAME_MAX} value={candidateName} onChange={(e) => setCandidateName(e.target.value)} />
            <LimitHint value={candidateName} max={NAME_MAX} />
          </div>
          <div className="form-group">
            <label htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              maxLength={EMAIL_MAX}
              value={email}
              readOnly={isUser}
              aria-describedby={isUser ? 'email-note' : undefined}
              onChange={(e) => setEmail(e.target.value)}
            />
            {isUser && (
              <p id="email-note" className="field-hint">
                This is the email on your account, so it can't be changed here.
              </p>
            )}
          </div>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="college_degree">College Degree</label>
              <input id="college_degree" type="text" maxLength={DEGREE_MAX} value={collegeDegree} onChange={(e) => setCollegeDegree(e.target.value)} />
            <LimitHint value={collegeDegree} max={DEGREE_MAX} />
            </div>
            <div className="form-group">
              <label htmlFor="graduation_date">Graduation Date</label>
              <input
                id="graduation_date"
                type="date"
                value={graduationDate}
                onChange={(e) => setGraduationDate(e.target.value)}
              />
            </div>
          </div>
          <div className="form-group">
            <label htmlFor="preferred_job_title">Preferred Job Title</label>
            <input
              id="preferred_job_title"
              maxLength={JOB_TITLE_MAX}
              type="text"
              value={preferredJobTitle}
              onChange={(e) => setPreferredJobTitle(e.target.value)}
            />
            <LimitHint value={preferredJobTitle} max={JOB_TITLE_MAX} />
          </div>
          <LocationSponsorshipFields value={locationSponsorship} onChange={setLocationSponsorship} idPrefix="apply" />
          <div className="form-group">
            <label>Skills</label>
            <SkillPicker value={skills} onChange={setSkills} />
          </div>
          <div className="form-group">
            <label>Resume (PDF, max 16MB)</label>
            <div className="upload-zone">
              {resume ? 'Click to change file' : 'Click to upload or drag a PDF here'}
              <input type="file" accept="application/pdf" onChange={handleFileChange} />
            </div>
            {fileError && <div className="field-error">{fileError}</div>}
            {resume && (
              <div className="upload-info">
                {resume.name} ({(resume.size / (1024 * 1024)).toFixed(2)} MB)
              </div>
            )}
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? <Spinner small /> : 'Submit'}
          </button>
        </form>

        {isUser && !hasProfile && (
          <div className="form-footer">
            <Link to="/claim-profile">Already in the system? Claim your profile instead.</Link>
          </div>
        )}
      </div>
    </div>
  )
}
