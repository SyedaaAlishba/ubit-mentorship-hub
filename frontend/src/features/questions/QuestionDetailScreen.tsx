import { useCallback, useEffect, useState, type FormEvent } from 'react'
import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import ConfirmDialog from '@/components/ui/ConfirmDialog'
import ErrorState from '@/components/ui/ErrorState'
import Skeleton from '@/components/ui/Skeleton'
import Textarea from '@/components/ui/Textarea'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { questionsApi } from '@/services/questionsApi'
import type { AnswerResponse, QuestionResponse } from '@/types/api'
import { formatRelativeTime } from '@/utils/format'
import { categoryLabel, roleLabel } from '@/utils/labels'
import {
  ArrowLeft,
  CheckCircle2,
  Lock,
  Tag,
  Trash2,
  Send,
  AlertCircle,
  MessageSquare,
} from 'lucide-react'
import styles from './QuestionDetailScreen.module.css'

export interface QuestionDetailScreenProps {
  questionId: string
}

export default function QuestionDetailScreen({ questionId }: QuestionDetailScreenProps) {
  const { user } = useAuth()
  const { goBack } = useNavigation()

  const [question, setQuestion] = useState<QuestionResponse | null>(null)
  const [answers, setAnswers] = useState<AnswerResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [newAnswer, setNewAnswer] = useState('')
  const [submittingAnswer, setSubmittingAnswer] = useState(false)
  const [answerError, setAnswerError] = useState<string | null>(null)

  const [closeConfirmOpen, setCloseConfirmOpen] = useState(false)
  const [closing, setClosing] = useState(false)

  const [deleteAnswerId, setDeleteAnswerId] = useState<string | null>(null)
  const [deletingAnswer, setDeletingAnswer] = useState(false)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [q, a] = await Promise.all([
        questionsApi.get(questionId),
        questionsApi.listAnswers(questionId),
      ])
      setQuestion(q)
      setAnswers(a.content)
    } catch (e) {
      setError('Could not load question details. It may have been deleted.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [questionId])

  useEffect(() => {
    void loadData()
  }, [loadData])

  async function handleAddAnswer(e: FormEvent) {
    e.preventDefault()
    if (!newAnswer.trim()) return

    setSubmittingAnswer(true)
    setAnswerError(null)
    try {
      const added = await questionsApi.createAnswer(questionId, newAnswer.trim())
      setAnswers((prev) => [...prev, added])
      setNewAnswer('')
    } catch (err: unknown) {
      setAnswerError(err instanceof Error ? err.message : 'Could not submit your answer.')
    } finally {
      setSubmittingAnswer(false)
    }
  }

  async function handleAcceptAnswer(answerId: string) {
    try {
      await questionsApi.acceptAnswer(questionId, answerId)
      await loadData()
    } catch (e) {
      console.error('Failed to accept answer', e)
    }
  }

  async function handleUnacceptAnswer(answerId: string) {
    try {
      await questionsApi.unacceptAnswer(questionId, answerId)
      await loadData()
    } catch (e) {
      console.error('Failed to unaccept answer', e)
    }
  }

  async function handleCloseQuestion() {
    setClosing(true)
    try {
      await questionsApi.close(questionId)
      setCloseConfirmOpen(false)
      await loadData()
    } catch (e) {
      console.error('Failed to close question', e)
    } finally {
      setClosing(false)
    }
  }

  async function handleDeleteAnswer() {
    if (!deleteAnswerId) return
    setDeletingAnswer(true)
    try {
      await questionsApi.deleteAnswer(deleteAnswerId)
      setAnswers((prev) => prev.filter((a) => a.answerId !== deleteAnswerId))
      setDeleteAnswerId(null)
    } catch (e) {
      console.error('Failed to delete answer', e)
    } finally {
      setDeletingAnswer(false)
    }
  }

  if (loading) {
    return (
      <div className={styles.container}>
        <Skeleton width="120px" height="2rem" />
        <Card className={styles.skeletonCard}>
          <Skeleton width="70%" height="1.8rem" />
          <Skeleton width="40%" height="1rem" />
          <Skeleton width="100%" height="4rem" />
        </Card>
      </div>
    )
  }

  if (error || !question) {
    return (
      <div className={styles.container}>
        <Button
          variant="ghost"
          size="sm"
          onClick={goBack}
          leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
        >
          Back to Questions
        </Button>
        <ErrorState message={error ?? 'Question not found.'} onRetry={() => void loadData()} />
      </div>
    )
  }

  const isAuthor = user?.userId === question.author.userId
  const isClosed = question.status === 'CLOSED'
  const isAcceptedQuestion = Boolean(question.acceptedAnswerId)

  return (
    <div className={styles.container}>
      {/* Top action bar */}
      <div className={styles.topActions}>
        <Button
          variant="ghost"
          size="sm"
          onClick={goBack}
          leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
        >
          Back to Questions
        </Button>

        {isAuthor && !isClosed && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCloseConfirmOpen(true)}
            leftIcon={<Lock size={14} strokeWidth={2} />}
          >
            Close Question
          </Button>
        )}
      </div>

      {/* Main Question Card */}
      <Card className={styles.questionCard}>
        <div className={styles.header}>
          <div className={styles.authorArea}>
            <Avatar name={question.author.name} size="md" />
            <div>
              <span className={styles.authorName}>{question.author.name}</span>
              <span className={styles.meta}>
                {roleLabel(question.author.role)} &middot; Asked {formatRelativeTime(question.createdAt)}
              </span>
            </div>
          </div>

          <div className={styles.badges}>
            <Badge variant="neutral">{categoryLabel(question.category)}</Badge>
            {isAcceptedQuestion && (
              <Badge variant="success">
                <CheckCircle2 size={12} strokeWidth={2.5} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                Answered
              </Badge>
            )}
            {isClosed && (
              <Badge variant="warning">
                <Lock size={12} strokeWidth={2.5} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                Closed
              </Badge>
            )}
          </div>
        </div>

        <h1 className={styles.title}>{question.title}</h1>
        <div className={styles.body}>{question.body}</div>

        {question.tags && question.tags.length > 0 && (
          <div className={styles.tags}>
            {question.tags.map((tag) => (
              <span key={tag} className={styles.tag}>
                <Tag size={11} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '3px' }} />
                {tag}
              </span>
            ))}
          </div>
        )}
      </Card>

      {/* Answers Section */}
      <section className={styles.answersSection}>
        <h2 className={styles.answersTitle}>
          <MessageSquare size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
          Answers ({answers.length})
        </h2>

        {answers.length === 0 ? (
          <Card className={styles.answerCard}>
            <p className="muted">No answers submitted yet. Be the first to help this student!</p>
          </Card>
        ) : (
          <div className={styles.answerList}>
            {answers.map((ans) => {
              const isAccepted = question.acceptedAnswerId === ans.answerId
              const isAnsAuthor = user?.userId === ans.author.userId

              return (
                <Card
                  key={ans.answerId}
                  className={`${styles.answerCard} ${isAccepted ? styles.acceptedCard : ''}`}
                >
                  <div className={styles.answerHeader}>
                    <div className={styles.authorArea}>
                      <Avatar name={ans.author.name} size="sm" />
                      <div>
                        <span className={styles.authorName}>{ans.author.name}</span>
                        <span className={styles.meta}>
                          {roleLabel(ans.author.role)} &middot; {formatRelativeTime(ans.createdAt)}
                        </span>
                      </div>
                    </div>

                    <div className={styles.answerActions}>
                      {isAccepted && (
                        <Badge variant="success">
                          <CheckCircle2 size={12} strokeWidth={2.5} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
                          Accepted Answer
                        </Badge>
                      )}

                      {/* Author can mark/unmark accepted answer */}
                      {isAuthor && !isClosed && (
                        isAccepted ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => void handleUnacceptAnswer(ans.answerId)}
                          >
                            Unaccept
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => void handleAcceptAnswer(ans.answerId)}
                            leftIcon={<CheckCircle2 size={14} strokeWidth={2} />}
                          >
                            Accept Answer
                          </Button>
                        )
                      )}

                      {isAnsAuthor && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDeleteAnswerId(ans.answerId)}
                          style={{ color: 'var(--color-error)' }}
                          aria-label="Delete your answer"
                          leftIcon={<Trash2 size={14} strokeWidth={2} />}
                        >
                          Delete
                        </Button>
                      )}
                    </div>
                  </div>

                  <div className={styles.answerBody}>{ans.body}</div>
                </Card>
              )
            })}
          </div>
        )}
      </section>

      {/* Answer Submission Form */}
      {!isClosed ? (
        <Card className={styles.replyCard}>
          <h3 className={styles.replyTitle}>Your Answer</h3>
          {answerError && (
            <div className={styles.errorBanner} role="alert">
              <AlertCircle size={16} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '6px' }} />
              {answerError}
            </div>
          )}
          {user?.state !== 'VERIFIED' && user?.verificationStatus !== 'VERIFIED' ? (
            <div style={{ padding: '1rem', backgroundColor: 'var(--color-surface-subtle)', borderRadius: '6px', fontSize: '0.875rem', border: '1px solid var(--color-border)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Lock size={16} strokeWidth={2} color="var(--color-text-muted)" style={{ flexShrink: 0 }} />
              <span><strong>Read-only access:</strong> Your account is currently awaiting verification. Verified student or alumni status is required to post answers.</span>
            </div>
          ) : (user?.userId && question?.author?.userId && user.userId === question.author.userId) ? (
            <div style={{ padding: '1rem', backgroundColor: 'var(--color-surface-subtle)', borderRadius: '6px', fontSize: '0.875rem', border: '1px solid var(--color-border)', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertCircle size={16} strokeWidth={2} color="var(--color-text-muted)" style={{ flexShrink: 0 }} />
              <span><strong>Question Author:</strong> You asked this question. You cannot answer your own question.</span>
            </div>
          ) : (
            <form onSubmit={(e) => void handleAddAnswer(e)} className={styles.replyForm}>
              <Textarea
                placeholder="Write a clear, helpful response to answer this question (minimum 10 characters)..."
                value={newAnswer}
                onChange={(e) => {
                  setNewAnswer(e.target.value)
                  if (answerError) setAnswerError(null)
                }}
                required
                minLength={10}
              />
              <div style={{ fontSize: '0.8rem', color: newAnswer.trim().length > 0 && newAnswer.trim().length < 10 ? '#dc2626' : 'var(--color-text-muted)', marginTop: '0.25rem' }}>
                {newAnswer.trim().length < 10 ? newAnswer.trim().length + '/10 characters required' : newAnswer.trim().length + ' characters'}
              </div>
              <div className={styles.replyFooter}>
                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  isLoading={submittingAnswer}
                  disabled={submittingAnswer || newAnswer.trim().length < 10}
                  leftIcon={<Send size={15} strokeWidth={2} />}
                >
                  Post Your Answer
                </Button>
              </div>
            </form>
          )}
        </Card>
      ) : (
        <div className={styles.closedNotice}>
          <Lock size={16} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '6px' }} />
          This question has been closed by the author and is no longer accepting new answers.
        </div>
      )}

      {/* Close Confirm Dialog */}
      <ConfirmDialog
        isOpen={closeConfirmOpen}
        onClose={() => setCloseConfirmOpen(false)}
        onConfirm={() => void handleCloseQuestion()}
        title="Close this Question?"
        message="Closing this question means no new answers can be submitted. This action cannot be reversed."
        confirmText="Close Question"
        isLoading={closing}
      />

      {/* Delete Answer Confirm Dialog */}
      <ConfirmDialog
        isOpen={Boolean(deleteAnswerId)}
        onClose={() => setDeleteAnswerId(null)}
        onConfirm={() => void handleDeleteAnswer()}
        title="Delete your Answer?"
        message="Are you sure you want to delete this answer? It will be removed permanently."
        confirmText="Delete Answer"
        isDestructive
        isLoading={deletingAnswer}
      />
    </div>
  )
}
