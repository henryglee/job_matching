import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { sendChat } from '../api/chat'
import { createDraft } from '../api/drafts'
import { useAuth } from '../context/AuthContext'
import { readBackup, useBackupWhileDirty } from '../formBackup'
import type { ChatMessage, DraftProposal } from '../types'
import { Alert } from './Alert'
import { CopyButton } from './CopyButton'
import { Spinner } from './Spinner'

// What the backend accepts (ChatRequest / ChatMessage); older turns are dropped from what's sent.
const MAX_MESSAGES_SENT = 20
const MAX_MESSAGE_CHARS = 8000
const MAX_INPUT_CHARS = 2000

const STARTER_QUESTIONS = [
  'Which of my matches are remote?',
  'Why is my top match a good fit?',
  'Draft an email for my top match',
  'What if I add a skill to my profile?',
]

interface Turn {
  role: 'user' | 'assistant'
  content: string
  proposals?: DraftProposal[]
}

// The conversation is kept per tab (formBackup) so a reload or an expired session doesn't
// lose it. Only the most recent turns are kept; the model only ever sees the last 20 anyway.
const HISTORY_KEY = 'chat:matches'
const MAX_TURNS_KEPT = 50

interface SavedChat {
  turns: Turn[]
  open: boolean
}

// The chat as it was left in this tab. A question still waiting for an answer when the page
// went away (reload mid-request) goes back into the input box instead, ready to resend.
function restoreChat(userId: string | undefined): { turns: Turn[]; open: boolean; input: string } {
  const saved = userId ? readBackup<SavedChat>(userId, HISTORY_KEY)?.value : null
  const turns = Array.isArray(saved?.turns) ? saved.turns : []
  const last = turns[turns.length - 1]
  if (last?.role === 'user') {
    return { turns: turns.slice(0, -1), open: true, input: last.content }
  }
  return { turns, open: !!saved?.open && turns.length > 0, input: '' }
}

// The conversation as sent to the backend. A proposed draft's text is added to that turn, so
// the model can revise it when asked ("make it more formal"): the panel is the only place the
// conversation lives.
function toWire(turns: Turn[]): ChatMessage[] {
  return turns.slice(-MAX_MESSAGES_SENT).map((turn) => {
    const drafts = (turn.proposals ?? [])
      .map((p) => `\n\n[Draft shown to the candidate for "${p.job_title}" (jobId ${p.job_id})]\nSubject: ${p.subject}\n\n${p.body}`)
      .join('')
    return { role: turn.role, content: (turn.content + drafts).slice(0, MAX_MESSAGE_CHARS) }
  })
}

// "Ask about your matches" on the Job Matches page (candidates only). Answers come from the
// backend's MatchChatService, which only reads data: drafts it proposes appear as cards and are
// saved only when the candidate clicks Save. The server stores nothing; the conversation lives
// in this tab (sessionStorage), cleared by Clear chat, Logout, or closing the tab.
export function MatchChatPanel() {
  const { user } = useAuth()
  const [restored] = useState(() => restoreChat(user?.id))
  const [open, setOpen] = useState(restored.open)
  const [turns, setTurns] = useState<Turn[]>(restored.turns)
  const [input, setInput] = useState(restored.input)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' })
  }, [turns, sending, open])

  // Keep the conversation for this tab; an empty chat (new, or after Clear chat) removes it.
  const saved = useMemo<SavedChat>(() => ({ turns: turns.slice(-MAX_TURNS_KEPT), open }), [turns, open])
  useBackupWhileDirty(user?.id, HISTORY_KEY, saved, turns.length > 0, null)

  const send = async (text: string) => {
    const content = text.trim()
    if (!content || sending) return
    const withQuestion: Turn[] = [...turns, { role: 'user', content }]
    setTurns(withQuestion)
    setInput('')
    setSending(true)
    setError(null)
    try {
      const reply = await sendChat(toWire(withQuestion))
      setTurns([...withQuestion, { role: 'assistant', content: reply.reply, proposals: reply.draft_proposals }])
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "The assistant couldn't answer. Try again in a moment.")
      // Take the unanswered question back out and put it in the box, ready to resend.
      setTurns(turns)
      setInput(content)
    } finally {
      setSending(false)
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void send(input)
    }
  }

  return (
    <section className={`chat-panel ${open ? 'open' : ''}`}>
      <button type="button" className="chat-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span>💬 Ask about your matches</span>
        <span aria-hidden="true">{open ? '▾' : '▸'}</span>
      </button>

      {open && (
        <div className="chat-body">
          <p className="chat-intro">
            Answers come from your matches and profile. It can draft emails for you to save and try out profile
            changes to see how your scores would move, but it never sends or changes anything.
            {turns.length > 0 && (
              <>
                {' '}
                <button
                  type="button"
                  className="btn-link"
                  disabled={sending}
                  onClick={() => {
                    setTurns([])
                    setError(null)
                  }}
                >
                  Clear chat
                </button>
              </>
            )}
          </p>

          <div className="chat-messages" ref={listRef} aria-live="polite">
            {turns.length === 0 && (
              <div className="chat-starters">
                {STARTER_QUESTIONS.map((question) => (
                  <button key={question} type="button" className="chat-starter" onClick={() => void send(question)}>
                    {question}
                  </button>
                ))}
              </div>
            )}
            {turns.map((turn, i) => (
              <div key={i} className={`chat-turn chat-turn-${turn.role}`}>
                <div className="chat-bubble">{turn.content}</div>
                {turn.proposals?.map((proposal, j) => <DraftProposalCard key={j} proposal={proposal} />)}
              </div>
            ))}
            {sending && (
              <div className="chat-turn chat-turn-assistant">
                <div className="chat-bubble chat-thinking">
                  <Spinner small /> Thinking…
                </div>
              </div>
            )}
          </div>

          {error && <Alert type="error">{error}</Alert>}

          <div className="chat-input-row">
            <textarea
              aria-label="Ask about your matches"
              rows={2}
              maxLength={MAX_INPUT_CHARS}
              placeholder="Ask something… (Enter to send, Shift+Enter for a new line)"
              value={input}
              disabled={sending}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
            />
            <button
              type="button"
              className="btn btn-primary btn-sm"
              disabled={sending || !input.trim()}
              onClick={() => void send(input)}
            >
              Send
            </button>
          </div>
        </div>
      )}
    </section>
  )
}

function DraftProposalCard({ proposal }: { proposal: DraftProposal }) {
  const navigate = useNavigate()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSave = async () => {
    setSaving(true)
    setError(null)
    try {
      const draft = await createDraft({
        job_id: proposal.job_id,
        recipient: proposal.recipient,
        subject: proposal.subject,
        body: proposal.body,
      })
      // Same landing as "Generate draft": Email Drafts with the new draft highlighted.
      navigate('/email-drafts', { state: { generatedDraftId: draft.id } })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to save the draft.')
      setSaving(false)
    }
  }

  const copyText = () =>
    proposal.recipient
      ? `To: ${proposal.recipient}\nSubject: ${proposal.subject}\n\n${proposal.body}`
      : `${proposal.subject}\n\n${proposal.body}`

  return (
    <div className="chat-draft">
      <div className="chat-draft-header">
        ✉ {proposal.job_title ?? 'Job'}
        {proposal.company_name && <span> · {proposal.company_name}</span>}
      </div>
      <div className="chat-draft-meta">
        {proposal.recipient ? `To: ${proposal.recipient}` : 'Cover letter (this job has no contact email)'}
      </div>
      <div className="chat-draft-meta">Subject: {proposal.subject}</div>
      <p className="chat-draft-body">{proposal.body}</p>
      {error && <Alert type="error">{error}</Alert>}
      <div className="chat-draft-actions">
        <button type="button" className="btn btn-primary btn-sm" disabled={saving} onClick={handleSave}>
          {saving ? <Spinner small /> : 'Save to Email Drafts'}
        </button>
        <CopyButton text={copyText} />
      </div>
    </div>
  )
}
