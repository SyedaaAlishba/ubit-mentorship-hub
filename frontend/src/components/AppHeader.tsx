/**
 * The header shared by every signed-in screen.
 *
 * Shows the account and, more importantly, shows role and verification as two
 * separate facts. Labels live in `utils/labels` so this module exports only a
 * component, which is what keeps React Fast Refresh working.
 */

import { useAuth } from '@/context/authContext'
import { roleLabel, statusLabel } from '@/utils/labels'

export default function AppHeader() {
  const { user, signOut, busy } = useAuth()
  if (!user) {
    return null
  }

  return (
    <header className="app-header">
      <div>
        <strong>{user.name ?? user.email}</strong>
        <span className="muted">
          {' '}
          &middot; {roleLabel(user.role)} &middot; {statusLabel(user.verificationStatus)}
        </span>
      </div>
      <button type="button" onClick={() => void signOut()} disabled={busy}>
        Sign out
      </button>
    </header>
  )
}
