/**
 * Typed client functions for the Admin Verification domain.
 */

import { api } from '@/services/api'
import type {
  AdminDecision,
  AdminVerificationApplication,
  Page,
} from '@/types/api'

export interface AdminDecisionParams {
  decision: AdminDecision
  reviewerNote?: string
}

export const adminApi = {
  listApplications: (page = 0, size = 20, status?: string) => {
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(size))
    if (status) params.set('status', status)
    return api.get<Page<AdminVerificationApplication>>(
      `/api/admin/verification/applications?${params.toString()}`,
    )
  },

  decide: (applicationId: string, data: AdminDecisionParams) =>
    api.post<{ applicationId: string; status: string; decidedAt: string }>(
      `/api/admin/verification/applications/${encodeURIComponent(applicationId)}/decide`,
      data,
    ),

  getDocumentUrl: (applicationId: string) =>
    `/api/admin/verification/applications/${encodeURIComponent(applicationId)}/document`,
}
