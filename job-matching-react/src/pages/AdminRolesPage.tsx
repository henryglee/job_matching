import { useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import type { Role } from '../types'

export default function AdminRolesPage() {
  const { user } = useAuth()

  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')
  const [savingId, setSavingId] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const [newRoleName, setNewRoleName] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    adminApi
      .getRoles()
      .then((res) => setRoles(res.roles))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load roles.'))
      .finally(() => setLoading(false))
  }, [])

  const startEdit = (role: Role) => {
    setEditingId(role.role_id)
    setEditValue(role.role_name)
  }

  const handleSaveEdit = async (role: Role) => {
    if (!editValue.trim()) return
    setSavingId(role.role_id)
    setError(null)
    try {
      await adminApi.updateRole(role.role_id, editValue.trim())
      setRoles((prev) => prev.map((r) => (r.role_id === role.role_id ? { ...r, role_name: editValue.trim() } : r)))
      setEditingId(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to update role.')
    } finally {
      setSavingId(null)
    }
  }

  const handleDelete = async (role: Role) => {
    if (!confirm(`Delete role "${role.role_name}"? This cannot be undone.`)) return
    setDeletingId(role.role_id)
    setError(null)
    try {
      await adminApi.deleteRole(role.role_id)
      setRoles((prev) => prev.filter((r) => r.role_id !== role.role_id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to delete role.')
    } finally {
      setDeletingId(null)
    }
  }

  const handleCreate = async () => {
    if (!newRoleName.trim()) return
    setCreating(true)
    setError(null)
    try {
      const role = await adminApi.createRole(newRoleName.trim())
      setRoles((prev) => [...prev, role])
      setNewRoleName('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to create role.')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="container">
      <AppHeader title="Admin — Roles" subtitle="Manage the roles available in the system" navItems={getNavItems(user)} />
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Role Name</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {roles.map((role) => (
                  <tr key={role.role_id}>
                    <td>
                      {editingId === role.role_id ? (
                        <input
                          type="text"
                          className="table-input"
                          aria-label="Role name"
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          autoFocus
                        />
                      ) : (
                        role.role_name
                      )}
                    </td>
                    <td>
                      <div className="row-actions">
                        {editingId === role.role_id ? (
                          <>
                            <button
                              type="button"
                              className="btn btn-primary btn-sm"
                              disabled={savingId === role.role_id}
                              onClick={() => handleSaveEdit(role)}
                            >
                              {savingId === role.role_id ? <Spinner small /> : 'Save'}
                            </button>
                            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditingId(null)}>
                              Cancel
                            </button>
                          </>
                        ) : (
                          <>
                            <button type="button" className="btn btn-secondary btn-sm" onClick={() => startEdit(role)}>
                              Edit
                            </button>
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              disabled={deletingId === role.role_id}
                              onClick={() => handleDelete(role)}
                            >
                              {deletingId === role.role_id ? <Spinner small /> : 'Delete'}
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
                <tr>
                  <td>
                    <input
                      type="text"
                      className="table-input"
                      aria-label="New role name"
                      placeholder="New role name"
                      value={newRoleName}
                      onChange={(e) => setNewRoleName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault()
                          void handleCreate()
                        }
                      }}
                    />
                  </td>
                  <td>
                    <button type="button" className="btn btn-primary btn-sm" disabled={creating} onClick={handleCreate}>
                      {creating ? <Spinner small /> : 'Create'}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
