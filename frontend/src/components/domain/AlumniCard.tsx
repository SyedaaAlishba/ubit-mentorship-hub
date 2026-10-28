import Avatar from '@/components/ui/Avatar'
import Badge from '@/components/ui/Badge'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import type { AlumniProfileResponse } from '@/types/api'
import { programLabel } from '@/utils/labels'
import { Briefcase, Send, User } from 'lucide-react'
import styles from './AlumniCard.module.css'

export interface AlumniCardProps {
  profile: AlumniProfileResponse
  onViewProfile?: () => void
  onRequestMentorship?: () => void
  canRequestMentorship?: boolean
}

export default function AlumniCard({
  profile,
  onViewProfile,
  onRequestMentorship,
  canRequestMentorship = false,
}: AlumniCardProps) {
  return (
    <Card className={styles.card}>
      <div className={styles.header}>
        <Avatar name={profile.name} size="lg" />
        <div className={styles.headerInfo}>
          <h3 className={styles.name}>{profile.name}</h3>
          <div className={styles.roleLine}>
            <span className={styles.role}>{profile.role}</span>
            {profile.program && (
              <>
                <span className={styles.dot}>&middot;</span>
                <span className={styles.program}>{programLabel(profile.program)}</span>
              </>
            )}
            {profile.passoutYear && (
              <>
                <span className={styles.dot}>&middot;</span>
                <span className={styles.year}>Class of '{String(profile.passoutYear).slice(-2)}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {(profile.jobTitle || profile.company) && (
        <div className={styles.jobLine}>
          <Briefcase size={14} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-2px', marginRight: '5px', color: 'var(--color-text-muted)' }} />
          <span className={styles.jobTitle}>{profile.jobTitle ?? 'Professional'}</span>
          {profile.company && <span className={styles.company}>at {profile.company}</span>}
        </div>
      )}

      {profile.bio && <p className={styles.bio}>{profile.bio}</p>}

      {profile.skills && profile.skills.length > 0 && (
        <div className={styles.skills}>
          {profile.skills.slice(0, 3).map((skill) => (
            <span key={skill} className={styles.skillBadge}>
              {skill}
            </span>
          ))}
          {profile.skills.length > 3 && (
            <span className={styles.moreSkills}>+{profile.skills.length - 3}</span>
          )}
        </div>
      )}

      <div className={styles.footer}>
        <div className={styles.statusSection}>
          {profile.mentorshipAvailable ? (
            <Badge variant="success">Available for Mentorship</Badge>
          ) : (
            <Badge variant="neutral">Not mentoring</Badge>
          )}
        </div>

        <div className={styles.actions}>
          {onViewProfile && (
            <Button
              variant="secondary"
              size="sm"
              onClick={onViewProfile}
              leftIcon={<User size={13} strokeWidth={2} />}
            >
              View Profile
            </Button>
          )}
          {canRequestMentorship && profile.mentorshipAvailable && onRequestMentorship && (
            <Button
              variant="primary"
              size="sm"
              onClick={onRequestMentorship}
              leftIcon={<Send size={13} strokeWidth={2} />}
            >
              Request
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}
