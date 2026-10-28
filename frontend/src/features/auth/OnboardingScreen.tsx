import { useState } from 'react'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import { useAuth } from '@/context/authContext'
import { api, ApiRequestError } from '@/services/api'
import type { ClaimableRole, CurrentUser } from '@/types/api'
import { GraduationCap, Briefcase, ArrowRight } from 'lucide-react'
import styles from './OnboardingScreen.module.css'

interface RoleOption {
  role: ClaimableRole
  title: string
  description: string
  icon: typeof GraduationCap
  actionHint: string
}

const ROLES: RoleOption[] = [
  {
    role: 'STUDENT',
    title: 'Student',
    description: 'Connect with seniors, ask academic & career questions, and find experienced mentors.',
    icon: GraduationCap,
    actionHint: 'Requires student card or enrollment slip verification',
  },
  {
    role: 'ALUMNI',
    title: 'Alumni',
    description: 'Share your professional experience, answer student queries, and mentor the next generation.',
    icon: Briefcase,
    actionHint: 'Requires graduation year and LinkedIn profile verification',
  },
]

export default function OnboardingScreen() {
  const { refresh, setUser, busy } = useAuth()
  const [selectedRole, setSelectedRole] = useState<ClaimableRole | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleContinue() {
    if (!selectedRole || pending || busy) return

    setPending(true)
    setError(null)
    try {
      const updatedUser = await api.post<CurrentUser>('/api/auth/onboarding', { role: selectedRole })
      if (updatedUser) {
        setUser(updatedUser)
      } else {
        await refresh()
      }
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause.message
          : 'Could not save your selection. Please check your connection and try again.',
      )
      setPending(false)
    }
  }

  return (
    <main className={styles.container}>
      <div className={styles.header}>
        <div className={styles.badge}>Onboarding</div>
        <h1 className={styles.title}>Join the UBIT Community</h1>
        <p className={styles.subtitle}>
          Select how you would like to participate. Your selection will determine the verification
          process to unlock full community access.
        </p>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
        </div>
      )}

      <div className={styles.grid}>
        {ROLES.map(({ role, title, description, icon: IconComponent, actionHint }) => {
          const isSelected = selectedRole === role

          return (
            <Card
              key={role}
              className={`${styles.roleCard} ${isSelected ? styles.selected : ''}`}
              onClick={() => setSelectedRole(role)}
              role="radio"
              aria-checked={isSelected}
              tabIndex={0}
              onKeyDown={(e) => (e.key === ' ' || e.key === 'Enter') && setSelectedRole(role)}
            >
              <div className={styles.cardHeader}>
                <div className={styles.roleIconWrapper}>
                  <IconComponent size={28} strokeWidth={1.75} color="var(--color-burgundy)" />
                </div>
                <div className={styles.radioIndicator}>
                  <div className={styles.radioInner} />
                </div>
              </div>
              <h2 className={styles.roleTitle}>{title}</h2>
              <p className={styles.roleDescription}>{description}</p>
              <div className={styles.roleHint}>{actionHint}</div>
            </Card>
          )
        })}
      </div>

      <div className={styles.footer}>
        <Button
          variant="primary"
          size="lg"
          disabled={!selectedRole || pending || busy}
          isLoading={pending}
          onClick={() => void handleContinue()}
          className={styles.continueBtn}
          rightIcon={<ArrowRight size={18} strokeWidth={2} />}
        >
          Continue to Verification
        </Button>
      </div>
    </main>
  )
}
