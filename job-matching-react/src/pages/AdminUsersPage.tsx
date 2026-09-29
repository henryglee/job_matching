import { useEffect, useMemo, useState } from 'react'
import * as adminApi from '../api/admin'
import { getCandidate } from '../api/candidates'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { Modal } from '../components/Modal'
import { CandidateDetails } from '../components/CandidateDetails'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import type { AdminUser, Candidate, Role } from '../types'

function abbreviate(id: string | null): string {
  if (!id) return 'None'
  return `${id.slice(0, 8)}…`
}

export default function AdminUsersPage() {
  const { user } = useAuth()

  const [users, setUsers] = useState<AdminUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [busy, setBusy] = useState<Set<string>>(new Set())

  const [addRoleFor, setAddRoleFor] = useState<string | null>(null)
  const [addRoleSelection, setAddRoleSelection] = useState('')
  const [linkProfileFor, setLinkProfileFor] = useState<string | null>(null)
  const [linkProfileInput, setLinkProfileInput] = useState('')
  // "View Profile": the linked candidate profile, fetched when the dialog opens.
  const [viewProfile, setViewProfile] = useState<{
    username: string
    candidate: Candidate | null
    error: string | null
  } | null>(null)

  const roleIdByName = useMemo(() => new Map(roles.map((r) => [r.role_name, r.role_id])), [roles])

  useEffect(() => {
    Promise.all([adminApi.getUsers(), adminApi.getRoles()])
      .then(([usersRes, rolesRes]) => {
        setUsers(usersRes.users)
        setRoles(rolesRes.roles)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load users.'))
      .finally(() => setLoading(false))
  }, [])

  const withBusy = async (key: string, fn: () => Promise<void>) => {
    setBusy((prev) => new Set(prev).add(key))
    setError(null)
    try {
      await fn()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setBusy((prev) => {
        const next = new Set(prev)
        next.delete(key)
        return next
      })
    }
  }

  const filtered = users.filter(
    (u) => u.username.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase()),
  )

  const handleAssignRole = (targetUser: AdminUser) => {
    if (!addRoleSelection) return
    void withBusy(`assign:${targetUser.id}`, async () => {
      await adminApi.assignRole(targetUser.id, addRoleSelection)
      const roleName = roles.find((r) => r.role_id === addRoleSelection)?.role_name
      setUsers((prev) =>
        prev.map((u) =>
          u.id === targetUser.id && roleName && !u.roles.includes(roleName)
            ? { ...u, roles: [...u.roles, roleName] }
            : u,
        ),
      )
      setAddRoleFor(null)
      setAddRoleSelection('')
    })
  }

  const handleRemoveRole = (targetUser: AdminUser, roleName: string) => {
    const roleId = roleIdByName.get(roleName)
    if (!roleId) return
    void withBusy(`remove:${targetUser.id}:${roleId}`, async () => {
      await adminApi.removeRole(targetUser.id, roleId)
      setUsers((prev) =>
        prev.map((u) => (u.id === targetUser.id ? { ...u, roles: u.roles.filter((r) => r !== roleName) } : u)),
      )
    })
  }

  const handleLinkProfile = (targetUser: AdminUser) => {
    if (!linkProfileInput.trim()) return
    void withBusy(`link:${targetUser.id}`, async () => {
      await adminApi.linkCandidateProfile(targetUser.id, linkProfileInput.trim())
      setUsers((prev) =>
        prev.map((u) => (u.id === targetUser.id ? { ...u, candidate_profile_id: linkProfileInput.trim() } : u)),
      )
      setLinkProfileFor(null)
      setLinkProfileInput('')
    })
  }

  const openViewProfile = (targetUser: AdminUser) => {
    const candidateId = targetUser.candidate_profile_id
    if (!candidateId) return
    setViewProfile({ username: targetUser.username, candidate: null, error: null })
    getCandidate(candidateId)
      .then((candidate) =>
        // Ignore the result if the dialog was closed (or reopened for someone else) meanwhile.
        setViewProfile((current) => (current?.username === targetUser.username ? { ...current, candidate } : current)),
      )
      .catch((err) =>
        setViewProfile((current) =>
          current?.username === targetUser.username
            ? { ...current, error: err instanceof ApiError ? err.message : 'Failed to load profile.' }
            : current,
        ),
      )
  }

  const handleUnlinkProfile = (targetUser: AdminUser) => {
    void withBusy(`unlink:${targetUser.id}`, async () => {
      await adminApi.unlinkCandidateProfile(targetUser.id)
      setUsers((prev) => prev.map((u) => (u.id === targetUser.id ? { ...u, candidate_profile_id: null } : u)))
    })
  }

  const handleDeleteUser = (targetUser: AdminUser) => {
    if (!confirm(`Delete user ${targetUser.username}? This cannot be undone.`)) return
    void withBusy(`delete:${targetUser.id}`, async () => {
      await adminApi.deleteUser(targetUser.id)
      setUsers((prev) => prev.filter((u) => u.id !== targetUser.id))
    })
  }

  return (
    <div className="container">
      <AppHeader title="Admin — Users" subtitle="Manage user accounts, roles, and profile links" navItems={getNavItems(user)} />
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}

        <div className="toolbar">
          <div className="toolbar-left">
            <input
              type="text"
              className="search-input"
              placeholder="Search by username or email…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="table-stack">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Roles</th>
                  <th>Candidate Profile</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((u) => (
                  <tr key={u.id}>
                    <td data-label="Username">{u.username}</td>
                    <td data-label="Email">{u.email}</td>
                    <td data-label="Roles">
                      <div className="pill-list">
                        {u.roles.map((roleName) => {
                          const key = `remove:${u.id}:${roleIdByName.get(roleName)}`
                          return (
                            <span key={roleName} className="pill">
                              {roleName}
                              {busy.has(key) ? (
                                <Spinner small />
                              ) : (
                                <span
                                  className="pill-remove"
                                  role="button"
                                  tabIndex={0}
                                  aria-label={`Remove ${roleName} role`}
                                  onClick={() => handleRemoveRole(u, roleName)}
                                  onKeyDown={(e) => {
                                    if (e.key === 'Enter' || e.key === ' ') {
                                      e.preventDefault()
                                      handleRemoveRole(u, roleName)
                                    }
                                  }}
                                >
                                  ×
                                </span>
                              )}
                            </span>
                          )
                        })}
                      </div>
                      {addRoleFor === u.id ? (
                        <div className="inline-edit-row" style={{ marginTop: 8 }}>
                          <select value={addRoleSelection} onChange={(e) => setAddRoleSelection(e.target.value)}>
                            <option value="">Select role…</option>
                            {roles
                              .filter((r) => !u.roles.includes(r.role_name))
                              .map((r) => (
                                <option key={r.role_id} value={r.role_id}>
                                  {r.role_name}
                                </option>
                              ))}
                          </select>
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            disabled={busy.has(`assign:${u.id}`)}
                            onClick={() => handleAssignRole(u)}
                          >
                            Assign
                          </button>
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setAddRoleFor(null)}>
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div style={{ marginTop: 8 }}>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setAddRoleFor(u.id)
                              setAddRoleSelection('')
                            }}
                          >
                            Add Role
                          </button>
                        </div>
                      )}
                    </td>
                    <td data-label="Candidate Profile">
                      <div>{abbreviate(u.candidate_profile_id)}</div>
                      {linkProfileFor === u.id ? (
                        <div className="inline-edit-row" style={{ marginTop: 8 }}>
                          <input
                            type="text"
                            placeholder="Candidate UUID"
                            value={linkProfileInput}
                            onChange={(e) => setLinkProfileInput(e.target.value)}
                            style={{ minWidth: 160 }}
                          />
                          <button
                            type="button"
                            className="btn btn-primary btn-sm"
                            disabled={busy.has(`link:${u.id}`)}
                            onClick={() => handleLinkProfile(u)}
                          >
                            Link
                          </button>
                          <button type="button" className="btn btn-secondary btn-sm" onClick={() => setLinkProfileFor(null)}>
                            Cancel
                          </button>
                        </div>
                      ) : (
                        <div className="row-actions" style={{ marginTop: 8 }}>
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => {
                              setLinkProfileFor(u.id)
                              setLinkProfileInput('')
                            }}
                          >
                            Link Profile
                          </button>
                          {u.candidate_profile_id && (
                            <button type="button" className="btn btn-secondary btn-sm" onClick={() => openViewProfile(u)}>
                              View Profile
                            </button>
                          )}
                          {u.candidate_profile_id && (
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              disabled={busy.has(`unlink:${u.id}`)}
                              onClick={() => handleUnlinkProfile(u)}
                            >
                              Unlink
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                    <td data-label="Actions">
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        disabled={u.id === user?.id || busy.has(`delete:${u.id}`)}
                        onClick={() => handleDeleteUser(u)}
                      >
                        {busy.has(`delete:${u.id}`) ? <Spinner small /> : 'Delete'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {viewProfile && (
        <Modal title={`Profile linked to ${viewProfile.username}`} onClose={() => setViewProfile(null)}>
          {viewProfile.error ? (
            <Alert type="error">{viewProfile.error}</Alert>
          ) : viewProfile.candidate ? (
            <CandidateDetails candidate={viewProfile.candidate} />
          ) : (
            <div className="loading-row">
              <Spinner />
            </div>
          )}
        </Modal>
      )}
    </div>
  )
}
