import type { FileResponse } from '../../shared/types/api'

export const DOCUMENT_ACCESS_SCOPES = ['PUBLIC', 'LAB', 'PROJECT', 'PRIVATE'] as const

export type DocumentAccessScope = (typeof DOCUMENT_ACCESS_SCOPES)[number]

export type DocumentActor = {
  userId: string
  name: string
  email: string
}

export type ProjectDocument = {
  id: number
  projectId: number
  title: string
  description: string | null
  currentFile: FileResponse
  currentVersionNo: number
  createdBy: DocumentActor | null
  createdAt: string
  updatedAt: string
}

export type DocumentVersion = {
  id: number
  documentId: number
  versionNo: number
  file: FileResponse
  uploadedBy: DocumentActor | null
  note: string | null
  createdAt: string
}

export type CreateProjectDocumentPayload = {
  file: File
  title: string
  description: string
  accessScope: DocumentAccessScope
  note: string
}

export type CreateDocumentVersionPayload = {
  file: File
  accessScope: DocumentAccessScope
  note: string
}
