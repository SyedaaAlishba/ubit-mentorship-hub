import { getInitials } from '@/utils/format'
import styles from './Avatar.module.css'

export type AvatarSize = 'sm' | 'md' | 'lg' | 'xl'

export interface AvatarProps {
  name?: string | null
  src?: string | null
  size?: AvatarSize
  className?: string
}

export default function Avatar({ name, src, size = 'md', className = '' }: AvatarProps) {
  const initials = getInitials(name)

  return (
    <div className={`${styles.avatar} ${styles[size]} ${className}`} aria-label={name ?? 'User Avatar'}>
      {src ? (
        <img src={src} alt={name ?? 'Avatar'} className={styles.image} />
      ) : (
        <span className={styles.initials}>{initials}</span>
      )}
    </div>
  )
}
