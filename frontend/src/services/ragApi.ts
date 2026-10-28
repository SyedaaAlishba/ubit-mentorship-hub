/**
 * RAG (Retrieval-Augmented Generation) API client.
 * Talks to the backend /api/rag/* endpoints.
 */

import { api } from './api'

export interface RagSourceInfo {
  question: string
  category: string
  similarityScore: number
}

export interface RagQueryResponse {
  question: string
  answer: string
  grounded: boolean
  similarityScore: number
  sources: RagSourceInfo[]
}

export interface RagIngestionResponse {
  totalIngested: number
  sourceDocument: string
  message: string
}

export const ragApi = {
  /**
   * Ask a question to the UBIT AI assistant.
   * Requires authenticated session with completed onboarding.
   */
  ask: (question: string) =>
    api.post<RagQueryResponse>('/api/rag/ask', { question }),

  /**
   * Trigger knowledge-base ingestion (admin / dev utility).
   */
  ingest: () => api.post<RagIngestionResponse>('/api/rag/ingest'),
}
