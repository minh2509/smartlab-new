import { Download, FileText } from 'lucide-react'

// Represents the parsed canonical content object.
export interface ArticleContentDocument {
  body: string
  files?: ArticleContentAttachmentReference[]
}

export type ArticleContentAttachmentReference =
  | { type: 'image'; fileId: number; alt?: string | null }
  | { type: 'file'; fileId: number; label?: string | null }

function publicFileUrl(fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  return `${baseUrl}/files/${fileId}`
}

function parseArticleContent(json: unknown): ArticleContentDocument | null {
  if (typeof json !== 'object' || json === null) return null
  const body = (json as Record<string, unknown>).body
  if (typeof body !== 'string') return null

  const filesRaw = (json as Record<string, unknown>).files
  const files: ArticleContentAttachmentReference[] = []

  if (Array.isArray(filesRaw)) {
    for (const item of filesRaw) {
      if (typeof item !== 'object' || item === null) continue
      const type = (item as Record<string, unknown>).type
      const fileId = (item as Record<string, unknown>).fileId
      if (typeof fileId !== 'number') continue

      if (type === 'image') {
        const alt = (item as Record<string, unknown>).alt
        files.push({ type: 'image', fileId, alt: typeof alt === 'string' ? alt : undefined })
      } else if (type === 'file') {
        const label = (item as Record<string, unknown>).label
        files.push({ type: 'file', fileId, label: typeof label === 'string' ? label : undefined })
      }
    }
  }

  return { body, files }
}

export function ArticleContent({ contentJson, className, bodyClassName, fallbackClassName }: {
  contentJson: Record<string, unknown>
  className?: string
  bodyClassName?: string
  fallbackClassName?: string
}) {
  const content = parseArticleContent(contentJson)

  if (!content) {
    return (
      <div className={fallbackClassName ?? 'article-page-content-state'}>
        <p>Nội dung này hiện chưa hỗ trợ hiển thị đầy đủ.</p>
      </div>
    )
  }

  return (
    <div className={className}>
      <p className={bodyClassName} style={{ whiteSpace: 'pre-wrap' }}>{content.body}</p>
      <ArticleContentAttachments references={content.files ?? []} />
    </div>
  )
}

function ArticleContentAttachments({ references }: { references: ArticleContentAttachmentReference[] }) {
  if (!references.length) return null
  return (
    <div className="article-content-attachments">
      {references.map((reference, i) => reference.type === 'image'
        ? <ArticleImage key={`img-${reference.fileId}-${i}`} reference={reference} />
        : <ArticleFile key={`file-${reference.fileId}-${i}`} reference={reference} />
      )}
    </div>
  )
}

function ArticleImage({ reference }: { reference: Extract<ArticleContentAttachmentReference, { type: 'image' }> }) {
  return (
    <img
      className="article-content-image"
      src={publicFileUrl(reference.fileId)}
      alt={reference.alt ?? ''}
      loading="lazy"
    />
  )
}

function ArticleFile({ reference }: { reference: Extract<ArticleContentAttachmentReference, { type: 'file' }> }) {
  return (
    <div className="article-content-file">
      <a href={publicFileUrl(reference.fileId)} download={reference.label ?? `attachment-${reference.fileId}`} target="_blank" rel="noopener noreferrer" className="btn outline">
        <FileText aria-hidden="true" size={16} />
        {reference.label ?? `Tệp đính kèm #${reference.fileId}`}
        <Download aria-hidden="true" size={16} />
      </a>
    </div>
  )
}
