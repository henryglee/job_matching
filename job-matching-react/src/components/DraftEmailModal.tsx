import { useEffect, useRef, useState } from 'react'
import { jwtDecode } from 'jwt-decode'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, backendUrl, getToken } from '../api/client'
import { generateDraft } from '../api/drafts'
import type { JobMatch, JwtPayload } from '../types'
import { Alert } from './Alert'
import { CopyButton } from './CopyButton'
import { Modal } from './Modal'
import { Spinner } from './Spinner'

interface DraftEmailModalProps {
  job: JobMatch
  onClose: () => void
}

const MAX_INSTRUCTIONS = 500

// Two ways to draft an application email for one match, both ending on the Email Drafts page:
// - Generate draft (main): the backend writes it with the app's own model (EmailDraftGenerator).
// - The candidate's own AI assistant over MCP (mcp/EmailDraftTools): a prompt to paste, the
//   one-time setup command, and a way back to Email Drafts afterwards (the assistant also
//   gets a review link from save_email_draft). Opens automatically if generating fails.
export function DraftEmailModal({ job, onClose }: DraftEmailModalProps) {
  const navigate = useNavigate()
  const [instructions, setInstructions] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [assistantOpen, setAssistantOpen] = useState(false)

  // The request keeps going if the dialog is closed; don't touch state or navigate after that.
  const mounted = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const handleGenerate = async () => {
    setGenerating(true)
    setError(null)
    try {
      const draft = await generateDraft(job.id, instructions)
      if (mounted.current) navigate('/email-drafts', { state: { generatedDraftId: draft.id } })
    } catch (err) {
      if (!mounted.current) return
      setError(err instanceof ApiError ? err.message : "Couldn't generate a draft. Try again, or use your own AI assistant.")
      // Offer the AI-assistant route when generating is unavailable (limit hit, model down),
      // not when the request itself was refused (e.g. instructions blocked by moderation).
      if (!(err instanceof ApiError) || err.status === 429 || err.status >= 500) {
        setAssistantOpen(true)
      }
    } finally {
      if (mounted.current) setGenerating(false)
    }
  }

  const company = job.company_name ? ` at ${job.company_name}` : ''
  const prompt =
    `Use the job-matching tools to draft an application email for the "${job.title}" role${company} ` +
    `(jobId: ${job.id}). Show me the draft and wait for my approval before saving it.`

  const token = getToken()
  const expiresAt = tokenExpiry(token)
  const setupCommand = `claude mcp add --transport http jobmatch ${backendUrl('/mcp')} --header "Authorization: Bearer ${token ?? '<token>'}"`

  return (
    // Not closable mid-generation, so the candidate doesn't lose track of a draft being written.
    <Modal title="Draft an application email" onClose={generating ? () => {} : onClose}>
      <p className="draft-modal-job">
        {job.title}
        {job.company_name && <span> · {job.company_name}</span>}
      </p>

      {error && <Alert type="error">{error}</Alert>}

      <div className="form-group">
        <label htmlFor="draft-instructions">Anything to include? (optional)</label>
        <textarea
          id="draft-instructions"
          rows={3}
          maxLength={MAX_INSTRUCTIONS}
          placeholder="e.g. Friendly tone, mention my capstone project"
          value={instructions}
          disabled={generating}
          onChange={(e) => setInstructions(e.target.value)}
        />
      </div>
      <button type="button" className="btn btn-primary btn-block" disabled={generating} onClick={handleGenerate}>
        {generating ? (
          <>
            <Spinner small /> Writing your draft…
          </>
        ) : (
          '✨ Generate draft'
        )}
      </button>
      <p className="draft-hint draft-generate-hint">
        Takes a few seconds. It's saved to <Link to="/email-drafts">Email Drafts</Link> for you to review and edit.
        Nothing is sent until you send it from your own email.
      </p>

      <details className="draft-alt" open={assistantOpen} onToggle={(e) => setAssistantOpen(e.currentTarget.open)}>
        <summary>Or draft it with your own AI assistant</summary>
        <p className="draft-modal-intro">
          Chat with your assistant (such as Claude) to write and revise it. Once you approve it there, it shows up on
          your Email Drafts page.
        </p>

        <div className="form-group">
          <label htmlFor="draft-prompt">Paste this into your AI assistant</label>
          <textarea id="draft-prompt" rows={4} readOnly value={prompt} onFocus={(e) => e.target.select()} />
        </div>
        <div className="draft-modal-actions">
          <CopyButton text={() => prompt} label="Copy prompt" />
          <CopyButton text={() => job.id} label="Copy job ID only" />
        </div>
        <p className="draft-hint">
          Prefer the built-in prompt? Pick <code>draft_application_email</code> in your assistant and paste the job ID.
        </p>
        <div className="draft-next-step">
          <p>
            Approved it in your assistant? It gives you a link to the draft, or you can find it on Email Drafts.
          </p>
          <Link to="/email-drafts" className="btn btn-secondary btn-sm">
            Go to Email Drafts →
          </Link>
        </div>

        <details className="draft-setup">
          <summary>First time? Connect your AI assistant</summary>
          <ol>
            <li>
              Install <a href="https://claude.com/claude-code" target="_blank" rel="noreferrer">Claude Code</a> or
              another MCP client that supports Streamable HTTP.
            </li>
            <li>Run this once in a terminal:</li>
          </ol>
          <textarea
            className="draft-setup-command"
            rows={4}
            readOnly
            value={setupCommand}
            onFocus={(e) => e.target.select()}
          />
          <div className="draft-modal-actions">
            <CopyButton text={() => setupCommand} label="Copy setup command" />
          </div>
          <p className="draft-hint">
            This command contains your login token. Anyone who has it can act as you
            {expiresAt ? ` until ${expiresAt.toLocaleString()}` : ''}, so don't share it or save it in a repo. After it
            expires, log in again and run <code>claude mcp remove jobmatch</code>, then this command again.
          </p>
        </details>
      </details>
    </Modal>
  )
}

function tokenExpiry(token: string | null): Date | null {
  if (!token) return null
  try {
    return new Date(jwtDecode<JwtPayload>(token).exp * 1000)
  } catch {
    return null
  }
}
