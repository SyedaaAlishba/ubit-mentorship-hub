import { useCallback, useEffect, useState, type FormEvent } from 'react'
import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import EmptyState from '@/components/ui/EmptyState'
import ErrorState from '@/components/ui/ErrorState'
import Input from '@/components/ui/Input'
import Modal from '@/components/ui/Modal'
import Skeleton from '@/components/ui/Skeleton'
import Textarea from '@/components/ui/Textarea'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { alumniApi } from '@/services/alumniApi'
import { mentorshipApi } from '@/services/mentorshipApi'
import type {
  AlumniProfileResponse,
  MentorshipRequestResponse,
  MentorshipResponse,
  Page,
} from '@/types/api'
import { formatDate } from '@/utils/format'
import { requestStatusLabel, roleLabel } from '@/utils/labels'
import {
  Handshake,
  Send,
  Inbox,
  UserRoundSearch,
  Check,
  X,
  Clock,
  User,
  Target,
} from 'lucide-react'
import styles from './MentorshipScreen.module.css'

type MentorshipTab = 'discover' | 'active' | 'received' | 'sent'

export default function MentorshipScreen() {
  const { user } = useAuth()
  const { navigate } = useNavigation()

  const [activeTab, setActiveTab] = useState<MentorshipTab>('active')

  // Data states
  const [activeList, setActiveList] = useState<Page<MentorshipResponse> | null>(null)
  const [sentList, setSentList] = useState<Page<MentorshipRequestResponse> | null>(null)
  const [receivedList, setReceivedList] = useState<Page<MentorshipRequestResponse> | null>(null)
  const [mentors, setMentors] = useState<Page<AlumniProfileResponse> | null>(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Request modal state
  const [selectedMentor, setSelectedMentor] = useState<AlumniProfileResponse | null>(null)
  const [requestMessage, setRequestMessage] = useState('')
  const [requestGoal, setRequestGoal] = useState('')
  const [submittingRequest, setSubmittingRequest] = useState(false)
  const [requestError, setRequestError] = useState<string | null>(null)

  // End mentorship confirm state
  const [endTargetId, setEndTargetId] = useState<string | null>(null)
  const [endReason, setEndReason] = useState('')
  const [ending, setEnding] = useState(false)

  const loadTabData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (activeTab === 'active') {
        const res = await mentorshipApi.listActive()
        setActiveList(res)
      } else if (activeTab === 'sent') {
        const res = await mentorshipApi.listSent()
        setSentList(res)
      } else if (activeTab === 'received') {
        const res = await mentorshipApi.listReceived()
        setReceivedList(res)
      } else if (activeTab === 'discover') {
        const res = await alumniApi.discover({ mentorshipAvailable: 'true' })
        setMentors(res)
      }
    } catch (e) {
      setError('Could not load mentorship data. Please try again.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [activeTab])

  useEffect(() => {
    void loadTabData()
  }, [loadTabData])

  const isStudent = user?.role === 'STUDENT'
  const isSeniorOrAlumni = user?.role === 'SENIOR' || user?.role === 'ALUMNI'
  const isVerified = user?.state === 'VERIFIED' || user?.verificationStatus === 'VERIFIED'

  async function handleSendRequest(e: FormEvent) {
    e.preventDefault()
    if (!selectedMentor || !requestMessage.trim()) return

    setSubmittingRequest(true)
    setRequestError(null)
    try {
      await mentorshipApi.createRequest({
        recipientUserId: selectedMentor.userId,
        message: requestMessage.trim(),
        goal: requestGoal.trim() || undefined,
      })
      setSelectedMentor(null)
      setRequestMessage('')
      setRequestGoal('')
      setActiveTab('sent')
    } catch (err: unknown) {
      setRequestError(err instanceof Error ? err.message : 'Could not send mentorship request.')
    } finally {
      setSubmittingRequest(false)
    }
  }

  async function handleAccept(requestId: string) {
    try {
      await mentorshipApi.acceptRequest(requestId)
      await loadTabData()
    } catch (e) {
      console.error('Failed to accept request', e)
    }
  }

  async function handleReject(requestId: string) {
    try {
      await mentorshipApi.rejectRequest(requestId)
      await loadTabData()
    } catch (e) {
      console.error('Failed to reject request', e)
    }
  }

  async function handleCancel(requestId: string) {
    try {
      await mentorshipApi.cancelRequest(requestId)
      await loadTabData()
    } catch (e) {
      console.error('Failed to cancel request', e)
    }
  }

  async function handleEndMentorship() {
    if (!endTargetId) return
    setEnding(true)
    try {
      await mentorshipApi.end(endTargetId, endReason.trim() || undefined)
      setEndTargetId(null)
      setEndReason('')
      await loadTabData()
    } catch (e) {
      console.error('Failed to end mentorship', e)
    } finally {
      setEnding(false)
    }
  }

  return (
    <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Mentorship Hub</h1>
          <p className={styles.subtitle}>
            Connect with experienced seniors and alumni for personalized guidance on academics,
            projects, career paths, and technical skills.
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className={styles.tabs} role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'active'}
          className={`${styles.tab} ${activeTab === 'active' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('active')}
        >
          <Handshake size={15} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '6px' }} />
          Active Mentorships
        </button>

        {isStudent && (
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'discover'}
            className={`${styles.tab} ${activeTab === 'discover' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('discover')}
          >
            <UserRoundSearch size={15} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '6px' }} />
            Find a Mentor
          </button>
        )}

        {isSeniorOrAlumni && (
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'received'}
            className={`${styles.tab} ${activeTab === 'received' ? styles.activeTab : ''}`}
            onClick={() => setActiveTab('received')}
          >
            <Inbox size={15} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '6px' }} />
            Received Requests
          </button>
        )}

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'sent'}
          className={`${styles.tab} ${activeTab === 'sent' ? styles.activeTab : ''}`}
          onClick={() => setActiveTab('sent')}
        >
          <Send size={15} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '6px' }} />
          Sent Requests
        </button>
      </div>

      {/* Tab Content */}
      {error ? (
        <ErrorState message={error} onRetry={() => void loadTabData()} />
      ) : loading ? (
        <div className={styles.list}>
          {[1, 2].map((i) => (
            <Card key={i} className={styles.skeletonCard}>
              <Skeleton width="40%" height="1.2rem" />
              <Skeleton width="90%" height="0.9rem" />
              <Skeleton width="60%" height="0.9rem" />
            </Card>
          ))}
        </div>
      ) : (
        <>
          {/* 1. Active Mentorships Tab */}
          {activeTab === 'active' && (
            <div className={styles.list}>
              {!activeList || activeList.content.length === 0 ? (
                <EmptyState
                  icon={<Handshake size={40} strokeWidth={1.5} color="var(--color-sand)" />}
                  title="No active mentorships"
                  description={
                    isStudent
                      ? 'You are not currently in any mentorship relationships. Find a mentor to start receiving guidance.'
                      : 'You do not have any active mentees at the moment.'
                  }
                  actionLabel={isStudent ? 'Find a Mentor' : undefined}
                  onAction={isStudent ? () => setActiveTab('discover') : undefined}
                />
              ) : (
                activeList.content.map((m) => {
                  const partner = isStudent ? m.mentor : m.mentee
                  const partnerRoleTitle = isStudent ? 'Mentor' : 'Mentee'

                  return (
                    <Card key={m.id} className={styles.mentorshipCard}>
                      <div className={styles.cardHeader}>
                        <div className={styles.partnerInfo}>
                          <Avatar name={partner.name} size="md" />
                          <div>
                            <span className={styles.partnerRole}>{partnerRoleTitle}</span>
                            <h3 className={styles.partnerName}>{partner.name}</h3>
                            <span className={styles.metaRole}>{roleLabel(partner.role)}</span>
                          </div>
                        </div>

                        <Badge variant="success">Active</Badge>
                      </div>

                      <div className={styles.cardBody}>
                        {m.endReason && (
                          <p className={styles.goalText}>
                            <Target size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '5px', color: 'var(--color-burgundy)' }} />
                            <strong>Note:</strong> {m.endReason}
                          </p>
                        )}
                        <span className={styles.dateMeta}>
                          <Clock size={12} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                          Started {formatDate(m.startedAt)}
                        </span>
                      </div>

                      <div className={styles.cardFooter}>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => navigate({ view: 'PUBLIC_PROFILE', userId: partner.userId })}
                          leftIcon={<User size={14} strokeWidth={2} />}
                        >
                          View Profile
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setEndTargetId(m.id)}
                          style={{ color: 'var(--color-error)' }}
                        >
                          End Mentorship
                        </Button>
                      </div>
                    </Card>
                  )
                })
              )}
            </div>
          )}

          {/* 2. Find a Mentor Tab (Students Only) */}
          {activeTab === 'discover' && (
            <div>
              {!mentors || mentors.content.length === 0 ? (
                <EmptyState
                  icon={<UserRoundSearch size={40} strokeWidth={1.5} color="var(--color-sand)" />}
                  title="No available mentors found"
                  description="Check back soon! More seniors and alumni will become available for mentorship."
                />
              ) : (
                <div className={styles.discoverGrid}>
                  {mentors.content.map((alum) => (
                    <Card key={alum.userId} className={styles.mentorItemCard}>
                      <div className={styles.partnerInfo}>
                        <Avatar name={alum.name} size="md" />
                        <div>
                          <h3 className={styles.partnerName}>{alum.name}</h3>
                          <span className={styles.metaRole}>{roleLabel(alum.role)}</span>
                          {(alum.jobTitle || alum.company) && (
                            <p className={styles.metaSub}>
                              {alum.jobTitle ?? 'Professional'} {alum.company && `@ ${alum.company}`}
                            </p>
                          )}
                        </div>
                      </div>

                      {alum.bio && <p className={styles.mentorBio}>{alum.bio}</p>}

                      {alum.skills && alum.skills.length > 0 && (
                        <div className={styles.tagList}>
                          {alum.skills.slice(0, 3).map((s) => (
                            <span key={s} className={styles.miniTag}>
                              {s}
                            </span>
                          ))}
                        </div>
                      )}

                      <div className={styles.cardFooter}>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => navigate({ view: 'PUBLIC_PROFILE', userId: alum.userId })}
                        >
                          View Profile
                        </Button>
                        <Button
                          variant="primary"
                          size="sm"
                          disabled={!isVerified}
                          onClick={() => setSelectedMentor(alum)}
                          title={!isVerified ? 'Verification required to request mentorship' : undefined}
                          leftIcon={<Send size={14} strokeWidth={2} />}
                        >
                          Request
                        </Button>
                      </div>
                    </Card>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* 3. Sent Requests Tab */}
          {activeTab === 'sent' && (
            <div className={styles.list}>
              {!sentList || sentList.content.length === 0 ? (
                <EmptyState
                  icon={<Send size={40} strokeWidth={1.5} color="var(--color-sand)" />}
                  title="No sent mentorship requests"
                  description="You have not sent any mentorship requests yet."
                  actionLabel={isStudent ? 'Find a Mentor' : undefined}
                  onAction={isStudent ? () => setActiveTab('discover') : undefined}
                />
              ) : (
                sentList.content.map((req) => {
                  const isPending = req.status === 'PENDING'

                  return (
                    <Card key={req.id} className={styles.requestCard}>
                      <div className={styles.cardHeader}>
                        <div className={styles.partnerInfo}>
                          <Avatar name={req.recipient.name} size="md" />
                          <div>
                            <span className={styles.partnerRole}>Recipient</span>
                            <h3 className={styles.partnerName}>{req.recipient.name}</h3>
                            <span className={styles.metaRole}>{roleLabel(req.recipient.role)}</span>
                          </div>
                        </div>

                        <Badge
                          variant={
                            req.status === 'ACCEPTED'
                              ? 'success'
                              : req.status === 'PENDING'
                              ? 'warning'
                              : 'neutral'
                          }
                        >
                          {requestStatusLabel(req.status)}
                        </Badge>
                      </div>

                      <div className={styles.cardBody}>
                        {req.goal && (
                          <p className={styles.goalText}>
                            <Target size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '5px', color: 'var(--color-burgundy)' }} />
                            <strong>Goal:</strong> {req.goal}
                          </p>
                        )}
                        <p className={styles.messageText}>{req.message}</p>
                        <span className={styles.dateMeta}>
                          <Clock size={12} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                          Sent {formatDate(req.createdAt)}
                        </span>
                      </div>

                      {isPending && (
                        <div className={styles.cardFooter}>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => void handleCancel(req.id)}
                            style={{ color: 'var(--color-error)' }}
                            leftIcon={<X size={14} strokeWidth={2} />}
                          >
                            Cancel Request
                          </Button>
                        </div>
                      )}
                    </Card>
                  )
                })
              )}
            </div>
          )}

          {/* 4. Received Requests Tab (Seniors & Alumni) */}
          {activeTab === 'received' && isSeniorOrAlumni && (
            <div className={styles.list}>
              {!receivedList || receivedList.content.length === 0 ? (
                <EmptyState
                  icon={<Inbox size={40} strokeWidth={1.5} color="var(--color-sand)" />}
                  title="No received requests"
                  description="You have no incoming mentorship requests at the moment."
                />
              ) : (
                receivedList.content.map((req) => {
                  const isPending = req.status === 'PENDING'

                  return (
                    <Card key={req.id} className={styles.requestCard}>
                      <div className={styles.cardHeader}>
                        <div className={styles.partnerInfo}>
                          <Avatar name={req.requester.name} size="md" />
                          <div>
                            <span className={styles.partnerRole}>Student Requester</span>
                            <h3 className={styles.partnerName}>{req.requester.name}</h3>
                            <span className={styles.metaRole}>{roleLabel(req.requester.role)}</span>
                          </div>
                        </div>

                        <Badge
                          variant={
                            req.status === 'ACCEPTED'
                              ? 'success'
                              : req.status === 'PENDING'
                              ? 'warning'
                              : 'neutral'
                          }
                        >
                          {requestStatusLabel(req.status)}
                        </Badge>
                      </div>

                      <div className={styles.cardBody}>
                        {req.goal && (
                          <p className={styles.goalText}>
                            <Target size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '5px', color: 'var(--color-burgundy)' }} />
                            <strong>Mentorship Goal:</strong> {req.goal}
                          </p>
                        )}
                        <p className={styles.messageText}>{req.message}</p>
                        <span className={styles.dateMeta}>
                          <Clock size={12} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                          Received {formatDate(req.createdAt)}
                        </span>
                      </div>

                      {isPending && (
                        <div className={styles.cardFooter}>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => navigate({ view: 'PUBLIC_PROFILE', userId: req.requester.userId })}
                            leftIcon={<User size={14} strokeWidth={2} />}
                          >
                            View Student Profile
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => void handleReject(req.id)}
                            style={{ color: 'var(--color-error)' }}
                            leftIcon={<X size={14} strokeWidth={2} />}
                          >
                            Decline
                          </Button>
                          <Button
                            variant="primary"
                            size="sm"
                            onClick={() => void handleAccept(req.id)}
                            leftIcon={<Check size={14} strokeWidth={2} />}
                          >
                            Accept Request
                          </Button>
                        </div>
                      )}
                    </Card>
                  )
                })
              )}
            </div>
          )}
        </>
      )}

      {/* Send Mentorship Request Modal */}
      <Modal
        isOpen={Boolean(selectedMentor)}
        onClose={() => setSelectedMentor(null)}
        title={selectedMentor ? `Request Mentorship from ${selectedMentor.name}` : 'Request Mentorship'}
        maxWidth="520px"
      >
        {requestError && (
          <div className={styles.errorBanner} role="alert">
            {requestError}
          </div>
        )}
        <form onSubmit={(e) => void handleSendRequest(e)} className={styles.modalForm}>
          <Input
            label="What is your primary mentorship goal? (Optional)"
            placeholder="e.g. Guidance on final year project, transition to industry, interview prep"
            value={requestGoal}
            onChange={(e) => setRequestGoal(e.target.value)}
          />

          <Textarea
            label="Message to Mentor"
            placeholder="Introduce yourself, your semester/studies, and why you are reaching out to this mentor..."
            value={requestMessage}
            onChange={(e) => setRequestMessage(e.target.value)}
            helperText="Minimum 10 characters"
            required
          />

          <div className={styles.modalFooter}>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setSelectedMentor(null)}
              disabled={submittingRequest}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={submittingRequest}
              disabled={submittingRequest || requestMessage.trim().length < 10}
              leftIcon={<Send size={15} strokeWidth={2} />}
            >
              Send Request
            </Button>
          </div>
        </form>
      </Modal>

      {/* End Mentorship Dialog */}
      <ConfirmDialog
        isOpen={Boolean(endTargetId)}
        onClose={() => setEndTargetId(null)}
        onConfirm={() => void handleEndMentorship()}
        title="End this Mentorship?"
        message="Ending this mentorship will close the active relationship. You can provide an optional reason below."
        confirmText="End Mentorship"
        isDestructive
        isLoading={ending}
      />
    </div>
  )
}
