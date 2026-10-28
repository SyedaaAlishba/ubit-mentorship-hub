import { type SelectHTMLAttributes, forwardRef } from 'react'
import styles from './Select.module.css'

export interface SelectOption {
  value: string | number
  label: string
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  options?: SelectOption[]
  error?: string
  helperText?: string
}

const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, options, error, helperText, id, children, className = '', ...props },
  ref,
) {
  const selectId = id ?? (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined)

  return (
    <div className={`${styles.wrapper} ${className}`}>
      {label && (
        <label htmlFor={selectId} className={styles.label}>
          {label}
        </label>
      )}
      <select
        ref={ref}
        id={selectId}
        className={`${styles.select} ${error ? styles.hasError : ''}`}
        aria-invalid={Boolean(error)}
        {...props}
      >
        {options
          ? options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))
          : children}
      </select>
      {error ? (
        <span className={styles.error} role="alert">
          {error}
        </span>
      ) : helperText ? (
        <span className={styles.helper}>{helperText}</span>
      ) : null}
    </div>
  )
})

export default Select
