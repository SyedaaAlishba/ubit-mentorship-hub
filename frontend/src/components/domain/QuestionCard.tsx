import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Card from '@/components/ui/Card'
import type { QuestionResponse } from '@/types/api'
import { formatRelativeTime } from '@/utils/format'
import { categoryLabel, roleLabel } from '@/utils/labels'
import { CheckCircle2, Lock, Tag } from 'lucide-react'
import styles from './QuestionCard.module.css'

export interface QuestionCardProps {
  question: QuestionResponse
  onClick?: () => void
}

export default function QuestionCard({ question, onClick }: QuestionCardProps) {
  const isClosed = question.status === 'CLOSED'
  const hasAccepted = Boolean(question.acceptedAnswerId)

  return (
    <Card hoverable className={styles.card} onClick={onClick}>
      <div className={styles.header}>
        <div className={styles.author}>
          <Avatar name={question.author.name} size="sm" />
          <div className={styles.authorInfo}>
            <span className={styles.authorName}>{question.author.name}</span>
            <span className={styles.meta}>
              {roleLabel(question.author.role)} &middot; {formatRelativeTime(question.createdAt)}
            </span>
          </div>
        </div>

        <div className={styles.badges}>
          <Badge variant="neutral">{categoryLabel(question.category)}</Badge>
          {hasAccepted && (
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

      <h3 className={styles.title}>{question.title}</h3>
      <p className={styles.preview}>{question.body}</p>

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
  )
}
