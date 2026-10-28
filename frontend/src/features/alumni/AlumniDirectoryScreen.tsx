import { useCallback, useEffect, useState } from 'react'
import AlumniCard from '@/components/domain/AlumniCard'
import Button from '@/components/ui/Button'
import EmptyState from '@/components/ui/EmptyState'
import ErrorState from '@/components/ui/ErrorState'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Skeleton from '@/components/ui/Skeleton'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { alumniApi } from '@/services/alumniApi'
import type { AlumniProfileResponse, Page } from '@/types/api'
import { Search, ChevronLeft, ChevronRight, UsersRound } from 'lucide-react'
import styles from './AlumniDirectoryScreen.module.css'

const ROLE_OPTIONS = [
  { value: '', label: 'All Roles' },
  { value: 'ALUMNI', label: 'Alumni' },
  { value: 'SENIOR', label: 'Senior Students' },
]

const PROGRAM_OPTIONS = [
  { value: '', label: 'All Programs' },
  { value: 'CS', label: 'Computer Science' },
  { value: 'SE', label: 'Software Engineering' },
  { value: 'AI', label: 'Artificial Intelligence' },
]

export default function AlumniDirectoryScreen() {
  const { user } = useAuth()
  const { navigate } = useNavigation()

  const [pageData, setPageData] = useState<Page<AlumniProfileResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [role, setRole] = useState('')
  const [program, setProgram] = useState('')
  const [searchName, setSearchName] = useState('')
  const [onlyMentors, setOnlyMentors] = useState(false)
  const [page, setPage] = useState(0)

  const loadAlumni = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await alumniApi.discover({
        page,
        size: 9,
        role: role || undefined,
        program: program || undefined,
        name: searchName.trim() || undefined,
        mentorshipAvailable: onlyMentors ? 'true' : undefined,
      })
      setPageData(res)
    } catch (e) {
      setError('Could not load alumni profiles. Please check your connection and try again.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [page, role, program, searchName, onlyMentors])

  useEffect(() => {
    void loadAlumni()
  }, [loadAlumni])

  const canRequestMentorship = user?.role === 'STUDENT'

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Alumni & Senior Directory</h1>
          <p className={styles.subtitle}>
            Explore profiles of verified graduates and senior students from UBIT. Connect for professional
            networking and mentorship.
          </p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className={styles.filterCard}>
        <div className={styles.filterGrid}>
          <div className={styles.searchWrapper}>
            <Search size={16} strokeWidth={2} className={styles.searchIcon} />
            <Input
              placeholder="Search by name..."
              value={searchName}
              onChange={(e) => {
                setSearchName(e.target.value)
                setPage(0)
              }}
              className={styles.searchInput}
            />
          </div>

          <Select
            value={role}
            onChange={(e) => {
              setRole(e.target.value)
              setPage(0)
            }}
            options={ROLE_OPTIONS}
          />

          <Select
            value={program}
            onChange={(e) => {
              setProgram(e.target.value)
              setPage(0)
            }}
            options={PROGRAM_OPTIONS}
          />

          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={onlyMentors}
              onChange={(e) => {
                setOnlyMentors(e.target.checked)
                setPage(0)
              }}
              className={styles.checkbox}
            />
            <span>Available to Mentor</span>
          </label>
        </div>
      </div>

      {/* Results */}
      {error ? (
        <ErrorState message={error} onRetry={() => void loadAlumni()} />
      ) : loading ? (
        <div className={styles.grid}>
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div key={i} className={styles.skeletonItem}>
              <Skeleton width="48px" height="48px" borderRadius="50%" />
              <Skeleton width="70%" height="1.2rem" />
              <Skeleton width="40%" height="0.8rem" />
              <Skeleton width="100%" height="3rem" />
            </div>
          ))}
        </div>
      ) : !pageData || pageData.content.length === 0 ? (
        <EmptyState
          icon={<UsersRound size={40} strokeWidth={1.5} color="var(--color-sand)" />}
          title="No profiles found"
          description="Try adjusting your search criteria or clearing filters to see more members."
          actionLabel="Clear Filters"
          onAction={() => {
            setRole('')
            setProgram('')
            setSearchName('')
            setOnlyMentors(false)
            setPage(0)
          }}
        />
      ) : (
        <>
          <div className={styles.grid}>
            {pageData.content.map((profile) => (
              <AlumniCard
                key={profile.userId}
                profile={profile}
                onViewProfile={() => navigate({ view: 'PUBLIC_PROFILE', userId: profile.userId })}
                canRequestMentorship={canRequestMentorship}
                onRequestMentorship={() => navigate({ view: 'MENTORSHIP' })}
              />
            ))}
          </div>

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
        </>
      )}
    </div>
  )
}
