import { useMemo, useState } from 'react'
import { ApiError } from '../api/client'
import { deleteDraft, markDraftSent, updateDraft } from '../api/drafts'
import { useAuth } from '../context/AuthContext'
import { formatTimestamp } from '../format'
import { readBackup, removeBackup, useBackupWhileDirty } from '../formBackup'
import type { EmailDraft } from '../types'
import { Alert } from './Alert'
import { CopyButton } from './CopyButton'
import { Spinner } from './Spinner'
import { safeHttpUrl } from '../safeUrl'

// Some mail apps cut off mailto: links much past this, so longer drafts get a nudge to copy instead.
const MAILTO_SAFE_LENGTH = 1800

interface EmailDraftCardProps {
  draft: EmailDraft
  // Just generated from the job matches page; outlined so it stands out.
  highlighted?: boolean
  onChanged: (draft: EmailDraft) => void
  onDeleted: (draftId: string) => void
}

interface DraftFields {
  recipient: string
  subject: string
  body: string
}

function serverFields(draft: EmailDraft): DraftFields {
  return { recipient: draft.recipient ?? '', subject: draft.subject, body: draft.body }
}

function sameFields(a: DraftFields, b: DraftFields): boolean {
  return a.recipient === b.recipient && a.subject === b.subject && a.body === b.body
}

// Unsaved edits to this draft left in sessionStorage (e.g. before a session expired), if any.
// A backup of a sent draft, or one that matches the server copy, is dropped.
function restorableEdits(userId: string | undefined, draft: EmailDraft) {
  if (!userId) return null
  const backup = readBackup<DraftFields>(userId, backupKey(draft.id))
  if (!backup) return null
  if (draft.status !== 'DRAFT' || sameFields(backup.value, serverFields(draft))) {
    removeBackup(userId, backupKey(draft.id))
    return null
  }
  // The draft changed on the server since these edits were made, e.g. revised by the
  // candidate's AI assistant over MCP.
  return { fields: backup.value, serverChanged: backup.base !== draft.updated_at }
}

function backupKey(draftId: string): string {
  return `draft:${draftId}`
}

function mailtoHref(recipient: string, subject: string, body: string): string {
  const params = new URLSearchParams({ subject, body }).toString().replace(/\+/g, '%20')
  // Keep "@" literal: RFC 6068 allows %40, but not every mail app decodes it.
  return `mailto:${encodeURIComponent(recipient.trim()).replace(/%40/g, '@')}?${params}`
}

export function EmailDraftCard({ draft, highlighted = false, onChanged, onDeleted }: EmailDraftCardProps) {
  const { user } = useAuth()
  const [restored] = useState(() => restorableEdits(user?.id, draft))
  const initial = restored?.fields ?? serverFields(draft)
  const [recipient, setRecipient] = useState(initial.recipient)
  const [subject, setSubject] = useState(initial.subject)
  const [body, setBody] = useState(initial.body)
  const [showRestored, setShowRestored] = useState(restored !== null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const isSent = draft.status === 'SENT'
  const dirty = recipient !== (draft.recipient ?? '') || subject !== draft.subject || body !== draft.body

  // Back up unsaved edits so an expired session or a reload doesn't lose them.
  const fields = useMemo(() => ({ recipient, subject, body }), [recipient, subject, body])
  useBackupWhileDirty(user?.id, backupKey(draft.id), fields, dirty && !isSent, draft.updated_at)

  const discardRestored = () => {
    const server = serverFields(draft)
    setRecipient(server.recipient)
    setSubject(server.subject)
    setBody(server.body)
    setShowRestored(false)
  }
  const heading = draft.job_title ?? 'Job no longer listed'
  const idPrefix = `draft-${draft.id}`

  const run = async (action: () => Promise<void>, fallback: string) => {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : fallback)
    } finally {
      setBusy(false)
    }
  }

  const save = () =>
    updateDraft(draft.id, { recipient: recipient.trim(), subject, body }).then((saved) => {
      // Show what was stored: the recipient is trimmed on save, and left as typed it would still
      // count as an unsaved edit (Save stays enabled, and the backup keeps being rewritten).
      setRecipient(saved.recipient ?? '')
      onChanged(saved)
      setShowRestored(false)
      return saved
    })

  const handleSave = () => run(async () => void (await save()), 'Failed to save the draft.')

  // Saves unsaved edits first, so the sent record matches what actually went out.
  const handleMarkSent = () =>
    run(async () => {
      if (dirty) await save()
      onChanged(await markDraftSent(draft.id))
    }, 'Failed to mark the draft as sent.')

  const handleDelete = () =>
    run(async () => {
      await deleteDraft(draft.id)
      if (user) removeBackup(user.id, backupKey(draft.id))
      onDeleted(draft.id)
    }, 'Failed to delete the draft.')

  const copyText = () =>
    recipient.trim() ? `To: ${recipient.trim()}\nSubject: ${subject}\n\n${body}` : `${subject}\n\n${body}`

  return (
    <div id={`draft-card-${draft.id}`} className={`match-card draft-card ${highlighted ? 'draft-highlight' : ''}`} aria-busy={busy}>
      <div className="match-card-header">
        <div>
          <h2>{heading}</h2>
          {draft.company_name && <div className="match-company">{draft.company_name}</div>}
        </div>
        <span className={`draft-status ${isSent ? 'draft-status-sent' : ''}`}>
          {isSent ? `Sent ${formatTimestamp(draft.sent_at)}` : `Updated ${formatTimestamp(draft.updated_at)}`}
        </span>
      </div>

      {error && <Alert type="error">{error}</Alert>}
      {showRestored && !isSent && (
        <div className="alert alert-info draft-restored">
          <span>
            Restored changes you hadn't saved.
            {restored?.serverChanged &&
              ' This draft was also changed since then (for example by your AI assistant), so check it before saving.'}
          </span>
          <button type="button" className="btn-link" onClick={discardRestored}>
            Discard them
          </button>
        </div>
      )}

      {isSent ? (
        <div className="draft-readonly">
          {draft.recipient && (
            <div>
              <strong>To:</strong> {draft.recipient}
            </div>
          )}
          <div>
            <strong>Subject:</strong> {draft.subject}
          </div>
          <p className="draft-body">{draft.body}</p>
        </div>
      ) : (
        <>
          <div className="form-group">
            <label htmlFor={`${idPrefix}-to`}>To</label>
            <input
              id={`${idPrefix}-to`}
              type="email"
              value={recipient}
              placeholder="No contact email. Apply on the job page and use this as a cover letter."
              onChange={(e) => setRecipient(e.target.value)}
            />
          </div>
          <div className="form-group">
            <label htmlFor={`${idPrefix}-subject`}>Subject</label>
            <input id={`${idPrefix}-subject`} type="text" value={subject} onChange={(e) => setSubject(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor={`${idPrefix}-body`}>Message</label>
            <textarea id={`${idPrefix}-body`} rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
          </div>
          {body.length > MAILTO_SAFE_LENGTH && (
            <p className="draft-hint">This message is long. If your mail app cuts it off, use Copy instead.</p>
          )}
        </>
      )}

      <div className="draft-actions">
        {!isSent && (
          <>
            <button type="button" className="btn btn-primary btn-sm" disabled={busy || !dirty} onClick={handleSave}>
              {busy ? <Spinner small /> : 'Save changes'}
            </button>
            {recipient.trim() && (
              <a className="btn btn-secondary btn-sm" href={mailtoHref(recipient, subject, body)}>
                Open in email app
              </a>
            )}
          </>
        )}
        <CopyButton text={copyText} />
        {!isSent && (
          <button type="button" className="btn btn-success btn-sm" disabled={busy} onClick={handleMarkSent}>
            Mark as sent
          </button>
        )}
        {confirmingDelete ? (
          <>
            <button type="button" className="btn btn-danger btn-sm" disabled={busy} onClick={handleDelete}>
              Confirm delete
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setConfirmingDelete(false)}>
              Keep
            </button>
          </>
        ) : (
          <button type="button" className="btn btn-link draft-delete" onClick={() => setConfirmingDelete(true)}>
            Delete
          </button>
        )}
        {safeHttpUrl(draft.job_url) && (
          <a className="draft-job-link" href={safeHttpUrl(draft.job_url)} target="_blank" rel="noreferrer">
            View job →
          </a>
        )}
      </div>
    </div>
  )
}
