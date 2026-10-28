import { useCallback, useEffect, useState, type FormEvent } from 'react'
import Button from '@/components/ui/Button'
import Card from '@/components/ui/Card'
import Input from '@/components/ui/Input'
import Select from '@/components/ui/Select'
import Textarea from '@/components/ui/Textarea'
import { useNavigation } from '@/context/navigationContext'
import { profileApi } from '@/services/profileApi'
import type { PreferredContact, ProfilePatchRequest, ProfileResponse, Visibility } from '@/types/api'
import { ArrowLeft, Save, User, Briefcase, Link as LinkIcon, Handshake } from 'lucide-react'
import styles from './ProfileScreens.module.css'

const VISIBILITY_OPTIONS = [
  { value: 'PUBLIC', label: 'Public (all members)' },
  { value: 'VERIFIED_ONLY', label: 'Verified members only' },
  { value: 'PRIVATE', label: 'Private' },
]

const CONTACT_OPTIONS = [
  { value: 'EMAIL', label: 'Email' },
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'WEBSITE', label: 'Website' },
]

export default function EditProfileScreen() {
  const { navigate, goBack } = useNavigation()

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Form Fields
  const [bio, setBio] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [company, setCompany] = useState('')
  const [workArea, setWorkArea] = useState('')
  const [interests, setInterests] = useState('')
  const [linkedinUrl, setLinkedinUrl] = useState('')
  const [githubUrl, setGithubUrl] = useState('')
  const [websiteUrl, setWebsiteUrl] = useState('')
  const [location, setLocation] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('VERIFIED_ONLY')
  const [preferredContact, setPreferredContact] = useState<PreferredContact>('EMAIL')
  const [mentorshipAvailable, setMentorshipAvailable] = useState(false)

  // Skills & Tech string tags
  const [skillsStr, setSkillsStr] = useState('')
  const [techStr, setTechStr] = useState('')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const p: ProfileResponse = await profileApi.getMe()
      setBio(p.bio ?? '')
      setJobTitle(p.jobTitle ?? '')
      setCompany(p.company ?? '')
      setWorkArea(p.workArea ?? '')
      setInterests(p.interests ?? '')
      setLinkedinUrl(p.linkedinUrl ?? '')
      setGithubUrl(p.githubUrl ?? '')
      setWebsiteUrl(p.websiteUrl ?? '')
      setLocation(p.location ?? '')
      if (p.visibility) setVisibility(p.visibility)
      if (p.preferredContact) setPreferredContact(p.preferredContact)
      setMentorshipAvailable(Boolean(p.mentorshipAvailable))

      if (p.skills && p.skills.length > 0) setSkillsStr(p.skills.join(', '))
      if (p.technologies && p.technologies.length > 0) setTechStr(p.technologies.join(', '))
    } catch (e) {
      setError('Could not load profile for editing.')
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadData()
  }, [loadData])

  async function handleSave(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    setError(null)

    const skills = skillsStr
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)

    const technologies = techStr
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean)

    const trimmedBio = bio.trim()
    if (trimmedBio.length > 500) {
      setError('Bio cannot exceed 500 characters.')
      setSaving(false)
      return
    }

    const trimmedLocation = location.trim()
    if (trimmedLocation) {
      if (trimmedLocation.length < 2 || trimmedLocation.length > 100) {
        setError('Location must be between 2 and 100 characters.')
        setSaving(false)
        return
      }
      const locationRegex = /^[a-zA-Z0-9\s,.'\-–/()#]+$/
      if (!locationRegex.test(trimmedLocation)) {
        setError('Location contains invalid characters.')
        setSaving(false)
        return
      }
    }

    const LINKEDIN_REGEX = /^https?:\/\/(?:www\.)?linkedin\.com\/in\/[A-Za-z0-9_\-%.]+\/?$/i
    const GITHUB_REGEX = /^https?:\/\/(?:www\.)?github\.com\/[A-Za-z0-9_\-%.]+\/?$/i

    const trimmedLinkedin = linkedinUrl.trim()
    if (trimmedLinkedin) {
      if (!LINKEDIN_REGEX.test(trimmedLinkedin)) {
        setError('Please enter a valid LinkedIn profile URL.')
        setSaving(false)
        return
      }
    }

    const trimmedGithub = githubUrl.trim()
    if (trimmedGithub) {
      if (!GITHUB_REGEX.test(trimmedGithub)) {
        setError('Please enter a valid GitHub profile URL.')
        setSaving(false)
        return
      }
    }

    const trimmedWebsite = websiteUrl.trim()
    if (trimmedWebsite) {
      if (!trimmedWebsite.startsWith('http://') && !trimmedWebsite.startsWith('https://')) {
        setError('Website URL must start with http:// or https://')
        setSaving(false)
        return
      }
      try {
        const parsed = new URL(trimmedWebsite)
        if (!parsed.hostname || !parsed.hostname.includes('.')) {
          setError('Website URL must contain a valid domain name.')
          setSaving(false)
          return
        }
      } catch {
        setError('Website URL is not a valid URL.')
        setSaving(false)
        return
      }
    }

    const patch: ProfilePatchRequest = {
      bio: trimmedBio || undefined,
      jobTitle: jobTitle.trim() || undefined,
      company: company.trim() || undefined,
      workArea: workArea.trim() || undefined,
      interests: interests.trim() || undefined,
      linkedinUrl: trimmedLinkedin || undefined,
      githubUrl: trimmedGithub || undefined,
      websiteUrl: trimmedWebsite || undefined,
      location: trimmedLocation || undefined,
      visibility,
      preferredContact,
      mentorshipAvailable,
      skills: skills.length > 0 ? skills : undefined,
      technologies: technologies.length > 0 ? technologies : undefined,
    }

    try {
      await profileApi.updateMe(patch)
      navigate({ view: 'MY_PROFILE' })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Could not save profile changes.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className={styles.container}>
        <p className="muted">Loading profile editor...</p>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <div className={styles.headerTop}>
        <div>
          <h1 className={styles.name}>Edit Profile</h1>
          <p className="muted">Update your public profile, skills, and mentorship availability.</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={goBack}
          leftIcon={<ArrowLeft size={16} strokeWidth={2} />}
        >
          Cancel
        </Button>
      </div>

      {error && (
        <div className={styles.errorBanner} role="alert">
          {error}
        </div>
      )}

      <form onSubmit={(e) => void handleSave(e)} className={styles.editForm}>
        {/* Mentorship Settings */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <Handshake size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Mentorship Availability
          </h2>
          <label className={styles.checkboxLabel}>
            <input
              type="checkbox"
              checked={mentorshipAvailable}
              onChange={(e) => setMentorshipAvailable(e.target.checked)}
              className={styles.checkbox}
            />
            <div>
              <strong>Available to accept mentees</strong>
              <p className="muted" style={{ fontSize: '0.8rem' }}>
                Students will be able to discover your profile and send mentorship requests.
              </p>
            </div>
          </label>
        </Card>

        {/* About & Bio */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <User size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            About You
          </h2>
          <Textarea
            label="Bio (up to 500 characters)"
            placeholder="Share a short summary about your background, studies, or interests..."
            value={bio}
            onChange={(e) => setBio(e.target.value)}
            maxLength={500}
          />
          <Input
            label="Location"
            placeholder="e.g. Karachi, Pakistan"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            maxLength={100}
            helperText="2 to 100 characters (city, country, etc.)"
          />
        </Card>

        {/* Professional Details */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <Briefcase size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Professional Information
          </h2>
          <div className={styles.formRow}>
            <Input
              label="Job Title"
              placeholder="e.g. Software Engineer"
              value={jobTitle}
              onChange={(e) => setJobTitle(e.target.value)}
            />
            <Input
              label="Company / Organization"
              placeholder="e.g. Systems Limited"
              value={company}
              onChange={(e) => setCompany(e.target.value)}
            />
          </div>
          <Input
            label="Work Area / Field"
            placeholder="e.g. Backend Development, DevOps, AI/ML"
            value={workArea}
            onChange={(e) => setWorkArea(e.target.value)}
          />
          <Input
            label="Skills (comma-separated)"
            placeholder="e.g. Java, Spring Boot, React, Problem Solving"
            value={skillsStr}
            onChange={(e) => setSkillsStr(e.target.value)}
            helperText="Separate skills with commas"
          />
          <Input
            label="Technologies (comma-separated)"
            placeholder="e.g. Docker, Kubernetes, PostgreSQL, Git"
            value={techStr}
            onChange={(e) => setTechStr(e.target.value)}
            helperText="Separate technologies with commas"
          />
          <Textarea
            label="Interests"
            placeholder="e.g. Open Source, Cloud Architecture, Mentorship"
            value={interests}
            onChange={(e) => setInterests(e.target.value)}
          />
        </Card>

        {/* Links & Visibility */}
        <Card className={styles.sectionCard}>
          <h2 className={styles.sectionTitle}>
            <LinkIcon size={18} strokeWidth={2} style={{ display: 'inline', verticalAlign: '-3px', marginRight: '8px', color: 'var(--color-burgundy)' }} />
            Social Links & Privacy
          </h2>
          <Input
            label="LinkedIn URL"
            placeholder="https://www.linkedin.com/in/username"
            value={linkedinUrl}
            onChange={(e) => setLinkedinUrl(e.target.value)}
          />
          <Input
            label="GitHub URL"
            placeholder="https://github.com/username"
            value={githubUrl}
            onChange={(e) => setGithubUrl(e.target.value)}
          />
          <Input
            label="Website URL"
            placeholder="https://yourwebsite.com"
            value={websiteUrl}
            onChange={(e) => setWebsiteUrl(e.target.value)}
          />

          <div className={styles.formRow}>
            <Select
              label="Profile Visibility"
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as Visibility)}
              options={VISIBILITY_OPTIONS}
            />
            <Select
              label="Preferred Contact Method"
              value={preferredContact}
              onChange={(e) => setPreferredContact(e.target.value as PreferredContact)}
              options={CONTACT_OPTIONS}
            />
          </div>
        </Card>

        <div className={styles.formActions}>
          <Button type="button" variant="ghost" onClick={goBack} disabled={saving}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="primary"
            isLoading={saving}
            disabled={saving}
            leftIcon={<Save size={15} strokeWidth={2} />}
          >
            Save Changes
          </Button>
        </div>
      </form>
    </div>
  )
}
