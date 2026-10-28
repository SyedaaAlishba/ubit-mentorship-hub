/**
 * The session context object and the hook that reads it.
 *
 * Deliberately separate from `AuthProvider`: a module that exports both a
 * component and other bindings breaks React Fast Refresh, so editing the provider
 * would reload the whole page instead of swapping just that component. Keeping
 * the context here means the provider file exports a component and nothing else.
 */

import { createContext, useContext } from 'react'

import type { CurrentUser } from '@/types/api'

export interface AuthContextValue {
  /**
   * `undefined` while the first probe is in flight. The distinction matters: it
   * is the difference between "still loading" and "not signed in", and collapsing
   * them would flash the sign-in screen at a signed-in user.
   */
  user: CurrentUser | undefined
  /** True while a mutating call is in flight, so buttons can disable themselves. */
  busy: boolean
  refresh: () => Promise<void>
  setUser: (user: CurrentUser | undefined) => void
  signIn: () => void
  signOut: () => Promise<void>
}

/** The anonymous session, used before sign-in and after sign-out. */
export const ANONYMOUS_USER: CurrentUser = {
  state: 'NOT_AUTHENTICATED',
  userId: null,
  email: null,
  name: null,
  role: null,
  verificationStatus: null,
  enabled: false,
  onboardingComplete: false,
  canSubmitVerification: false,
  loginUrl: '/oauth2/authorization/google',
  logoutUrl: '/api/auth/logout',
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
