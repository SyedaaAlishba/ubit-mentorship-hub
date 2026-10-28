import type { ReactNode } from 'react'
import styles from './IconBox.module.css'

export type IconBoxVariant = 'burgundy' | 'sand' | 'success' | 'warning' | 'neutral'
export type IconBoxSize = 'sm' | 'md' | 'lg'

export interface IconBoxProps {
  icon: ReactNode
  variant?: IconBoxVariant
  size?: IconBoxSize
  className?: string
  ariaHidden?: boolean
}

export default function IconBox({
  icon,
  variant = 'burgundy',
  size = 'md',
  className = '',
  ariaHidden = true,
}: IconBoxProps) {
  const classes = [
    styles.box,
    styles[`variant_${variant}`],
    styles[`size_${size}`],
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={classes} aria-hidden={ariaHidden ? 'true' : undefined}>
      {icon}
    </div>
  )
}
