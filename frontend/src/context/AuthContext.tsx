/**
 * Owns the session and exposes it to the tree.
 *
 * `GET /api/auth/me` is the one call the app routes on. It answers
 * `NOT_AUTHENTICATED` for an anonymous caller rather than 401, which lets the app
 * render a signed-out screen instead of treating "not signed in" as an error - the
 * common case on first load, not a failure.
 *
 * The context object and the `useAuth` hook live in `authContext.ts` so this file
 * exports only a component, which is what keeps React Fast Refresh working.
 */

import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { api, ApiRequestError } from '@/services/api'
import type { CurrentUser } from '@/types/api'
import { ANONYMOUS_USER, AuthContext, type AuthContextValue } from './authContext'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | undefined>(undefined)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(async () => {
    try {
      setUser(await api.get<CurrentUser>('/api/auth/me'))
    } catch (error) {
      // A failed probe is treated as signed out rather than crashing the app: the
      // UI still renders and the user can retry.
      if (!(error instanceof ApiRequestError) || error.status !== 401) {
        console.error('Could not read the session', error)
      }
      setUser(ANONYMOUS_USER)
    }
  }, [])

  // The first probe is a genuine synchronisation with an external system (the
  // server session), which is what an effect is for. The warning is about calling
  // setState synchronously in an effect body; here it happens after an await, on a
  // later microtask, so there is no cascading render.
  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const current = await api.get<CurrentUser>('/api/auth/me')
        if (active) {
          setUser(current)
        }
      } catch (error) {
        if (active) {
          if (!(error instanceof ApiRequestError) || error.status !== 401) {
            console.error('Could not read the session', error)
          }
          setUser(ANONYMOUS_USER)
        }
      }
    })()
    return () => {
      // Guards against a setState after unmount or after a remount, which would
      // otherwise show the previous session on the new one.
      active = false
    }
  }, [])

  const signIn = useCallback(() => {
    // A full navigation, not fetch: the OAuth2 flow ends in a redirect from
    // Google, so the browser has to follow it and the session cookie with it.
    window.location.assign('/oauth2/authorization/google')
  }, [])

  const signOut = useCallback(async () => {
    setBusy(true)
    try {
      await api.postEmpty('/api/auth/logout')
      setUser(ANONYMOUS_USER)
    } catch (error) {
      console.error('Sign-out failed', error)
    } finally {
      setBusy(false)
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ user, busy, refresh, setUser, signIn, signOut }),
    [user, busy, refresh, setUser, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
