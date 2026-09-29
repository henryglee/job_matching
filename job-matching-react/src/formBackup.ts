import { useEffect } from 'react'

// Per-tab state worth keeping across a page reload or a trip to /login when a session expires
// (see AuthContext.expireSession): unsaved form edits, offered back when the user returns, and
// the matches chat conversation (MatchChatPanel). sessionStorage is per tab and gone when the
// tab closes, and a deliberate logout clears it, so half-written text and chats don't linger
// on a shared computer. Keys include the user id, so nothing surfaces for a different account
// that logs in on the same tab.
//
// Every storage call is wrapped: sessionStorage can be unavailable (some private modes) or
// full, and a backup is a convenience, never a reason for a form to break.

const PREFIX = 'jobmatching.unsaved:'

export interface FormBackup<T> {
  value: T
  // The server version the edits were made against (e.g. updated_at), to warn when the
  // server copy has changed since.
  base: string | null
}

function storageKey(userId: string, key: string): string {
  return `${PREFIX}${userId}:${key}`
}

export function readBackup<T>(userId: string, key: string): FormBackup<T> | null {
  try {
    const raw = sessionStorage.getItem(storageKey(userId, key))
    return raw ? (JSON.parse(raw) as FormBackup<T>) : null
  } catch {
    return null
  }
}

export function writeBackup<T>(userId: string, key: string, value: T, base: string | null): void {
  try {
    sessionStorage.setItem(storageKey(userId, key), JSON.stringify({ value, base } satisfies FormBackup<T>))
  } catch {
    // Unavailable or full: carry on without a backup.
  }
}

export function removeBackup(userId: string, key: string): void {
  try {
    sessionStorage.removeItem(storageKey(userId, key))
  } catch {
    // Nothing to do.
  }
}

// Removes every backup, or every one not belonging to keepUserId.
export function clearBackups(keepUserId?: string): void {
  try {
    const keep = keepUserId ? `${PREFIX}${keepUserId}:` : null
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const key = sessionStorage.key(i)
      if (key?.startsWith(PREFIX) && !(keep && key.startsWith(keep))) {
        sessionStorage.removeItem(key)
      }
    }
  } catch {
    // Nothing to do.
  }
}

// Keeps `value` backed up while it differs from the server copy (dirty) and removes the
// backup once it doesn't: after a save, a discard, or undoing the edit by hand. Pass a null
// key until any existing backup has been read, or the first clean render would delete it.
export function useBackupWhileDirty<T>(
  userId: string | undefined,
  key: string | null,
  value: T,
  dirty: boolean,
  base: string | null,
): void {
  useEffect(() => {
    if (!userId || !key) return
    if (dirty) {
      writeBackup(userId, key, value, base)
    } else {
      removeBackup(userId, key)
    }
  }, [userId, key, value, dirty, base])
}
