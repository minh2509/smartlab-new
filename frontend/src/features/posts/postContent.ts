import type { PostContentAttachmentReference, PostContentDocument } from './types'

export function parsePostContent(contentJson: Record<string, unknown> | undefined): PostContentDocument | null {
  if (contentJson?.type !== 'doc' || typeof contentJson.body !== 'string') return null
  const files = Array.isArray(contentJson.files)
    ? contentJson.files.flatMap(parseReference)
    : []
  return { type: 'doc', body: contentJson.body, ...(files.length ? { files } : {}) }
}

export function serializePostContent(body: string, files: PostContentAttachmentReference[]): PostContentDocument {
  return { type: 'doc', body, ...(files.length ? { files } : {}) }
}

function parseReference(value: unknown): PostContentAttachmentReference[] {
  if (!value || typeof value !== 'object') return []
  const reference = value as Record<string, unknown>
  if (!Number.isSafeInteger(reference.fileId) || (reference.fileId as number) <= 0) return []
  if (reference.type === 'image') {
    return [{ type: 'image', fileId: reference.fileId as number, ...(typeof reference.alt === 'string' ? { alt: reference.alt } : {}) }]
  }
  if (reference.type === 'file') {
    return [{ type: 'file', fileId: reference.fileId as number, ...(typeof reference.label === 'string' ? { label: reference.label } : {}) }]
  }
  return []
}
