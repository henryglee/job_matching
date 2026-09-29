import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { getCandidate, updateCandidate } from '../api/candidates'
import { updateOwnName } from '../api/users'
import { isSameAccountName, validateAccountName, type AccountNameValue } from '../accountName'
import { AccountNameFields } from '../components/AccountNameFields'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { CandidateFields, type CandidateFieldsValue } from '../components/CandidateFields'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import { toDateInputValue } from '../format'
import { toLocationSponsorshipPayload, toLocationSponsorshipValue } from '../locationSponsorship'
import { readBackup, removeBackup, useBackupWhileDirty } from '../formBackup'

// Everything on this page that a backup (formBackup) keeps: both sections.
interface ProfileEdits {
  form: CandidateFieldsValue
  accountName: AccountNameValue
}

const BACKUP_KEY = 'profile'

export default function EditProfilePage() {
  const { user } = useAuth()

  const [form, setForm] = useState<CandidateFieldsValue | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [accountName, setAccountName] = useState<AccountNameValue>({ firstName: '', lastName: '' })
  // Last saved account name, so an untouched name isn't re-sent on every save.
  const [savedAccountName, setSavedAccountName] = useState<AccountNameValue>({ firstName: '', lastName: '' })
  // What the page showed after loading or last saving; edits are anything different from it.
  const [baseline, setBaseline] = useState<ProfileEdits | null>(null)
  // The profile's date_updated when loaded, to tell whether a restored backup predates a
  // change made elsewhere. Null once this page has saved (its own save isn't "elsewhere").
  const [serverVersion, setServerVersion] = useState<string | null>(null)
  const [restored, setRestored] = useState<{ serverChanged: boolean } | null>(null)

  const edits = useMemo<ProfileEdits | null>(() => (form ? { form, accountName } : null), [form, accountName])
  const dirty = !!edits && !!baseline && JSON.stringify(edits) !== JSON.stringify(baseline)
  // Back up unsaved edits so an expired session or a reload doesn't lose them. The key stays
  // null until loading has read any existing backup.
  useBackupWhileDirty(user?.id, baseline ? BACKUP_KEY : null, edits, dirty, serverVersion)

  useEffect(() => {
    if (!user?.candidateProfileId) return
    getCandidate(user.candidateProfileId)
      .then((candidate) => {
        const current = { firstName: candidate.account_first_name ?? '', lastName: candidate.account_last_name ?? '' }
        setSavedAccountName(current)
        // Like the Flask page: with no account name yet, start from the profile's full name.
        const loadedName =
          !current.firstName && !current.lastName && candidate.candidate_name
            ? (() => {
                const parts = candidate.candidate_name.trim().split(/\s+/)
                return { firstName: parts[0] ?? '', lastName: parts.slice(1).join(' ') }
              })()
            : current
        const loaded: ProfileEdits = {
          form: {
            candidate_name: candidate.candidate_name,
            // A blank profile email shows (and on the next save becomes) the account email.
            email: candidate.email || user.email || '',
            college_degree: candidate.college_degree ?? '',
            graduation_date: toDateInputValue(candidate.graduation_date),
            preferred_job_title: candidate.preferred_job_title ?? '',
            skills: candidate.skills ?? [],
            ...toLocationSponsorshipValue(candidate),
          },
          accountName: loadedName,
        }
        const version = candidate.date_updated ?? null

        // Unsaved edits left from before (e.g. the session expired mid-edit) take the place of
        // the loaded values, with a notice and a way to discard them.
        const backup = user?.id ? readBackup<ProfileEdits>(user.id, BACKUP_KEY) : null
        const restore = backup && JSON.stringify(backup.value) !== JSON.stringify(loaded) ? backup : null
        if (backup && !restore && user?.id) removeBackup(user.id, BACKUP_KEY)

        setForm(restore ? restore.value.form : loaded.form)
        setAccountName(restore ? restore.value.accountName : loaded.accountName)
        setRestored(restore ? { serverChanged: restore.base !== null && restore.base !== version } : null)
        setServerVersion(version)
        setBaseline(loaded)
      })
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Failed to load your profile.'))
      .finally(() => setLoading(false))
  }, [user?.candidateProfileId, user?.id, user?.email])

  if (!user) return <Navigate to="/login" replace />
  if (user.activeRole !== 'User') return <Navigate to="/" replace />
  if (!user.candidateProfileId) return <Navigate to="/profile-setup" replace />

  const handleSave = async () => {
    if (!form) return
    if (!form.candidate_name.trim()) {
      setSaveError('Full name is required.')
      return
    }
    const nameChanged = !isSameAccountName(accountName, savedAccountName)
    const nameError = nameChanged ? validateAccountName(accountName) : null
    if (nameError) {
      setSaveError(nameError)
      return
    }
    setSaving(true)
    setSaveError(null)
    setSaved(false)
    try {
      await updateCandidate(user.candidateProfileId!, {
        candidate_name: form.candidate_name.trim(),
        email: form.email.trim() || null,
        college_degree: form.college_degree.trim() || null,
        graduation_date: form.graduation_date || null,
        preferred_job_title: form.preferred_job_title.trim() || null,
        skills: form.skills,
        ...toLocationSponsorshipPayload(form),
      })
      let finalName = accountName
      if (nameChanged) {
        const trimmed = { firstName: accountName.firstName.trim(), lastName: accountName.lastName.trim() }
        await updateOwnName(trimmed.firstName, trimmed.lastName)
        setAccountName(trimmed)
        setSavedAccountName(trimmed)
        finalName = trimmed
      }
      // Saved: nothing is unsaved any more, so the backup is dropped.
      setBaseline({ form, accountName: finalName })
      setServerVersion(null)
      setRestored(null)
      setSaved(true)
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Failed to save your profile.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="container container-form">
      <AppHeader title="Edit Profile" subtitle="Keep your candidate profile up to date" navItems={getNavItems(user)} />
      <div className="content">
        {loadError && <Alert type="error">{loadError}</Alert>}
        {saveError && <Alert type="error">{saveError}</Alert>}
        {saved && <Alert type="success">Profile updated successfully.</Alert>}
        {restored && form && baseline && (
          <div className="alert alert-info draft-restored">
            <span>
              Restored changes you hadn't saved.
              {restored.serverChanged && ' Your profile was also changed since then, so check it before saving.'}
            </span>
            <button
              type="button"
              className="btn-link"
              onClick={() => {
                setForm(baseline.form)
                setAccountName(baseline.accountName)
                setRestored(null)
              }}
            >
              Discard them
            </button>
          </div>
        )}

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : form ? (
          <>
            <h3 className="form-section-title">Account Name</h3>
            <AccountNameFields value={accountName} onChange={setAccountName} idPrefix="account" />
            <h3 className="form-section-title">Candidate Profile</h3>
            <CandidateFields value={form} onChange={setForm} idPrefix="profile" emailReadOnly />
            <button type="button" className="btn btn-primary btn-block" disabled={saving} onClick={handleSave}>
              {saving ? <Spinner small /> : 'Save Changes'}
            </button>
          </>
        ) : null}
      </div>
    </div>
  )
}
