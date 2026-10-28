/**
 * Typed client functions for the profile domain.
 */

import { api } from '@/services/api'
import type { ProfilePatchRequest, ProfilePublicResponse, ProfileResponse } from '@/types/api'

export const profileApi = {
  getMe: () => api.get<ProfileResponse>('/api/profile/me'),
  updateMe: (patch: ProfilePatchRequest) => api.patch<ProfileResponse>('/api/profile/me', patch),
  getPublic: (userId: string) => api.get<ProfilePublicResponse>(`/api/profile/${encodeURIComponent(userId)}`),
}
