/**
 * NavigationProvider — owns navigation history for the authenticated shell.
 *
 * Kept separate from `navigationContext.ts` so this file exports only a
 * component, which is what keeps React Fast Refresh working correctly.
 */

import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { NavigationContext, type AppView } from './navigationContext'

const DEFAULT_VIEW: AppView = { view: 'DASHBOARD' }
const MAX_HISTORY = 20

export function NavigationProvider({ children }: { children: ReactNode }) {
  const [history, setHistory] = useState<AppView[]>([DEFAULT_VIEW])

  const current = history[history.length - 1] ?? DEFAULT_VIEW

  const navigate = useCallback((view: AppView) => {
    setHistory((prev) => {
      const next = [...prev, view]
      // Cap history to avoid unbounded growth.
      return next.length > MAX_HISTORY ? next.slice(next.length - MAX_HISTORY) : next
    })
  }, [])

  const goBack = useCallback(() => {
    setHistory((prev) => {
      if (prev.length <= 1) return [DEFAULT_VIEW]
      return prev.slice(0, -1)
    })
  }, [])

  const value = useMemo(
    () => ({ current, navigate, goBack }),
    [current, navigate, goBack],
  )

  return <NavigationContext.Provider value={value}>{children}</NavigationContext.Provider>
}
