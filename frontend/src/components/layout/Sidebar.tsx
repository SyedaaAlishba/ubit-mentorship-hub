import type { ReactNode } from 'react'
import Avatar from '@/components/ui/Avatar'
import { useAuth } from '@/context/authContext'
import { useNavigation, type AppView } from '@/context/navigationContext'
import { roleLabel } from '@/utils/labels'
import {
  LayoutDashboard,
  MessagesSquare,
  UsersRound,
  Handshake,
  CircleUserRound,
  ShieldCheck,
  ShieldAlert,
  LogOut,
  Link2,
} from 'lucide-react'
import styles from './Sidebar.module.css'

export interface SidebarProps {
  isOpen?: boolean
  onClose?: () => void
}

export default function Sidebar({ isOpen = false, onClose }: SidebarProps) {
  const { user, signOut, busy } = useAuth()
  const { current, navigate } = useNavigation()

  if (!user) return null

  const isAdmin = user.role === 'ADMIN'
  const isVerified = user.state === 'VERIFIED' || user.verificationStatus === 'VERIFIED'

  const navItems: {
    label: string
    icon: ReactNode
    view: AppView
    active: boolean
  }[] = [
    {
      label: 'Dashboard',
      icon: <LayoutDashboard size={18} strokeWidth={2} />,
      view: { view: 'DASHBOARD' },
      active: current.view === 'DASHBOARD',
    },
    {
      label: 'Community Q&A',
      icon: <MessagesSquare size={18} strokeWidth={2} />,
      view: { view: 'QUESTIONS' },
      active:
        current.view === 'QUESTIONS' ||
        current.view === 'QUESTION_DETAIL' ||
        current.view === 'ASK_QUESTION',
    },
    {
      label: 'Alumni Directory',
      icon: <UsersRound size={18} strokeWidth={2} />,
      view: { view: 'ALUMNI_DIRECTORY' },
      active: current.view === 'ALUMNI_DIRECTORY',
    },
    {
      label: 'Mentorship',
      icon: <Handshake size={18} strokeWidth={2} />,
      view: { view: 'MENTORSHIP' },
      active: current.view === 'MENTORSHIP',
    },
    {
      label: 'My Profile',
      icon: <CircleUserRound size={18} strokeWidth={2} />,
      view: { view: 'MY_PROFILE' },
      active: current.view === 'MY_PROFILE' || current.view === 'EDIT_PROFILE',
    },
  ]

  if (!isVerified) {
    navItems.push({
      label: 'Verification Status',
      icon: <ShieldCheck size={18} strokeWidth={2} />,
      view: { view: 'VERIFICATION' as const },
      active: current.view === 'VERIFICATION',
    })
  }

  function handleNav(viewItem: Parameters<typeof navigate>[0]) {
    navigate(viewItem)
    if (onClose) onClose()
  }

  return (
    <>
      {isOpen && <div className={styles.backdrop} onClick={onClose} aria-hidden="true" />}
      <aside className={`${styles.sidebar} ${isOpen ? styles.open : ''}`}>
        <div className={styles.brand}>
          <div className={styles.brandLogo}>
            <Link2 size={18} strokeWidth={2.5} />
          </div>
          <h1 className={styles.brandName}>PeerLink</h1>
        </div>

        <nav className={styles.nav} aria-label="Main Navigation">
          <ul className={styles.navList}>
            {navItems.map((item) => (
              <li key={item.label}>
                <button
                  type="button"
                  className={`${styles.navLink} ${item.active ? styles.active : ''}`}
                  onClick={() => handleNav(item.view)}
                >
                  <span className={styles.navIcon}>{item.icon}</span>
                  <span>{item.label}</span>
                </button>
              </li>
            ))}

            {isAdmin && (
              <li className={styles.adminSection}>
                <span className={styles.adminLabel}>ADMINISTRATION</span>
                <button
                  type="button"
                  className={`${styles.navLink} ${current.view === 'ADMIN' ? styles.active : ''}`}
                  onClick={() => handleNav({ view: 'ADMIN' })}
                >
                  <span className={styles.navIcon}>
                    <ShieldAlert size={18} strokeWidth={2} />
                  </span>
                  <span>Admin Console</span>
                </button>
              </li>
            )}
          </ul>
        </nav>

        <div className={styles.footer}>
          <div
            className={styles.userCard}
            onClick={() => handleNav({ view: 'MY_PROFILE' })}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && handleNav({ view: 'MY_PROFILE' })}
          >
            <Avatar name={user.name ?? user.email} size="sm" />
            <div className={styles.userInfo}>
              <span className={styles.userName}>{user.name ?? 'User'}</span>
              <span className={styles.userRole}>{roleLabel(user.role)}</span>
            </div>
          </div>
          <button
            type="button"
            className={styles.signOutBtn}
            onClick={() => void signOut()}
            disabled={busy}
            title="Sign out"
            aria-label="Sign out"
          >
            <LogOut size={14} strokeWidth={2} />
          </button>
        </div>
      </aside>
    </>
  )
}
