/**
 * Typed client functions for the Q&A domain.
 */

import { api } from '@/services/api'
import type {
  AnswerResponse,
  Page,
  QuestionCategory,
  QuestionResponse,
} from '@/types/api'

export interface QuestionCreateParams {
  title: string
  body: string
  category: QuestionCategory
  tags?: string[]
}

export interface QuestionUpdateParams {
  title?: string
  body?: string
  category?: QuestionCategory
  tags?: string[]
}

export interface QuestionListQuery {
  page?: number
  size?: number
  category?: QuestionCategory
  tags?: string[]
  authorId?: string
}

export const questionsApi = {
  list: (query: QuestionListQuery = {}) => {
    const params = new URLSearchParams()
    if (query.page !== undefined) params.set('page', String(query.page))
    if (query.size !== undefined) params.set('size', String(query.size))
    if (query.category) params.set('category', query.category)
    if (query.authorId) params.set('authorId', query.authorId)
    if (query.tags && query.tags.length > 0) {
      query.tags.forEach((t) => params.append('tags', t))
    }
    const qs = params.toString()
    return api.get<Page<QuestionResponse>>(`/api/questions${qs ? `?${qs}` : ''}`)
  },

  get: (questionId: string) =>
    api.get<QuestionResponse>(`/api/questions/${encodeURIComponent(questionId)}`),

  create: (data: QuestionCreateParams) =>
    api.post<QuestionResponse>('/api/questions', data),

  update: (questionId: string, data: QuestionUpdateParams) =>
    api.patch<QuestionResponse>(`/api/questions/${encodeURIComponent(questionId)}`, data),

  delete: (questionId: string) =>
    api.delete(`/api/questions/${encodeURIComponent(questionId)}`),

  close: (questionId: string) =>
    api.post<QuestionResponse>(`/api/questions/${encodeURIComponent(questionId)}/close`),

  // Answers
  listAnswers: (questionId: string, page = 0, size = 20) =>
    api.get<Page<AnswerResponse>>(
      `/api/questions/${encodeURIComponent(questionId)}/answers?page=${page}&size=${size}`,
    ),

  createAnswer: (questionId: string, body: string) =>
    api.post<AnswerResponse>(`/api/questions/${encodeURIComponent(questionId)}/answers`, { body }),

  updateAnswer: (answerId: string, body: string) =>
    api.patch<AnswerResponse>(`/api/answers/${encodeURIComponent(answerId)}`, { body }),

  deleteAnswer: (answerId: string) =>
    api.delete(`/api/answers/${encodeURIComponent(answerId)}`),

  acceptAnswer: (questionId: string, answerId: string) =>
    api.postEmpty(
      `/api/questions/${encodeURIComponent(questionId)}/answers/${encodeURIComponent(answerId)}/accept`,
    ),

  unacceptAnswer: (questionId: string, answerId: string) =>
    api.delete(
      `/api/questions/${encodeURIComponent(questionId)}/answers/${encodeURIComponent(answerId)}/accept`,
    ),
}
