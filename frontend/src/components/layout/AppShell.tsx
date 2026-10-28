import { useState, type ReactNode } from 'react'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { Clock, AlertTriangle } from 'lucide-react'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import styles from './AppShell.module.css'

export interface AppShellProps {
  children: ReactNode
}

export default function AppShell({ children }: AppShellProps) {
  const { user } = useAuth()
  const { navigate, current } = useNavigation()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  const isPending = user?.state === 'PENDING_VERIFICATION' || user?.verificationStatus === 'PENDING'
  const isRejected = user?.state === 'REJECTED' || user?.verificationStatus === 'REJECTED'
  const isViewingVerification = current.view === 'VERIFICATION'

  return (
    <div className={styles.container}>
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className={styles.mainWrapper}>
        <Topbar onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
        {isPending && !isViewingVerification && (
          <div className={styles.pendingBanner} role="status">
            <div className={styles.bannerContent}>
              <Clock size={18} strokeWidth={2} style={{ flexShrink: 0 }} />
              <span>
                <strong>Application under review:</strong> Your student verification is awaiting administrative review. You have read-only access to browse questions, alumni, and mentorship information.
              </span>
            </div>
            <button
              type="button"
              className={styles.bannerAction}
              onClick={() => navigate({ view: 'VERIFICATION' })}
            >
              View Application
            </button>
          </div>
        )}
        {isRejected && !isViewingVerification && (
          <div className={styles.rejectedBanner} role="alert">
            <div className={styles.bannerContent}>
              <AlertTriangle size={18} strokeWidth={2} style={{ flexShrink: 0 }} />
              <span>
                <strong>Application rejected:</strong> Your verification application was not approved. You can review the administrator's feedback and resubmit.
              </span>
            </div>
            <button
              type="button"
              className={styles.bannerAction}
              onClick={() => navigate({ view: 'VERIFICATION' })}
            >
              Resubmit
            </button>
          </div>
        )}
        <main className={styles.content}>{children}</main>
      </div>
    </div>
  )
}
