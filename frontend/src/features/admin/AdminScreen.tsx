import { useCallback, useEffect, useState } from 'react'
import {
  ShieldAlert,
  FileText,
  ExternalLink,
  CheckCircle2,
  XCircle,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import EmptyState from '@/components/ui/EmptyState'
import ErrorState from '@/components/ui/ErrorState'
import Modal from '@/components/ui/Modal'
import Skeleton from '@/components/ui/Skeleton'
import Textarea from '@/components/ui/Textarea'
import { adminApi } from '@/services/adminApi'
import type { AdminDecision, AdminVerificationApplication, Page } from '@/types/api'
import { formatDate } from '@/utils/format'
import { programLabel } from '@/utils/labels'
import styles from './AdminScreen.module.css'

export default function AdminScreen() {
  const [pageData, setPageData] = useState<Page<AdminVerificationApplication> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [statusFilter, setStatusFilter] = useState<string>('PENDING')
  const [page, setPage] = useState(0)

  // Detail modal
  const [selectedApp, setSelectedApp] = useState<AdminVerificationApplication | null>(null)

  // Decision state
  const [decisionType, setDecisionType] = useState<AdminDecision | null>(null)
  const [reviewerNote, setReviewerNote] = useState('')
  const [deciding, setDeciding] = useState(false)
  const [decisionError, setDecisionError] = useState<string | null>(null)

  const loadApplications = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await adminApi.listApplications(page, 15, statusFilter || undefined)
      setPageData(res)
    } catch (e) {
      setError('Could not load verification queue. Administrator access required.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter])

  useEffect(() => {
    void loadApplications()
  }, [loadApplications])

  async function handleConfirmDecision() {
    if (!selectedApp || !decisionType) return

    if (decisionType === 'REJECTED' && !reviewerNote.trim()) {
      setDecisionError('A rejection note explaining the reason is required.')
      return
    }

    setDeciding(true)
    setDecisionError(null)
    try {
      await adminApi.decide(selectedApp.applicationId, {
        decision: decisionType,
        reviewerNote: reviewerNote.trim() || undefined,
      })
      setDecisionType(null)
      setSelectedApp(null)
      setReviewerNote('')
      await loadApplications()
    } catch (err: unknown) {
      setDecisionError(err instanceof Error ? err.message : 'Could not submit decision.')
    } finally {
      setDeciding(false)
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <div className={styles.adminBadge}>
            <ShieldAlert size={14} style={{ marginRight: 6, verticalAlign: -1 }} />
            Admin Privilege
          </div>
          <h1 className={styles.title}>Verification Review Queue</h1>
          <p className={styles.subtitle}>
            Review and decide student and alumni affiliation claims to grant access to the UBIT platform.
          </p>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className={styles.filterTabs}>
        {['PENDING', 'VERIFIED', 'REJECTED', ''].map((s) => (
          <button
            key={s}
            type="button"
            className={`${styles.filterBtn} ${statusFilter === s ? styles.activeFilter : ''}`}
            onClick={() => {
              setStatusFilter(s)
              setPage(0)
            }}
          >
            {s === 'PENDING' ? 'Pending Review' : s === 'VERIFIED' ? 'Approved' : s === 'REJECTED' ? 'Rejected' : 'All Claims'}
          </button>
        ))}
      </div>

      {/* Applications Table / Cards */}
      {error ? (
        <ErrorState message={error} onRetry={() => void loadApplications()} />
      ) : loading ? (
        <div className={styles.skeletonList}>
          {[1, 2, 3].map((i) => (
            <Card key={i} className={styles.skeletonCard}>
              <Skeleton width="30%" height="1.2rem" />
              <Skeleton width="60%" height="0.9rem" />
            </Card>
          ))}
        </div>
      ) : !pageData || pageData.content.length === 0 ? (
        <EmptyState
          title="No applications in queue"
          description={`There are currently no verification applications matching status: ${statusFilter || 'ALL'}.`}
        />
      ) : (
        <div className={styles.tableCard}>
          <div className={styles.tableResponsive}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Applicant</th>
                  <th>Type</th>
                  <th>Submitted Details</th>
                  <th>Date</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {pageData.content.map((app) => (
                  <tr key={app.applicationId} className={styles.tableRow}>
                    <td>
                      <div className={styles.applicantCell}>
                        <strong>{app.applicantName}</strong>
                        <span className={styles.emailText}>{app.applicantEmail}</span>
                      </div>
                    </td>
                    <td>
                      <Badge variant="neutral">{app.type}</Badge>
                    </td>
                    <td>
                      <div className={styles.detailsCell}>
                        {app.type === 'STUDENT' ? (
                          <>
                            <span>{programLabel(app.program)} &middot; Sem {app.currentSemester}</span>
                            <span className={styles.metaSub}>Proof: {app.documentFilename ?? 'Document'}</span>
                          </>
                        ) : (
                          <>
                            <span>Graduation: {app.passoutYear}</span>
                            {app.linkedInUrl && (
                              <a
                                href={app.linkedInUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className={styles.linkText}
                              >
                                <span>LinkedIn Profile</span>
                                <ExternalLink size={12} style={{ marginLeft: 4, verticalAlign: -1 }} />
                              </a>
                            )}
                          </>
                        )}
                      </div>
                    </td>
                    <td>
                      <span className={styles.dateText}>{formatDate(app.submittedAt)}</span>
                    </td>
                    <td>
                      <Badge
                        variant={
                          app.status === 'VERIFIED'
                            ? 'success'
                            : app.status === 'PENDING'
                            ? 'warning'
                            : 'danger'
                        }
                      >
                        {app.status}
                      </Badge>
                    </td>
                    <td>
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => setSelectedApp(app)}
                      >
                        Inspect
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {pageData.totalPages > 1 && (
            <div className={styles.pagination}>
              <Button
                variant="secondary"
                size="sm"
                disabled={pageData.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                <ChevronLeft size={16} style={{ marginRight: 4, verticalAlign: -2 }} />
                Previous
              </Button>
              <span className={styles.pageInfo}>
                Page {pageData.number + 1} of {pageData.totalPages}
              </span>
              <Button
                variant="secondary"
                size="sm"
                disabled={pageData.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
                <ChevronRight size={16} style={{ marginLeft: 4, verticalAlign: -2 }} />
              </Button>
            </div>
          )}
        </div>
      )}

      {/* Inspect & Decision Modal */}
      <Modal
        isOpen={Boolean(selectedApp)}
        onClose={() => {
          setSelectedApp(null)
          setDecisionType(null)
          setReviewerNote('')
          setDecisionError(null)
        }}
        title="Application Review"
        maxWidth="620px"
      >
        {selectedApp && (
          <div className={styles.modalContent}>
            <div className={styles.reviewHeader}>
              <div>
                <h3 className={styles.modalApplicantName}>{selectedApp.applicantName}</h3>
                <span className={styles.modalApplicantEmail}>{selectedApp.applicantEmail}</span>
              </div>
              <Badge
                variant={
                  selectedApp.status === 'VERIFIED'
                    ? 'success'
                    : selectedApp.status === 'PENDING'
                    ? 'warning'
                    : 'danger'
                }
              >
                {selectedApp.status}
              </Badge>
            </div>

            <div className={styles.infoSection}>
              <h4 className={styles.infoTitle}>Claimed Information</h4>
              <dl className={styles.detailGrid}>
                <div>
                  <dt>Claim Type</dt>
                  <dd>{selectedApp.type}</dd>
                </div>
                <div>
                  <dt>Attempt Number</dt>
                  <dd>#{selectedApp.attemptNumber}</dd>
                </div>
                {selectedApp.program && (
                  <div>
                    <dt>Program</dt>
                    <dd>{programLabel(selectedApp.program)}</dd>
                  </div>
                )}
                {selectedApp.currentSemester && (
                  <div>
                    <dt>Semester</dt>
                    <dd>Semester {selectedApp.currentSemester}</dd>
                  </div>
                )}
                {selectedApp.passoutYear && (
                  <div>
                    <dt>Pass-out Year</dt>
                    <dd>{selectedApp.passoutYear}</dd>
                  </div>
                )}
                {selectedApp.linkedInUrl && (
                  <div className={styles.fullWidthItem}>
                    <dt>LinkedIn</dt>
                    <dd>
                      <a href={selectedApp.linkedInUrl} target="_blank" rel="noopener noreferrer">
                        {selectedApp.linkedInUrl}
                      </a>
                    </dd>
                  </div>
                )}
              </dl>
            </div>

            {/* Document Preview / Download */}
            {selectedApp.documentUrl && (
              <div className={styles.docSection}>
                <h4 className={styles.infoTitle}>Verification Document</h4>
                <div className={styles.docBox}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <FileText size={16} />
                    {selectedApp.documentFilename ?? 'Document Proof'}
                  </span>
                  <a
                    href={selectedApp.documentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={styles.docLink}
                  >
                    <span>Open Document in New Tab</span>
                    <ExternalLink size={13} style={{ marginLeft: 4, verticalAlign: -1 }} />
                  </a>
                </div>
              </div>
            )}

            {/* Reviewer Note if already decided */}
            {selectedApp.reviewerNote && (
              <div className={styles.noteSection}>
                <h4 className={styles.infoTitle}>Previous Decision Note</h4>
                <p className={styles.noteText}>{selectedApp.reviewerNote}</p>
              </div>
            )}

            {/* Decision Controls (for PENDING applications) */}
            {selectedApp.status === 'PENDING' && (
              <div className={styles.decisionControls}>
                <h4 className={styles.infoTitle}>Make a Decision</h4>

                {decisionError && (
                  <div className={styles.errorBanner} role="alert">
                    {decisionError}
                  </div>
                )}

                <Textarea
                  label="Reviewer Note"
                  placeholder={
                    decisionType === 'REJECTED'
                      ? 'Explain why this application is being rejected (REQUIRED for rejections)...'
                      : 'Optional reviewer notes or approval confirmation...'
                  }
                  value={reviewerNote}
                  onChange={(e) => setReviewerNote(e.target.value)}
                  required={decisionType === 'REJECTED'}
                />

                <div className={styles.decisionButtons}>
                  <Button
                    variant="danger"
                    size="md"
                    onClick={() => {
                      setDecisionType('REJECTED')
                      if (!reviewerNote.trim()) {
                        setDecisionError('Please enter a reviewer note explaining the rejection.')
                      } else {
                        void handleConfirmDecision()
                      }
                    }}
                    isLoading={deciding && decisionType === 'REJECTED'}
                    disabled={deciding}
                  >
                    <XCircle size={16} style={{ marginRight: 6, verticalAlign: -2 }} />
                    Reject Application
                  </Button>

                  <Button
                    variant="primary"
                    size="md"
                    onClick={() => {
                      setDecisionType('VERIFIED')
                      void handleConfirmDecision()
                    }}
                    isLoading={deciding && decisionType === 'VERIFIED'}
                    disabled={deciding}
                  >
                    <CheckCircle2 size={16} style={{ marginRight: 6, verticalAlign: -2 }} />
                    Approve & Verify
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* Confirmation Dialog */}
      <ConfirmDialog
        isOpen={false}
        onClose={() => {}}
        onConfirm={() => {}}
        title="Confirm Decision"
        message="Are you sure you want to proceed with this decision?"
      />
    </div>
  )
}
