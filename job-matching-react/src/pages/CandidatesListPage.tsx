import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  deleteCandidate,
  getCandidates,
  sendMatches,
  updateCandidate,
} from '../api/candidates'
import { scrapeJobs } from '../api/jobs'
import { updateUserName } from '../api/users'
import { isSameAccountName, validateAccountName, type AccountNameValue } from '../accountName'
import { AccountNameFields } from '../components/AccountNameFields'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { Modal } from '../components/Modal'
import { CandidateFields, type CandidateFieldsValue } from '../components/CandidateFields'
import { CandidateDetails } from '../components/CandidateDetails'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import { formatDate, toDateInputValue } from '../format'
import { toLocationSponsorshipPayload, toLocationSponsorshipValue } from '../locationSponsorship'
import type { Candidate, ScrapeSummary } from '../types'

const SCRAPE_SITES = ['indeed', 'google']
// Skills shown per row before collapsing the rest into "+N more"; the View dialog lists all.
const VISIBLE_SKILLS = 3

function candidateAccountName(candidate: Candidate): AccountNameValue {
  return { firstName: candidate.account_first_name ?? '', lastName: candidate.account_last_name ?? '' }
}

function candidateToFields(candidate: Candidate): CandidateFieldsValue {
  return {
    candidate_name: candidate.candidate_name,
    email: candidate.email ?? '',
    college_degree: candidate.college_degree ?? '',
    graduation_date: toDateInputValue(candidate.graduation_date),
    preferred_job_title: candidate.preferred_job_title ?? '',
    skills: candidate.skills ?? [],
    ...toLocationSponsorshipValue(candidate),
  }
}

export default function CandidatesListPage() {
  const { user, isAdminOrStaff } = useAuth()
  const navigate = useNavigate()

  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const [sendingId, setSendingId] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const [viewingCandidate, setViewingCandidate] = useState<Candidate | null>(null)
  const [editingCandidate, setEditingCandidate] = useState<Candidate | null>(null)
  const [editForm, setEditForm] = useState<CandidateFieldsValue | null>(null)
  const [editSaving, setEditSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [editAccountName, setEditAccountName] = useState<AccountNameValue>({ firstName: '', lastName: '' })

  const [scrapeModalOpen, setScrapeModalOpen] = useState(false)
  const [scrapeSearchTerm, setScrapeSearchTerm] = useState('software engineer')
  const [scrapeLocation, setScrapeLocation] = useState('United States')
  const [scrapeResultsWanted, setScrapeResultsWanted] = useState(50)
  const [scrapeSites, setScrapeSites] = useState<string[]>(SCRAPE_SITES)
  const [scraping, setScraping] = useState(false)
  const [scrapeError, setScrapeError] = useState<string | null>(null)
  const [scrapeResult, setScrapeResult] = useState<ScrapeSummary | null>(null)

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text })
    setTimeout(() => setToast(null), 4000)
  }

  const loadCandidates = () => {
    setLoading(true)
    setError(null)
    getCandidates()
      .then((res) => setCandidates(res.candidates))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load candidates.'))
      .finally(() => setLoading(false))
  }

  useEffect(loadCandidates, [])

  // Same fields as the Flask page's search: name, degree, preferred title and skills.
  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return candidates
    return candidates.filter((c) =>
      [c.candidate_name, c.college_degree, c.preferred_job_title, ...(c.skills ?? [])].some((field) =>
        field?.toLowerCase().includes(term),
      ),
    )
  }, [candidates, search])

  const handleSendEmail = async (candidate: Candidate) => {
    setSendingId(candidate.id)
    try {
      const res = await sendMatches(candidate.id)
      showToast('success', res.message)
    } catch (err) {
      showToast('error', err instanceof ApiError ? err.message : 'Failed to send match email.')
    } finally {
      setSendingId(null)
    }
  }

  const handleDelete = async (candidate: Candidate) => {
    if (!confirm(`Delete ${candidate.candidate_name}? This cannot be undone.`)) return
    setDeletingId(candidate.id)
    try {
      await deleteCandidate(candidate.id)
      setCandidates((prev) => prev.filter((c) => c.id !== candidate.id))
    } catch (err) {
      showToast('error', err instanceof ApiError ? err.message : 'Failed to delete candidate.')
    } finally {
      setDeletingId(null)
    }
  }

  const openEdit = (candidate: Candidate) => {
    setEditingCandidate(candidate)
    setEditForm(candidateToFields(candidate))
    setEditAccountName(candidateAccountName(candidate))
    setEditError(null)
  }

  const handleEditSave = async () => {
    if (!editingCandidate || !editForm) return
    if (!editForm.candidate_name.trim()) {
      setEditError('Full name is required.')
      return
    }
    const linkedUserId = editingCandidate.linked_user_id
    const nameChanged = !!linkedUserId && !isSameAccountName(editAccountName, candidateAccountName(editingCandidate))
    const nameError = nameChanged ? validateAccountName(editAccountName) : null
    if (nameError) {
      setEditError(nameError)
      return
    }
    setEditSaving(true)
    setEditError(null)
    try {
      await updateCandidate(editingCandidate.id, {
        candidate_name: editForm.candidate_name.trim(),
        email: editForm.email.trim() || null,
        college_degree: editForm.college_degree.trim() || null,
        graduation_date: editForm.graduation_date || null,
        preferred_job_title: editForm.preferred_job_title.trim() || null,
        skills: editForm.skills,
        ...toLocationSponsorshipPayload(editForm),
      })
      const accountName = { firstName: editAccountName.firstName.trim(), lastName: editAccountName.lastName.trim() }
      if (nameChanged && linkedUserId) {
        await updateUserName(linkedUserId, accountName.firstName, accountName.lastName)
      }
      setCandidates((prev) =>
        prev.map((c) =>
          c.id === editingCandidate.id
            ? {
                ...c,
                candidate_name: editForm.candidate_name.trim(),
                email: editForm.email.trim() || null,
                college_degree: editForm.college_degree.trim() || null,
                graduation_date: editForm.graduation_date || null,
                preferred_job_title: editForm.preferred_job_title.trim() || null,
                skills: editForm.skills,
                ...toLocationSponsorshipPayload(editForm),
                ...(nameChanged ? { account_first_name: accountName.firstName, account_last_name: accountName.lastName } : {}),
              }
            : c,
        ),
      )
      setEditingCandidate(null)
      setEditForm(null)
    } catch (err) {
      setEditError(err instanceof ApiError ? err.message : 'Failed to update candidate.')
    } finally {
      setEditSaving(false)
    }
  }

  const toggleScrapeSite = (site: string) => {
    setScrapeSites((prev) => (prev.includes(site) ? prev.filter((s) => s !== site) : [...prev, site]))
  }

  const handleScrapeSubmit = async () => {
    setScraping(true)
    setScrapeError(null)
    setScrapeResult(null)
    try {
      const result = await scrapeJobs({
        site_name: scrapeSites,
        search_term: scrapeSearchTerm,
        location: scrapeLocation,
        results_wanted: scrapeResultsWanted,
      })
      setScrapeResult(result)
    } catch (err) {
      setScrapeError(err instanceof ApiError ? err.message : 'Failed to scrape jobs.')
    } finally {
      setScraping(false)
    }
  }

  return (
    <div className="container">
      <AppHeader title="Candidates" subtitle="Browse and manage candidate profiles" navItems={getNavItems(user)} />
      <div className="content">
        {toast && <Alert type={toast.type}>{toast.text}</Alert>}
        {error && <Alert type="error">{error}</Alert>}

        <div className="toolbar">
          <div className="toolbar-left">
            <input
              type="text"
              className="search-input"
              placeholder="Search by name, degree, title or skill…"
              aria-label="Search candidates"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <button type="button" className="btn btn-secondary btn-sm" onClick={loadCandidates}>
              Refresh
            </button>
          </div>
          <div className="toolbar-right">
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={() => {
                setScrapeResult(null)
                setScrapeError(null)
                setScrapeModalOpen(true)
              }}
            >
              Scrape Jobs
            </button>
            <Link to="/apply" className="btn btn-primary btn-sm">
              Add Candidate
            </Link>
          </div>
        </div>

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <h3>No candidates found</h3>
            <p>{search.trim() ? `Nothing matches “${search.trim()}”.` : 'Add the first one.'}</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="table-stack candidates-table">
              <thead>
                <tr>
                  <th>Candidate</th>
                  <th>Education</th>
                  <th>Preferred Title</th>
                  <th>Skills</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((candidate) => (
                  <tr key={candidate.id}>
                    <td data-label="Candidate" className="cell-candidate">
                      <div className="cell-primary">{candidate.candidate_name}</div>
                      {candidate.email && (
                        <span className="cell-truncate cell-secondary" title={candidate.email}>
                          {candidate.email}
                        </span>
                      )}
                    </td>
                    <td data-label="Education">
                      <div>{candidate.college_degree || '—'}</div>
                      {candidate.graduation_date && (
                        <div className="cell-secondary">Graduated {formatDate(candidate.graduation_date)}</div>
                      )}
                    </td>
                    <td data-label="Preferred Title">{candidate.preferred_job_title || '—'}</td>
                    <td data-label="Skills" className="cell-skills">
                      {candidate.skills && candidate.skills.length > 0 ? (
                        <div className="pill-list">
                          {candidate.skills.slice(0, VISIBLE_SKILLS).map((skill) => (
                            <span key={skill} className="pill">
                              {skill}
                            </span>
                          ))}
                          {candidate.skills.length > VISIBLE_SKILLS && (
                            <span className="pill pill-more" title={candidate.skills.slice(VISIBLE_SKILLS).join(', ')}>
                              +{candidate.skills.length - VISIBLE_SKILLS} more
                            </span>
                          )}
                        </div>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td data-label="Actions" className="cell-actions">
                      <div className="row-actions">
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          aria-label={`View ${candidate.candidate_name}`}
                          onClick={() => setViewingCandidate(candidate)}
                        >
                          View
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => navigate(`/job-matches?candidate_id=${candidate.id}`)}
                        >
                          Matches
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          onClick={() => navigate(`/applied-jobs?candidate_id=${candidate.id}`)}
                        >
                          Applied Jobs
                        </button>
                        <button
                          type="button"
                          className="btn btn-secondary btn-sm"
                          disabled={sendingId === candidate.id}
                          onClick={() => handleSendEmail(candidate)}
                        >
                          {sendingId === candidate.id ? <Spinner small /> : 'Send Email'}
                        </button>
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => openEdit(candidate)}>
                          Edit
                        </button>
                        {isAdminOrStaff && (
                          <button
                            type="button"
                            className="btn btn-danger btn-sm"
                            disabled={deletingId === candidate.id}
                            onClick={() => handleDelete(candidate)}
                          >
                            {deletingId === candidate.id ? <Spinner small /> : 'Delete'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {viewingCandidate && (
        <Modal title="Candidate Details" onClose={() => setViewingCandidate(null)}>
          <CandidateDetails candidate={viewingCandidate} />
        </Modal>
      )}

      {editingCandidate && editForm && (
        <Modal
          title="Edit Candidate"
          onClose={() => {
            setEditingCandidate(null)
            setEditForm(null)
          }}
        >
          {editError && <Alert type="error">{editError}</Alert>}
          {editingCandidate.linked_user_id && (
            <>
              <h3 className="form-section-title">Linked Account Name</h3>
              <AccountNameFields value={editAccountName} onChange={setEditAccountName} idPrefix="edit-account" />
              <h3 className="form-section-title">Candidate Profile</h3>
            </>
          )}
          <CandidateFields value={editForm} onChange={setEditForm} idPrefix="edit" />
          <div className="form-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setEditingCandidate(null)
                setEditForm(null)
              }}
            >
              Cancel
            </button>
            <button type="button" className="btn btn-primary" disabled={editSaving} onClick={handleEditSave} style={{ flex: 1 }}>
              {editSaving ? <Spinner small /> : 'Save Changes'}
            </button>
          </div>
        </Modal>
      )}

      {scrapeModalOpen && (
        <Modal title="Scrape Jobs" onClose={() => setScrapeModalOpen(false)}>
          {scrapeError && <Alert type="error">{scrapeError}</Alert>}
          {scrapeResult && (
            <Alert type="success">
              Scraped {scrapeResult.jobs_scraped} jobs, saved {scrapeResult.jobs_saved}, generated{' '}
              {scrapeResult.embeddings_generated} embeddings.
            </Alert>
          )}
          <div className="form-group">
            <label htmlFor="scrape-term">Search Term</label>
            <input id="scrape-term" type="text" value={scrapeSearchTerm} onChange={(e) => setScrapeSearchTerm(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="scrape-location">Location</label>
            <input id="scrape-location" type="text" value={scrapeLocation} onChange={(e) => setScrapeLocation(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="scrape-results">Results Wanted</label>
            <input
              id="scrape-results"
              type="number"
              min={1}
              value={scrapeResultsWanted}
              onChange={(e) => setScrapeResultsWanted(Number(e.target.value) || 1)}
            />
          </div>
          <div className="form-group">
            <label>Sites</label>
            {SCRAPE_SITES.map((site) => (
              <div key={site} className="checkbox-row">
                <input
                  id={`site-${site}`}
                  type="checkbox"
                  checked={scrapeSites.includes(site)}
                  onChange={() => toggleScrapeSite(site)}
                />
                <label htmlFor={`site-${site}`} style={{ margin: 0, textTransform: 'capitalize' }}>
                  {site}
                </label>
              </div>
            ))}
          </div>
          <button type="button" className="btn btn-primary btn-block" disabled={scraping} onClick={handleScrapeSubmit}>
            {scraping ? <Spinner small /> : 'Start Scrape'}
          </button>
        </Modal>
      )}
    </div>
  )
}
