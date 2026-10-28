import { useRef, useState, type FormEvent } from 'react'
import { UploadCloud, FileText } from 'lucide-react'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import { api, ApiRequestError } from '@/services/api'
import type { Program, StudentDocumentType } from '@/types/api'
import styles from './VerificationForms.module.css'

const PROGRAMS: { value: Program; label: string }[] = [
  { value: 'CS', label: 'Computer Science' },
  { value: 'SE', label: 'Software Engineering' },
  { value: 'AI', label: 'Artificial Intelligence' },
]

const DOCUMENT_TYPES: { value: StudentDocumentType; label: string }[] = [
  { value: 'STUDENT_CARD', label: 'Student Card' },
  { value: 'ENROLLMENT_SLIP', label: 'Enrollment Slip' },
]

const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED = '.jpg,.jpeg,.png,.webp,.pdf'
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'pdf']

export interface StudentVerificationFormProps {
  isResubmission: boolean
  onSubmitted: () => Promise<void>
}

export default function StudentVerificationForm({
  isResubmission,
  onSubmitted,
}: StudentVerificationFormProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [program, setProgram] = useState<Program>('CS')
  const [currentSemester, setCurrentSemester] = useState(1)
  const [documentType, setDocumentType] = useState<StudentDocumentType>('STUDENT_CARD')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)

  function handleFileChange(selectedFile: File | null) {
    setError(null)
    if (!selectedFile) {
      setFile(null)
      return
    }

    const extension = selectedFile.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      setError('Unsupported file type. Please upload a JPG, PNG, WebP, or PDF document.')
      setFile(null)
      if (fileInput.current) fileInput.current.value = ''
      return
    }

    if (selectedFile.size > MAX_BYTES) {
      setError('That file is larger than 5 MB. Please upload a document 5 MB or smaller.')
      setFile(null)
      if (fileInput.current) fileInput.current.value = ''
      return
    }

    setFile(selectedFile)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    if (!file) {
      setError('Please choose a document to upload.')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('That file is larger than 5 MB. Please upload a smaller file.')
      return
    }

    setPending(true)
    try {
      const form = new FormData()
      form.append('program', program)
      form.append('currentSemester', String(currentSemester))
      form.append('documentType', documentType)
      form.append('document', file)
      await api.postForm('/api/verification/student', form)
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
          {isResubmission ? 'Resubmit Student Verification' : 'Student Verification'}
        </h2>
        <p className={styles.formSubtitle}>
          Upload proof of enrollment to verify your status as a current UBIT student.
          An administrator will review your submission.
        </p>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
        </div>
      )}

      <form onSubmit={(e) => void submit(e)} className={styles.form}>
        <Select
          label="Academic Program"
          value={program}
          onChange={(e) => setProgram(e.target.value as Program)}
          error={fieldErrors.program}
          options={PROGRAMS}
        />

        <Input
          label="Current Semester (1-8)"
          type="number"
          min={1}
          max={8}
          value={currentSemester}
          onChange={(e) => setCurrentSemester(Number(e.target.value))}
          error={fieldErrors.currentSemester}
        />

        <Select
          label="Document Type"
          value={documentType}
          onChange={(e) => setDocumentType(e.target.value as StudentDocumentType)}
          options={DOCUMENT_TYPES}
        />

        <div className={styles.fileUploadArea}>
          <label className={styles.fileLabel}>
            Verification Document
            <span className={styles.fileHint}>JPG, PNG, WebP or PDF, up to 5 MB</span>
          </label>
          <div
            className={styles.dropZone}
            onClick={() => fileInput.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => e.key === 'Enter' && fileInput.current?.click()}
          >
            <input
              ref={fileInput}
              type="file"
              accept={ACCEPTED}
              onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
              className={styles.hiddenFileInput}
            />
            <span className={styles.uploadIcon}>
              {file ? <FileText size={32} /> : <UploadCloud size={32} />}
            </span>
            {file ? (
              <div className={styles.fileName}>
                <strong>Selected:</strong> {file.name} ({(file.size / (1024 * 1024)).toFixed(2)} MB)
              </div>
            ) : (
              <div className={styles.uploadPrompt}>
                <span>Click to browse file</span>
                <span className={styles.uploadHelp}>or drag and drop here</span>
              </div>
            )}
          </div>
        </div>

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
