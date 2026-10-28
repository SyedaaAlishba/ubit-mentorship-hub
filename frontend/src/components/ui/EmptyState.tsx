import type { ReactNode } from 'react'
import Button from './Button'
import styles from './EmptyState.module.css'

export interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: string
  actionLabel?: string
  onAction?: () => void
  actionDisabled?: boolean
  actionHint?: string
  className?: string
}

export default function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  actionDisabled = false,
  actionHint,
  className = '',
}: EmptyStateProps) {
  return (
    <div className={`${styles.container} ${className}`}>
      {icon && <div className={styles.icon}>{icon}</div>}
      <h3 className={styles.title}>{title}</h3>
      {description && <p className={styles.description}>{description}</p>}
      {actionLabel && onAction && (
        <>
          <Button
            variant="primary"
            onClick={onAction}
            className={styles.action}
            disabled={actionDisabled}
            title={actionDisabled && actionHint ? actionHint : undefined}
          >
            {actionLabel}
          </Button>
          {actionDisabled && actionHint && (
            <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
              {actionHint}
            </span>
          )}
        </>
      )}
    </div>
  )
}
