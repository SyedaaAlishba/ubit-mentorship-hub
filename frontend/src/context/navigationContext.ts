/**
 * Navigation context for the authenticated application shell.
 *
 * Because the app uses a server-driven state machine rather than URL routing,
 * navigation between authenticated views is managed here as local React state.
 * The current view is a discriminated union so TypeScript can ensure that all
 * views that need parameters (e.g. a question ID) actually carry them.
 *
 * This context is intentionally separate from `AuthContext`: auth state drives
 * the outer shell (sign-in / onboarding / verification), while navigation state
 * drives the inner content once the user is verified.
 */

import { createContext, useContext } from 'react'

/** Every navigable view in the authenticated shell. */
export type AppView =
  | { view: 'DASHBOARD' }
  | { view: 'QUESTIONS' }
  | { view: 'QUESTION_DETAIL'; questionId: string }
  | { view: 'ASK_QUESTION' }
  | { view: 'ALUMNI_DIRECTORY' }
  | { view: 'PUBLIC_PROFILE'; userId: string }
  | { view: 'MY_PROFILE' }
  | { view: 'EDIT_PROFILE' }
  | { view: 'MENTORSHIP' }
  | { view: 'VERIFICATION' }
  | { view: 'ADMIN' }

export interface NavigationContextValue {
  /** The view currently shown in the main content area. */
  current: AppView
  /** Navigate to a new view. */
  navigate: (view: AppView) => void
  /** Go back to the previous view, or to the Dashboard if there is no history. */
  goBack: () => void
}

export const NavigationContext = createContext<NavigationContextValue | null>(null)

export function useNavigation(): NavigationContextValue {
  const ctx = useContext(NavigationContext)
  if (!ctx) {
    throw new Error('useNavigation must be used inside a NavigationProvider')
  }
  return ctx
}
