import { useCallback, useEffect, useState } from 'react'
import { ShieldCheck, Clock, AlertTriangle, ExternalLink } from 'lucide-react'
import AppHeader from '@/components/AppHeader'
import Badge from '@/components/ui/Badge'
import Card from '@/components/ui/Card'
import Skeleton from '@/components/ui/Skeleton'
import { useAuth } from '@/context/authContext'
import { api, ApiRequestError } from '@/services/api'
import type { VerificationApplication } from '@/types/api'
import { formatDate } from '@/utils/format'
import { programLabel } from '@/utils/labels'
import AlumniVerificationForm from './AlumniVerificationForm'
import StudentVerificationForm from './StudentVerificationForm'
import OnboardingScreen from '@/features/auth/OnboardingScreen'
import styles from './VerificationScreen.module.css'

export default function VerificationScreen() {
  const { user, refresh } = useAuth()
  const [application, setApplication] = useState<VerificationApplication | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoadError(null)
    try {
      setApplication(await api.get<VerificationApplication>('/api/verification/me'))
    } catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 404) {
        setApplication(null)
      } else {
        setLoadError(
          cause instanceof ApiRequestError
            ? cause.message
            : 'Could not load your application status.',
        )
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    let active = true
    void (async () => {
      try {
        const current = await api.get<VerificationApplication>('/api/verification/me')
        if (active) setApplication(current)
      } catch (cause) {
        if (active) {
          if (cause instanceof ApiRequestError && cause.status === 404) {
            setApplication(null)
          } else {
            setLoadError(
              cause instanceof ApiRequestError
                ? cause.message
                : 'Could not load your application status.',
            )
          }
        }
      } finally {
        if (active) setLoading(false)
      }
    })()
    return () => {
      active = false
    }
  }, [])

  const afterSubmit = useCallback(async () => {
    await Promise.all([refresh(), load()])
  }, [refresh, load])

  if (!user) return null

  const isResubmission = (application?.attemptNumber ?? 0) > 0

  return (
    <div className={styles.shell}>
      <AppHeader />
      <main className={styles.main}>
        {loadError && (
          <div className={styles.errorBanner} role="alert">
            {loadError}
          </div>
        )}

        <StatusPanel application={application} loading={loading} />

        {user.canSubmitVerification && user.role === 'STUDENT' && (
          <div className={styles.formContainer}>
            <StudentVerificationForm isResubmission={isResubmission} onSubmitted={afterSubmit} />
          </div>
        )}

        {user.canSubmitVerification && user.role === 'ALUMNI' && (
          <div className={styles.formContainer}>
            <AlumniVerificationForm isResubmission={isResubmission} onSubmitted={afterSubmit} />
          </div>
        )}

        {user.canSubmitVerification && !user.role && (
          <div className={styles.formContainer}>
            <OnboardingScreen />
          </div>
        )}
      </main>
    </div>
  )
}

function StatusPanel({
  application,
  loading,
}: {
  application: VerificationApplication | null
  loading: boolean
}) {
  if (loading) {
    return (
      <Card className={styles.statusCard}>
        <Skeleton width="180px" height="1.5rem" />
        <Skeleton width="100%" height="1rem" />
        <Skeleton width="80%" height="1rem" />
      </Card>
    )
  }

  if (!application) {
    return (
      <Card className={styles.statusCard}>
        <div className={styles.statusHeader}>
          <h2 className={styles.statusTitle}>Verification Required</h2>
          <Badge variant="warning">
            <Clock size={13} style={{ marginRight: 4, verticalAlign: -1 }} />
            Not Applied
          </Badge>
        </div>
        <p className={styles.statusDesc}>
          Please submit your verification details below so an administrator can confirm your UBIT
          affiliation and grant you full community access.
        </p>
      </Card>
    )
  }

  const isPending = application.status === 'PENDING'
  const isVerified = application.status === 'VERIFIED'
  const isRejected = application.status === 'REJECTED'

  return (
    <Card className={styles.statusCard}>
      <div className={styles.statusHeader}>
        <h2 className={styles.statusTitle}>
          {isPending && 'Application Under Review'}
          {isVerified && 'Account Verified'}
          {isRejected && 'Verification Not Approved'}
        </h2>
        {isPending && (
          <Badge variant="warning">
            <Clock size={13} style={{ marginRight: 4, verticalAlign: -1 }} />
            Awaiting Review
          </Badge>
        )}
        {isVerified && (
          <Badge variant="success">
            <ShieldCheck size={13} style={{ marginRight: 4, verticalAlign: -1 }} />
            Verified
          </Badge>
        )}
        {isRejected && (
          <Badge variant="danger">
            <AlertTriangle size={13} style={{ marginRight: 4, verticalAlign: -1 }} />
            Rejected
          </Badge>
        )}
      </div>

      {isPending && (
        <p className={styles.statusDesc}>
          Your application was submitted{' '}
          {application.submittedAt ? formatDate(application.submittedAt) : 'recently'}. An
          administrator is reviewing your details. You will have full access once approved.
        </p>
      )}

      {isVerified && (
        <p className={styles.statusDesc}>
          Your account has been officially verified! You have complete access to ask and answer
          questions, explore alumni, and request mentorship.
        </p>
      )}

      {isRejected && (
        <div className={styles.rejectedBanner}>
          <p className={styles.statusDesc}>
            Your verification attempt #{application.attemptNumber} was not approved. Please review
            the requirements and submit updated documentation below.
          </p>
        </div>
      )}

      <div className={styles.detailsGrid}>
        {application.program && (
          <div className={styles.detailItem}>
            <span className={styles.detailLabel}>Program</span>
            <span className={styles.detailValue}>{programLabel(application.program)}</span>
          </div>
        )}
        {application.currentSemester !== null && (
          <div className={styles.detailItem}>
            <span className={styles.detailLabel}>Semester</span>
            <span className={styles.detailValue}>Semester {application.currentSemester}</span>
          </div>
        )}
        {application.documentType && (
          <div className={styles.detailItem}>
            <span className={styles.detailLabel}>Document Proof</span>
            <span className={styles.detailValue}>
              {application.documentFilename ?? 'Uploaded'} (
              {application.documentType === 'ENROLLMENT_SLIP' ? 'Enrollment Slip' : 'Student Card'})
            </span>
          </div>
        )}
        {application.passoutYear !== null && (
          <div className={styles.detailItem}>
            <span className={styles.detailLabel}>Pass-out Year</span>
            <span className={styles.detailValue}>{application.passoutYear}</span>
          </div>
        )}
        {application.linkedInUrl && (
          <div className={styles.detailItem}>
            <span className={styles.detailLabel}>LinkedIn</span>
            <a
              href={application.linkedInUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={styles.detailLink}
            >
              <span>{application.linkedInUrl}</span>
              <ExternalLink size={13} style={{ marginLeft: 4, verticalAlign: -1 }} />
            </a>
          </div>
        )}
      </div>
    </Card>
  )
}
