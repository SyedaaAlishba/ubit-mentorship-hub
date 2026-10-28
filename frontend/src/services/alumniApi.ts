/**
 * Typed client functions for the Alumni Discovery domain.
 */

import { api } from '@/services/api'
import type { AlumniProfileResponse, Page } from '@/types/api'

export interface AlumniDiscoveryFilter {
  page?: number
  size?: number
  role?: string
  program?: string
  passoutYear?: string
  skills?: string[]
  technologies?: string[]
  company?: string
  workArea?: string
  jobTitle?: string
  name?: string
  mentorshipAvailable?: string
}

export const alumniApi = {
  discover: (filter: AlumniDiscoveryFilter = {}) => {
    const params = new URLSearchParams()
    if (filter.page !== undefined) params.set('page', String(filter.page))
    if (filter.size !== undefined) params.set('size', String(filter.size))
    if (filter.role) params.set('role', filter.role)
    if (filter.program) params.set('program', filter.program)
    if (filter.passoutYear) params.set('passoutYear', filter.passoutYear)
    if (filter.company) params.set('company', filter.company)
    if (filter.workArea) params.set('workArea', filter.workArea)
    if (filter.jobTitle) params.set('jobTitle', filter.jobTitle)
    if (filter.name) params.set('name', filter.name)
    if (filter.mentorshipAvailable !== undefined) params.set('mentorshipAvailable', filter.mentorshipAvailable)

    if (filter.skills && filter.skills.length > 0) {
      filter.skills.forEach((s) => params.append('skills', s))
    }
    if (filter.technologies && filter.technologies.length > 0) {
      filter.technologies.forEach((t) => params.append('technologies', t))
    }

    const qs = params.toString()
    return api.get<Page<AlumniProfileResponse>>(`/api/alumni${qs ? `?${qs}` : ''}`)
  },
}
