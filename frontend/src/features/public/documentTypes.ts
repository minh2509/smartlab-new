export const PUBLIC_DOCUMENT_FILE_TYPES = ['ALL', 'PDF', 'DOCUMENT', 'SPREADSHEET', 'PRESENTATION', 'IMAGE', 'ARCHIVE', 'OTHER'] as const
export type PublicDocumentFileType = (typeof PUBLIC_DOCUMENT_FILE_TYPES)[number]

export const PUBLIC_DOCUMENT_SORTS = ['LATEST', 'OLDEST', 'TITLE_ASC', 'TITLE_DESC'] as const
export type PublicDocumentSort = (typeof PUBLIC_DOCUMENT_SORTS)[number]

export type PublicDocumentSummary = {
  id: number
  title: string
  description: string | null
  projectId: number
  projectCode: string
  projectName: string
  currentFileId: number
  originalFileName: string
  mimeType: string
  sizeBytes: number
  currentVersionNo: number
  updatedAt: string
}
