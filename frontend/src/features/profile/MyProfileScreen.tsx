import { useCallback, useEffect, useState } from 'react'
import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import ErrorState from '@/components/ui/ErrorState'
import Skeleton from '@/components/ui/Skeleton'
import { useNavigation } from '@/context/navigationContext'
import { profileApi } from '@/services/profileApi'
import type { ProfileResponse } from '@/types/api'
import { contactLabel, programLabel, roleLabel, statusLabel, visibilityLabel } from '@/utils/labels'
import {
  Pencil,
  GraduationCap,
  Briefcase,
  Link as LinkIcon,
  Globe,
  MapPin,
  Eye,
  Mail,
  ExternalLink,
} from 'lucide-react'
import styles from './ProfileScreens.module.css'

export default function MyProfileScreen() {
  const { navigate } = useNavigation()

  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadProfile = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await profileApi.getMe()
      setProfile(data)
    } catch (e) {
      setError('Could not load your profile. Please try again.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadProfile()
  }, [loadProfile])

  if (loading) {
    return (
      <div className={styles.container}>
        <Card className={styles.profileHeaderCard}>
          <Skeleton width="72px" height="72px" borderRadius="50%" />
          <Skeleton width="40%" height="1.6rem" />
          <Skeleton width="20%" height="1rem" />
        </Card>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className={styles.container}>
        <ErrorState message={error ?? 'Profile not found.'} onRetry={() => void loadProfile()} />
      </div>
    )
  }

  return (
    <div className={styles.container}>
      {/* Profile Header */}
      <Card className={styles.profileHeaderCard}>
        <div className={styles.headerTop}>
          <Avatar name={profile.name} size="xl" />
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate({ view: 'EDIT_PROFILE' })}
            leftIcon={<Pencil size={14} strokeWidth={2} />}
          >
            Edit Profile
          </Button>
        </div>

        <div className={styles.headerInfo}>
          <h1 className={styles.name}>{profile.name}</h1>
          <div className={styles.badgeRow}>
            <Badge variant="burgundy">{roleLabel(profile.role)}</Badge>
            <Badge variant={profile.verificationStatus === 'VERIFIED' ? 'success' : 'warning'}>
              {statusLabel(profile.verificationStatus)}
            </Badge>
            {profile.mentorshipAvailable && (
              <Badge variant="success">Available for Mentorship</Badge>
            )}
          </div>

          {(profile.jobTitle || profile.company) && (
            <p className={styles.jobLine}>
              <Briefcase size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '5px', color: 'var(--color-text-muted)' }} />
              <strong>{profile.jobTitle ?? 'Professional'}</strong>
              {profile.company && <span> at {profile.company}</span>}
            </p>
          )}

          {profile.bio && <p className={styles.bio}>{profile.bio}</p>}
        </div>
      </Card>

      {/* Profile Sections Grid */}
      <div className={styles.sectionsGrid}>
        {/* Academic Details */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <GraduationCap size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Academic Information
          </h2>
          <dl className={styles.detailsList}>
            {profile.program && (
              <div className={styles.detailRow}>
                <dt>Program</dt>
                <dd>{programLabel(profile.program)}</dd>
              </div>
            )}
            {profile.currentSemester !== null && (
              <div className={styles.detailRow}>
                <dt>Current Semester</dt>
                <dd>Semester {profile.currentSemester}</dd>
              </div>
            )}
            {profile.passoutYear !== null && (
              <div className={styles.detailRow}>
                <dt>Graduation Year</dt>
                <dd>{profile.passoutYear}</dd>
              </div>
            )}
            <div className={styles.detailRow}>
              <dt>Account Email</dt>
              <dd>{profile.email}</dd>
            </div>
          </dl>
        </Card>

        {/* Professional & Skills */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <Briefcase size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Professional & Skills
          </h2>

          {profile.skills && profile.skills.length > 0 && (
            <div className={styles.tagGroup}>
              <span className={styles.groupLabel}>Skills</span>
              <div className={styles.tags}>
                {profile.skills.map((s) => (
                  <span key={s} className={styles.tag}>
                    {s}
                  </span>
                ))}
              </div>
            </div>
          )}

          {profile.technologies && profile.technologies.length > 0 && (
            <div className={styles.tagGroup}>
              <span className={styles.groupLabel}>Technologies</span>
              <div className={styles.tags}>
                {profile.technologies.map((t) => (
                  <span key={t} className={styles.tag}>
                    {t}
                  </span>
                ))}
              </div>
            </div>
          )}

          {profile.workArea && (
            <dl className={styles.detailsList}>
              <div className={styles.detailRow}>
                <dt>Work Area</dt>
                <dd>{profile.workArea}</dd>
              </div>
            </dl>
          )}

          {profile.interests && (
            <div className={styles.tagGroup}>
              <span className={styles.groupLabel}>Interests</span>
              <p className={styles.interestsText}>{profile.interests}</p>
            </div>
          )}
        </Card>

        {/* Links & Visibility */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <LinkIcon size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Links & Privacy
          </h2>
          <dl className={styles.detailsList}>
            {profile.linkedinUrl && (
              <div className={styles.detailRow}>
                <dt>LinkedIn</dt>
                <dd>
                  <a href={profile.linkedinUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    {profile.linkedinUrl}
                    <ExternalLink size={12} strokeWidth={2} />
                  </a>
                </dd>
              </div>
            )}
            {profile.githubUrl && (
              <div className={styles.detailRow}>
                <dt>GitHub</dt>
                <dd>
                  <a href={profile.githubUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    {profile.githubUrl}
                    <ExternalLink size={12} strokeWidth={2} />
                  </a>
                </dd>
              </div>
            )}
            {profile.websiteUrl && (
              <div className={styles.detailRow}>
                <dt>Website</dt>
                <dd>
                  <a href={profile.websiteUrl} target="_blank" rel="noopener noreferrer" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                    <Globe size={13} strokeWidth={2} />
                    {profile.websiteUrl}
                    <ExternalLink size={12} strokeWidth={2} />
                  </a>
                </dd>
              </div>
            )}
            {profile.location && (
              <div className={styles.detailRow}>
                <dt>Location</dt>
                <dd style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <MapPin size={13} strokeWidth={2} color="var(--color-text-muted)" />
                  {profile.location}
                </dd>
              </div>
            )}
            <div className={styles.detailRow}>
              <dt>Visibility</dt>
              <dd style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Eye size={13} strokeWidth={2} color="var(--color-text-muted)" />
                {visibilityLabel(profile.visibility)}
              </dd>
            </div>
            <div className={styles.detailRow}>
              <dt>Preferred Contact</dt>
              <dd style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Mail size={13} strokeWidth={2} color="var(--color-text-muted)" />
                {contactLabel(profile.preferredContact)}
              </dd>
            </div>
          </dl>
        </Card>
      </div>
    </div>
  )
}
