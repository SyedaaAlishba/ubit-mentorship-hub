import { useState, type FormEvent } from 'react'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import Input from '@/components/ui/Input'
import { api, ApiRequestError } from '@/services/api'
import styles from './VerificationForms.module.css'

const LINKEDIN_PROFILE = /^https:\/\/(?:[a-z]{2,3}\.)?linkedin\.com\/in\/[A-Za-z0-9_-]+\/?$/
const CURRENT_YEAR = new Date().getFullYear()

export interface AlumniVerificationFormProps {
  isResubmission: boolean
  onSubmitted: () => Promise<void>
}

export default function AlumniVerificationForm({
  isResubmission,
  onSubmitted,
}: AlumniVerificationFormProps) {
  const [passoutYear, setPassoutYear] = useState(CURRENT_YEAR)
  const [linkedInUrl, setLinkedInUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    if (!LINKEDIN_PROFILE.test(linkedInUrl.trim())) {
      setFieldErrors({
        linkedInUrl: 'Enter a valid LinkedIn profile URL (e.g. https://www.linkedin.com/in/username)',
      })
      return
    }

    setPending(true)
    try {
      await api.post('/api/verification/alumni', {
        passoutYear,
        linkedInUrl: linkedInUrl.trim(),
      })
      await onSubmitted()
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        setError(cause.message)
        const perField: Record<string, string> = {}
        for (const violation of cause.violations) {
          perField[violation.field] = violation.message
        }
        setFieldErrors(perField)
      } else {
        setError('Could not submit your application. Please check your connection and try again.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <Card className={styles.formCard}>
      <div className={styles.formHeader}>
        <h2 className={styles.formTitle}>
          {isResubmission ? 'Resubmit Alumni Verification' : 'Alumni Verification'}
        </h2>
        <p className={styles.formSubtitle}>
          Provide your graduation year and LinkedIn profile so an administrator can verify your
          alumni standing with UBIT.
        </p>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
        </div>
      )}

      <form onSubmit={(e) => void submit(e)} className={styles.form}>
        <Input
          label="Graduation / Pass-out Year"
          type="number"
          min={1970}
          max={CURRENT_YEAR + 1}
          value={passoutYear}
          onChange={(e) => setPassoutYear(Number(e.target.value))}
          error={fieldErrors.passoutYear}
          helperText="The year you completed your degree at UBIT"
        />

        <Input
          label="LinkedIn Profile URL"
          type="url"
          placeholder="https://www.linkedin.com/in/yourname"
          value={linkedInUrl}
          onChange={(e) => setLinkedInUrl(e.target.value)}
          error={fieldErrors.linkedInUrl}
          helperText="Used to confirm your professional background"
        />

        <div className={styles.formFooter}>
          <Button
            type="submit"
            variant="primary"
            size="md"
            isLoading={pending}
            disabled={pending}
          >
            {pending ? 'Submitting...' : isResubmission ? 'Resubmit Application' : 'Submit for Review'}
          </Button>
        </div>
      </form>
    </Card>
  )
}
