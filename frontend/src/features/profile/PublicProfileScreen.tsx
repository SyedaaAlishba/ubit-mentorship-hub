import { useCallback, useEffect, useState } from 'react'
import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import ErrorState from '@/components/ui/ErrorState'
import Skeleton from '@/components/ui/Skeleton'
import { useAuth } from '@/context/authContext'
import { useNavigation } from '@/context/navigationContext'
import { profileApi } from '@/services/profileApi'
import type { ProfilePublicResponse } from '@/types/api'
import { contactLabel } from '@/utils/labels'
import {
  ArrowLeft,
  Briefcase,
  Link as LinkIcon,
  Globe,
  MapPin,
  Mail,
  Send,
  ExternalLink,
} from 'lucide-react'
import styles from './ProfileScreens.module.css'

export interface PublicProfileScreenProps {
  userId: string
}

export default function PublicProfileScreen({ userId }: PublicProfileScreenProps) {
  const { user } = useAuth()
  const { navigate, goBack } = useNavigation()

  const [profile, setProfile] = useState<ProfilePublicResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await profileApi.getPublic(userId)
      setProfile(data)
    } catch (e) {
      setError('Could not view this profile. It may be private or unverified.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    void loadData()
  }, [loadData])

  if (loading) {
    return (
      <div className={styles.container}>
        <Skeleton width="100px" height="2rem" />
        <Card className={styles.profileHeaderCard}>
          <Skeleton width="72px" height="72px" borderRadius="50%" />
          <Skeleton width="50%" height="1.6rem" />
          <Skeleton width="30%" height="1rem" />
        </Card>
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className={styles.container}>
        <Button
          variant="ghost"
          size="sm"
          onClick={goBack}
          leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
        >
          Back
        </Button>
        <ErrorState message={error ?? 'Profile not found.'} onRetry={() => void loadData()} />
      </div>
    )
  }

  const isVerified = user?.state === 'VERIFIED' || user?.verificationStatus === 'VERIFIED'
  const canRequestMentorship = isVerified && user?.role === 'STUDENT' && profile.mentorshipAvailable

  return (
    <div className={styles.container}>
      <Button
        variant="ghost"
        size="sm"
        onClick={goBack}
        leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
      >
        Back
      </Button>

      {/* Header Card */}
      <Card className={styles.profileHeaderCard}>
        <div className={styles.headerTop}>
          <Avatar name={profile.name} size="xl" />
          {canRequestMentorship && (
            <Button
              variant="primary"
              size="md"
              onClick={() => navigate({ view: 'MENTORSHIP' })}
              leftIcon={<Send size={15} strokeWidth={2} />}
            >
              Request Mentorship
            </Button>
          )}
        </div>

        <div className={styles.headerInfo}>
          <h1 className={styles.name}>{profile.name}</h1>
          <div className={styles.badgeRow}>
            <Badge variant="burgundy">{profile.role}</Badge>
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

      {/* Professional & Skills */}
      <div className={styles.sectionsGrid}>
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <Briefcase size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Skills & Expertise
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

        {/* Links & Contact */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <LinkIcon size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Connect
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
