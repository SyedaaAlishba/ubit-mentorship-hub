import { useCallback, useEffect, useState } from 'react'
import QuestionCard from '@/components/domain/QuestionCard'
import Button from '@/components/ui/Button'
import EmptyState from '@/components/ui/EmptyState'
import ErrorState from '@/components/ui/ErrorState'
import Select from '@/components/ui/Select'
import Skeleton from '@/components/ui/Skeleton'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { questionsApi } from '@/services/questionsApi'
import type { Page, QuestionCategory, QuestionResponse } from '@/types/api'
import { Plus, ChevronLeft, ChevronRight, HelpCircle } from 'lucide-react'
import styles from './QuestionsScreen.module.css'

const CATEGORY_OPTIONS: { value: string; label: string }[] = [
  { value: '', label: 'All Categories' },
  { value: 'ACADEMIC', label: 'Academic' },
  { value: 'CAREER', label: 'Career' },
  { value: 'TECHNICAL', label: 'Technical' },
  { value: 'CAMPUS', label: 'Campus' },
  { value: 'GENERAL', label: 'General' },
]

export default function QuestionsScreen() {
  const { user } = useAuth()
  const { navigate } = useNavigation()

  const [pageData, setPageData] = useState<Page<QuestionResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedCategory, setSelectedCategory] = useState<string>('')
  const [page, setPage] = useState(0)

  const loadQuestions = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await questionsApi.list({
        page,
        size: 10,
        category: selectedCategory ? (selectedCategory as QuestionCategory) : undefined,
      })
      setPageData(res)
    } catch (e) {
      setError('Could not load community questions. Please try again.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [page, selectedCategory])

  useEffect(() => {
    void loadQuestions()
  }, [loadQuestions])

  const isVerified = user?.state === 'VERIFIED' || user?.verificationStatus === 'VERIFIED'

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Community Q&A</h1>
          <p className={styles.subtitle}>
            Ask questions, get advice from seniors & alumni, and learn from shared experiences.
          </p>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.25rem' }}>
          <Button
            variant="primary"
            size="md"
            disabled={!isVerified}
            onClick={() => isVerified && navigate({ view: 'ASK_QUESTION' })}
            title={!isVerified ? 'Verification is required to post questions' : undefined}
            leftIcon={<Plus size={16} strokeWidth={2} />}
          >
            Ask a Question
          </Button>
          {!isVerified && (
            <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>
              Verification required to ask questions
            </span>
          )}
        </div>
      </div>

      <div className={styles.filterBar}>
        <div className={styles.filterGroup}>
          <Select
            value={selectedCategory}
            onChange={(e) => {
              setSelectedCategory(e.target.value)
              setPage(0)
            }}
            options={CATEGORY_OPTIONS}
            className={styles.categorySelect}
          />
        </div>
      </div>

      {error ? (
        <ErrorState message={error} onRetry={() => void loadQuestions()} />
      ) : loading ? (
        <div className={styles.list}>
          {[1, 2, 3].map((i) => (
            <div key={i} className={styles.skeletonItem}>
              <Skeleton width="60%" height="1.4rem" />
              <Skeleton width="100%" height="1rem" />
              <Skeleton width="80%" height="1rem" />
            </div>
          ))}
        </div>
      ) : !pageData || pageData.content.length === 0 ? (
        <EmptyState
          icon={<HelpCircle size={40} strokeWidth={1.5} color="var(--color-sand)" />}
          title="No questions found"
          description={
            selectedCategory
              ? `No questions found in this category. Be the first to ask!`
              : "Your community hasn't asked anything yet."
          }
          actionLabel="Ask a Question"
          onAction={() => {
            if (isVerified) navigate({ view: 'ASK_QUESTION' })
          }}
          actionDisabled={!isVerified}
          actionHint={!isVerified ? 'Only verified students can ask questions' : undefined}
        />
      ) : (
        <div className={styles.list}>
          {pageData.content.map((q) => (
            <QuestionCard
              key={q.questionId}
              question={q}
              onClick={() => navigate({ view: 'QUESTION_DETAIL', questionId: q.questionId })}
            />
          ))}

          {/* Pagination Controls */}
          {pageData.totalPages > 1 && (
            <div className={styles.pagination}>
              <Button
                variant="secondary"
                size="sm"
                disabled={pageData.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                leftIcon={<ChevronLeft size={16} strokeWidth={2} />}
              >
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
                rightIcon={<ChevronRight size={16} strokeWidth={2} />}
              >
                Next
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
