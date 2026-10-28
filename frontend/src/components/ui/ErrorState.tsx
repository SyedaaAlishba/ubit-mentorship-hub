import Button from './Button'
import { AlertCircle } from 'lucide-react'
import styles from './ErrorState.module.css'

export interface ErrorStateProps {
  title?: string
  message?: string
  onRetry?: () => void
  className?: string
}

export default function ErrorState({
  title = 'Something went wrong',
  message = 'An unexpected error occurred while loading this content. Please try again.',
  onRetry,
  className = '',
}: ErrorStateProps) {
  return (
    <div className={`${styles.container} ${className}`} role="alert">
      <div className={styles.icon}>
        <AlertCircle size={36} strokeWidth={1.75} />
      </div>
      <h3 className={styles.title}>{title}</h3>
      <p className={styles.message}>{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" onClick={onRetry} className={styles.retryButton}>
          Try Again
        </Button>
      )}
    </div>
  )
}
