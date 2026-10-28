/**
 * Typed client functions for the Mentorship domain.
 */

import { api } from '@/services/api'
import type {
  MentorshipRequestResponse,
  MentorshipResponse,
  Page,
} from '@/types/api'

export interface CreateMentorshipRequestParams {
  recipientUserId: string
  message: string
  goal?: string
}

export const mentorshipApi = {
  createRequest: (data: CreateMentorshipRequestParams) =>
    api.post<MentorshipRequestResponse>('/api/mentorship/requests', data),

  listSent: (page = 0, size = 20) =>
    api.get<Page<MentorshipRequestResponse>>(`/api/mentorship/requests/sent?page=${page}&size=${size}`),

  listReceived: (page = 0, size = 20) =>
    api.get<Page<MentorshipRequestResponse>>(`/api/mentorship/requests/received?page=${page}&size=${size}`),

  getRequest: (requestId: string) =>
    api.get<MentorshipRequestResponse>(`/api/mentorship/requests/${encodeURIComponent(requestId)}`),

  acceptRequest: (requestId: string) =>
    api.post<MentorshipResponse>(`/api/mentorship/requests/${encodeURIComponent(requestId)}/accept`),

  rejectRequest: (requestId: string) =>
    api.post<MentorshipRequestResponse>(`/api/mentorship/requests/${encodeURIComponent(requestId)}/reject`),

  cancelRequest: (requestId: string) =>
    api.post<MentorshipRequestResponse>(`/api/mentorship/requests/${encodeURIComponent(requestId)}/cancel`),

  listActive: (page = 0, size = 20) =>
    api.get<Page<MentorshipResponse>>(`/api/mentorship/active?page=${page}&size=${size}`),

  endMentorship: (mentorshipId: string, reason?: string) =>
    api.post<MentorshipResponse>(
      `/api/mentorship/${encodeURIComponent(mentorshipId)}/end`,
      reason ? { reason } : undefined,
    ),

  end: (mentorshipId: string, reason?: string) =>
    api.post<MentorshipResponse>(
      `/api/mentorship/${encodeURIComponent(mentorshipId)}/end`,
      reason ? { reason } : undefined,
    ),
}
