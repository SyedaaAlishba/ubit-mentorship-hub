import { useEffect, useRef, useState } from 'react'
import AlumniCard from '@/components/domain/AlumniCard'
import QuestionCard from '@/components/domain/QuestionCard'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import EmptyState from '@/components/ui/EmptyState'
import IconBox from '@/components/ui/IconBox'
import Skeleton from '@/components/ui/Skeleton'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { alumniApi } from '@/services/alumniApi'
import { mentorshipApi } from '@/services/mentorshipApi'
import { questionsApi } from '@/services/questionsApi'
import { ragApi } from '@/services/ragApi'
import type { RagQueryResponse } from '@/services/ragApi'
import type { AlumniProfileResponse, MentorshipResponse, QuestionResponse } from '@/types/api'
import {
  HelpCircle,
  UsersRound,
  UserRoundSearch,
  Sparkles,
  Send,
  ArrowRight,
  Handshake,
  MessagesSquare,
  GraduationCap,
} from 'lucide-react'
import styles from './DashboardScreen.module.css'

const RECOMMENDED_QUESTIONS = [
  'What is the minimum attendance required to sit in a final examination?',
  'How do I decide which field to choose: AI, Data Science, Cybersecurity, or Web Development?',
  'What career opportunities are available for computer science graduates in Pakistan and abroad?',
  'Which industry skills should I learn beyond my university syllabus?',
  'How can I determine whether I am ready to apply for an internship?',
  'How should I prepare for a technical interview as a computer science student?',
]

export default function DashboardScreen() {
  const { user } = useAuth()
  const { navigate } = useNavigation()

  const [questions, setQuestions] = useState<QuestionResponse[]>([])
  const [loadingQuestions, setLoadingQuestions] = useState(true)

  const [alumni, setAlumni] = useState<AlumniProfileResponse[]>([])
  const [loadingAlumni, setLoadingAlumni] = useState(true)

  const [activeMentorships, setActiveMentorships] = useState<MentorshipResponse[]>([])

  // AI Assistant state
  const [aiQuestion, setAiQuestion] = useState('')
  const [aiLoading, setAiLoading] = useState(false)
  const [aiResult, setAiResult] = useState<RagQueryResponse | null>(null)
  const [aiError, setAiError] = useState<string | null>(null)
  const aiInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let active = true

    // Fetch recent questions
    void (async () => {
      try {
        const res = await questionsApi.list({ size: 4 })
        if (active) setQuestions(res.content)
      } catch (e) {
        console.error('Failed to load dashboard questions', e)
      } finally {
        if (active) setLoadingQuestions(false)
      }
    })()

    // Fetch alumni profiles
    void (async () => {
      try {
        const res = await alumniApi.discover({ size: 3 })
        if (active) setAlumni(res.content)
      } catch (e) {
        console.error('Failed to load dashboard alumni', e)
      } finally {
        if (active) setLoadingAlumni(false)
      }
    })()

    // Fetch active mentorships
    void (async () => {
      try {
        const res = await mentorshipApi.listActive(0, 3)
        if (active) setActiveMentorships(res.content)
      } catch (e) {
        console.error('Failed to load active mentorships', e)
      }
    })()

    return () => {
      active = false
    }
  }, [])

  const handleAiAsk = async (question: string) => {
    const q = question.trim()
    if (!q || aiLoading) return
    setAiQuestion(q)
    setAiLoading(true)
    setAiResult(null)
    setAiError(null)
    try {
      const res = await ragApi.ask(q)
      setAiResult(res)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Something went wrong. Please try again.'
      setAiError(msg)
    } finally {
      setAiLoading(false)
    }
  }

  const handleAiSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    void handleAiAsk(aiQuestion)
  }

  const handleChipClick = (chipText: string) => {
    // Strip surrounding quotes
    const clean = chipText.replace(/^"(.*)"$/, '$1')
    setAiQuestion(clean)
    setAiResult(null)
    setAiError(null)
    aiInputRef.current?.focus()
    void handleAiAsk(clean)
  }

  const isStudent = user?.role === 'STUDENT'
  const isVerified = user?.verificationStatus === 'VERIFIED'

  return (
    <div className={styles.container}>
      {/* 1. Hero Section */}
      <section className={styles.hero}>
        <div className={styles.heroOverlay}>
          <div className={styles.heroContent}>
            <div className={styles.heroBadge}>
              <GraduationCap size={15} strokeWidth={2} className={styles.heroBadgeIcon} />
              <span>UBIT DEPARTMENT &middot; UNIVERSITY OF KARACHI</span>
            </div>
            <h1 className={styles.heroTitle}>Student Mentorship & Alumni Hub</h1>
            <p className={styles.heroText}>
              Connect with your university community, learn from experienced seniors and alumni,
              and find the guidance you need to grow your academic and professional career.
            </p>
          </div>
        </div>
      </section>

      {/* 2. Quick Action Cards */}
      <section className={styles.quickActions}>
        {/* Ask Question action - only for verified users */}
        <Card
          hoverable={isVerified}
          className={`${styles.actionCard} ${!isVerified ? styles.actionCardDisabled : ''}`}
          onClick={() => {
            if (isVerified) navigate({ view: 'ASK_QUESTION' })
          }}
          title={!isVerified ? 'Only verified students can ask questions' : undefined}
          aria-disabled={!isVerified || undefined}
        >
          <IconBox
            icon={<HelpCircle size={22} strokeWidth={2} />}
            variant="burgundy"
            size="md"
          />
          <div className={styles.actionBody}>
            <h3 className={styles.actionTitle}>Ask a Question</h3>
            <p className={styles.actionDesc}>
              {isVerified
                ? 'Ask the UBIT community for academic, technical, or career guidance.'
                : 'Only verified students can ask questions.'}
            </p>
          </div>
        </Card>

        <Card hoverable className={styles.actionCard} onClick={() => navigate({ view: 'ALUMNI_DIRECTORY' })}>
          <IconBox
            icon={<UsersRound size={22} strokeWidth={2} />}
            variant="sand"
            size="md"
          />
          <div className={styles.actionBody}>
            <h3 className={styles.actionTitle}>Explore Alumni</h3>
            <p className={styles.actionDesc}>Discover verified alumni, their career journeys, and technologies they work with.</p>
          </div>
        </Card>

        <Card hoverable className={styles.actionCard} onClick={() => navigate({ view: 'MENTORSHIP' })}>
          <IconBox
            icon={<UserRoundSearch size={22} strokeWidth={2} />}
            variant="burgundy"
            size="md"
          />
          <div className={styles.actionBody}>
            <h3 className={styles.actionTitle}>Find a Mentor</h3>
            <p className={styles.actionDesc}>Connect with eligible senior students and alumni for one-on-one mentorship.</p>
          </div>
        </Card>
      </section>

      {/* 3. AI Assistant Area (integrated interface) */}
      <section className={styles.aiSection}>
        <Card className={styles.aiCard}>
          <div className={styles.aiHeader}>
            <div className={styles.aiBadge}>
              <IconBox
                icon={<Sparkles size={16} strokeWidth={2} />}
                variant="burgundy"
                size="sm"
              />
              <span>UBIT AI Assistant</span>
            </div>
            <span className={styles.aiStatus}>Knowledge Base Ready</span>
          </div>
          <h2 className={styles.aiTitle}>Ask the UBIT Assistant</h2>
          <p className={styles.aiDesc}>
            Get instant answers regarding university rules, course curricula, exam schedules, and department guidelines.
          </p>

          {/* Search form */}
          <form onSubmit={handleAiSubmit} className={styles.aiSearchWrapper}>
            <input
              ref={aiInputRef}
              type="text"
              value={aiQuestion}
              onChange={(e) => setAiQuestion(e.target.value)}
              placeholder="Ask about UBIT rules, course requirements, semester schedules, or FAQs..."
              className={styles.aiInput}
              disabled={aiLoading}
              maxLength={1000}
              aria-label="Ask UBIT AI assistant"
            />
            <Button
              variant="primary"
              size="md"
              type="submit"
              disabled={aiLoading || !aiQuestion.trim()}
              leftIcon={<Send size={15} strokeWidth={2} />}
            >
              {aiLoading ? 'Asking…' : 'Ask'}
            </Button>
          </form>

          {/* Suggested chips */}
          {!aiResult && !aiLoading && (
            <div className={styles.aiPrompts}>
              <span className={styles.promptLabel}>
                <HelpCircle size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '4px' }} />
                Suggested queries:
              </span>
              {RECOMMENDED_QUESTIONS.map((q) => (
                <button
                  key={q}
                  type="button"
                  className={styles.promptChip}
                  onClick={() => handleChipClick(q)}
                >
                  "{q}"
                </button>
              ))}
            </div>
          )}

          {/* Loading state */}
          {aiLoading && (
            <div className={styles.aiResultBox}>
              <div className={styles.aiResultSkeleton}>
                <Skeleton width="80%" height="1rem" />
                <Skeleton width="95%" height="0.9rem" />
                <Skeleton width="70%" height="0.9rem" />
              </div>
            </div>
          )}

          {/* Error state */}
          {aiError && !aiLoading && (
            <div className={styles.aiResultBox}>
              <p className={styles.aiResultError}>{aiError}</p>
              <button
                type="button"
                className={styles.aiRetryBtn}
                onClick={() => { setAiError(null); setAiResult(null) }}
              >
                Clear
              </button>
            </div>
          )}

          {/* Result state */}
          {aiResult && !aiLoading && (
            <div className={styles.aiResultBox}>
              <div className={styles.aiResultHeader}>
                <span className={styles.aiResultLabel}>
                  {aiResult.grounded ? 'UBIT Assistant' : 'UBIT Assistant'}
                </span>
              </div>
              <p className={styles.aiResultAnswer}>{aiResult.answer}</p>

              <button
                type="button"
                className={styles.aiClearBtn}
                onClick={() => { setAiResult(null); setAiQuestion(''); setAiError(null) }}
              >
                Ask another question
              </button>
            </div>
          )}
        </Card>
      </section>

      {/* 4. Active Mentorship Overview (if any) */}
      {activeMentorships.length > 0 && (
        <section className={styles.section}>
          <div className={styles.sectionHeader}>
            <div>
              <h2 className={styles.sectionTitle}>
                <Handshake size={20} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
                Active Mentorships
              </h2>
              <p className={styles.sectionSubtitle}>Your ongoing guidance relationships</p>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => navigate({ view: 'MENTORSHIP' })}
              rightIcon={<ArrowRight size={14} strokeWidth={2} />}
            >
              View All Mentorships
            </Button>
          </div>
          <div className={styles.mentorshipGrid}>
            {activeMentorships.map((m) => (
              <Card key={m.id} className={styles.mentorshipCard}>
                <div className={styles.mentorshipRole}>{isStudent ? 'Your Mentor' : 'Your Mentee'}</div>
                <h3 className={styles.mentorshipName}>{isStudent ? m.mentor.name : m.mentee.name}</h3>
                <p className={styles.mentorshipMeta}>
                  Status: <strong>Active</strong> &middot; Started{' '}{new Date(m.startedAt).toLocaleDateString()}
                </p>
              </Card>
            ))}
          </div>
        </section>
      )}

      {/* 5. Recent Community Questions */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <div>
            <h2 className={styles.sectionTitle}>
              <MessagesSquare size={20} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
              Recent Community Questions
            </h2>
            <p className={styles.sectionSubtitle}>Discussions and inquiries from UBIT students</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate({ view: 'QUESTIONS' })}
            rightIcon={<ArrowRight size={14} strokeWidth={2} />}
          >
            View All Questions
          </Button>
        </div>
        {loadingQuestions ? (
          <div className={styles.questionList}>
            {[1, 2].map((i) => (
              <Card key={i} className={styles.skeletonCard}>
                <Skeleton width="40%" height="1.2rem" />
                <Skeleton width="90%" height="0.9rem" />
                <Skeleton width="70%" height="0.9rem" />
              </Card>
            ))}
          </div>
        ) : questions.length === 0 ? (
          <EmptyState
            title="No questions asked yet"
            description="Be the first to start a conversation in the UBIT community."
            actionLabel="Ask a Question"
            onAction={() => {
              if (isVerified) navigate({ view: 'ASK_QUESTION' })
            }}
            actionDisabled={!isVerified}
            actionHint={!isVerified ? 'Only verified students can ask questions' : undefined}
          />
        ) : (
          <div className={styles.questionList}>
            {questions.map((q) => (
              <QuestionCard
                key={q.questionId}
                question={q}
                onClick={() => navigate({ view: 'QUESTION_DETAIL', questionId: q.questionId })}
              />
            ))}
          </div>
        )}
      </section>

      {/* 6. Discover Alumni Section */}
      <section className={styles.section}>
        <div className={styles.sectionHeader}>
          <div>
            <h2 className={styles.sectionTitle}>
              <UsersRound size={20} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
              Explore Alumni
            </h2>
            <p className={styles.sectionSubtitle}>Connect with verified graduates from UBIT</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate({ view: 'ALUMNI_DIRECTORY' })}
            rightIcon={<ArrowRight size={14} strokeWidth={2} />}
          >
            Explore Directory
          </Button>
        </div>
        {loadingAlumni ? (
          <div className={styles.alumniGrid}>
            {[1, 2, 3].map((i) => (
              <Card key={i} className={styles.skeletonCard}>
                <Skeleton width="60%" height="1.2rem" />
                <Skeleton width="40%" height="0.8rem" />
                <Skeleton width="100%" height="2rem" />
              </Card>
            ))}
          </div>
        ) : alumni.length === 0 ? (
          <EmptyState
            title="No alumni registered yet"
            description="Verified alumni will appear here once they complete their profiles."
          />
        ) : (
          <div className={styles.alumniGrid}>
            {alumni.map((alum) => (
              <AlumniCard
                key={alum.userId}
                profile={alum}
                onViewProfile={() => navigate({ view: 'PUBLIC_PROFILE', userId: alum.userId })}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
