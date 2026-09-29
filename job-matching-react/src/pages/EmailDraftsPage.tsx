import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { listDrafts } from '../api/drafts'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { EmailDraftCard } from '../components/EmailDraftCard'
import { Spinner } from '../components/Spinner'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import type { EmailDraft } from '../types'

// Application email drafts, from "Generate draft" on a job match (backend EmailDraftGenerator)
// or from the candidate's own AI assistant over MCP (mcp/EmailDraftTools). The candidate reviews
// and edits them here and sends them from their own mail app, so replies come back to them. The
// app never sends these itself.
export default function EmailDraftsPage() {
  const { user } = useAuth()
  // The draft to point the candidate at: one just made by "Generate draft" on the job matches
  // page (router state), or one their AI assistant saved, from the review link the MCP
  // save/update tools return (?highlight=<id>).
  const generatedDraftId = (useLocation().state as { generatedDraftId?: string } | null)?.generatedDraftId
  const [searchParams] = useSearchParams()
  const highlightId = generatedDraftId ?? searchParams.get('highlight')
  const [drafts, setDrafts] = useState<EmailDraft[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!user?.candidateProfileId) return
    listDrafts()
      .then((res) => setDrafts(res.drafts))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load your drafts.'))
      .finally(() => setLoading(false))
  }, [user?.candidateProfileId])

  useEffect(() => {
    if (!loading && highlightId) {
      document.getElementById(`draft-card-${highlightId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }, [loading, highlightId])

  if (!user) return <Navigate to="/login" replace />
  if (user.activeRole !== 'User') return <Navigate to="/" replace />
  if (!user.candidateProfileId) return <Navigate to="/profile-setup" replace />

  const handleChanged = (updated: EmailDraft) =>
    setDrafts((current) => current.map((d) => (d.id === updated.id ? updated : d)))
  const handleDeleted = (draftId: string) => setDrafts((current) => current.filter((d) => d.id !== draftId))

  const unsent = drafts.filter((d) => d.status === 'DRAFT')
  const sent = drafts.filter((d) => d.status === 'SENT')

  const renderCards = (list: EmailDraft[]) => (
    <div className="match-list">
      {list.map((draft) => (
        <EmailDraftCard
          key={draft.id}
          draft={draft}
          highlighted={draft.id === highlightId}
          onChanged={handleChanged}
          onDeleted={handleDeleted}
        />
      ))}
    </div>
  )

  return (
    <div className="container">
      <AppHeader
        title="Email Drafts"
        subtitle="Review your application email drafts, then send them from your own email"
        navItems={getNavItems(user)}
      />
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}
        {highlightId && !loading && drafts.some((d) => d.id === highlightId && d.status === 'DRAFT') && (
          <Alert type="success">
            {generatedDraftId ? 'Your draft is ready.' : 'Here is the draft your AI assistant saved.'} Review and edit it
            below, then open it in your email app to send.
          </Alert>
        )}

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : (
          <>
            {unsent.length === 0 ? (
              <div className="empty-state">
                <h3>No drafts to review</h3>
                <p>
                  Use <strong>✉ Draft email</strong> on any of your <Link to="/job-matches">job matches</Link> to generate
                  one, or ask an AI assistant connected to this app, such as Claude. Drafts show up here for a final
                  review before you send them.
                </p>
              </div>
            ) : (
              renderCards(unsent)
            )}

            {sent.length > 0 && (
              <>
                <h3 className="section-title draft-section-title">Sent ({sent.length})</h3>
                {renderCards(sent)}
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
