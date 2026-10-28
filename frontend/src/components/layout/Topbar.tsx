import Avatar from '@/components/ui/Avatar'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { Menu } from 'lucide-react'
import styles from './Topbar.module.css'

export interface TopbarProps {
  onToggleSidebar?: () => void
}

export default function Topbar({ onToggleSidebar }: TopbarProps) {
  const { user } = useAuth()
  const { current, navigate } = useNavigation()

  if (!user) return null

  function getTitle() {
    switch (current.view) {
      case 'DASHBOARD':
        return 'Dashboard'
      case 'QUESTIONS':
        return 'Community Q&A'
      case 'QUESTION_DETAIL':
        return 'Question'
      case 'ASK_QUESTION':
        return 'Ask a Question'
      case 'ALUMNI_DIRECTORY':
        return 'Alumni Directory'
      case 'PUBLIC_PROFILE':
        return 'User Profile'
      case 'MY_PROFILE':
        return 'My Profile'
      case 'EDIT_PROFILE':
        return 'Edit Profile'
      case 'MENTORSHIP':
        return 'Mentorship'
      case 'VERIFICATION':
        return 'Student Verification'
      case 'ADMIN':
        return 'Admin Console'
      default:
        return 'UBIT Hub'
    }
  }

  return (
    <header className={styles.topbar}>
      <div className={styles.left}>
        <button
          type="button"
          className={styles.menuBtn}
          onClick={onToggleSidebar}
          aria-label="Toggle navigation menu"
        >
          <Menu size={20} strokeWidth={2} />
        </button>
        <h2 className={styles.title}>{getTitle()}</h2>
      </div>

      <div className={styles.right}>
        <div
          className={styles.profileBtn}
          onClick={() => navigate({ view: 'MY_PROFILE' })}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => e.key === 'Enter' && navigate({ view: 'MY_PROFILE' })}
        >
          <Avatar name={user.name ?? user.email} size="sm" />
          <span className={styles.profileName}>{user.name ?? user.email}</span>
        </div>
      </div>
    </header>
  )
}
