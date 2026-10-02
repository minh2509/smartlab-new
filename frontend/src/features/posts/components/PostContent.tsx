import { Download, FileText } from 'lucide-react'
import { useEffect, useState } from 'react'
import { downloadPostContentFile } from '../api'
import { inlineImageFileIds, parsePostContent } from '../postContent'
import type { PostContentAttachmentReference, PostContentDocument } from '../types'
import { PostRichText } from './PostRichText'

type PostContentProps = {
  contentJson: Record<string, unknown>
  slug: string
  token?: string | null
  parsedContent?: PostContentDocument | null
  className?: string
  bodyClassName?: string
  fallbackClassName?: string
}

export function PostContent({ contentJson, slug, token, parsedContent, className, bodyClassName, fallbackClassName }: PostContentProps) {
  const content = parsedContent === undefined ? parsePostContent(contentJson) : parsedContent
  if (!content) {
    return <div className={fallbackClassName ?? 'post-page-content-state'}><p>Nội dung này hiện chưa hỗ trợ hiển thị đầy đủ.</p></div>
  }
  return (
    <div className={className}>
      <PostRichText
        document={content}
        className={bodyClassName}
        renderImage={(fileId, alt, key) => <PostImage key={key} slug={slug} token={token} reference={{ type: 'image', fileId, alt }} inline />}
      />
      <PostContentAttachments slug={slug} token={token} references={content.files ?? []} inlineImageIds={inlineImageFileIds(content)} />
    </div>
  )
}

function PostContentAttachments({ slug, token, references, inlineImageIds }: {
  slug: string
  token?: string | null
  references: PostContentAttachmentReference[]
  inlineImageIds: Set<number>
}) {
  const standaloneReferences = references.filter((reference) => reference.type !== 'image' || !inlineImageIds.has(reference.fileId))
  if (!standaloneReferences.length) return null
  return <div className="post-content-attachments">
    {standaloneReferences.map((reference) => reference.type === 'image'
      ? <PostImage key={`${reference.type}-${reference.fileId}`} slug={slug} token={token} reference={reference} />
      : <PostFile key={`${reference.type}-${reference.fileId}`} slug={slug} token={token} reference={reference} />)}
  </div>
}

function PostImage({ slug, token, reference, inline = false }: { slug: string, token?: string | null, reference: Extract<PostContentAttachmentReference, { type: 'image' }>, inline?: boolean }) {
  const { url, error } = usePostMediaUrl(slug, reference.fileId, token)
  if (error) return <p className="post-media-unavailable">Không thể tải ảnh đính kèm.</p>
  if (!url) return <p className="post-media-loading">Đang tải ảnh...</p>
  if (inline) return <figure className="post-rich-inline-image"><img src={url} alt={reference.alt ?? ''} />{reference.alt ? <figcaption>{reference.alt}</figcaption> : null}</figure>
  return <img className="post-content-image" src={url} alt={reference.alt ?? ''} />
}

function PostFile({ slug, token, reference }: { slug: string, token?: string | null, reference: Extract<PostContentAttachmentReference, { type: 'file' }> }) {
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function download() {
    if (downloading) return
    setDownloading(true)
    setError(null)
    try {
      const { blob, filename } = await downloadPostContentFile(token, slug, reference.fileId)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename ?? reference.label ?? `attachment-${reference.fileId}`
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể tải tệp đính kèm.')
    } finally {
      setDownloading(false)
    }
  }
  return <div className="post-content-file">
    <button type="button" onClick={() => void download()} disabled={downloading}>
      <FileText aria-hidden="true" /> {downloading ? 'Đang tải...' : reference.label ?? `Tệp đính kèm #${reference.fileId}`} <Download aria-hidden="true" />
    </button>
    {error ? <p className="post-media-unavailable" role="alert">{error}</p> : null}
  </div>
}

function usePostMediaUrl(slug: string, fileId: number, token?: string | null) {
  const [state, setState] = useState<{ url: string | null, error: boolean }>({ url: null, error: false })
  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    setState({ url: null, error: false })
    void downloadPostContentFile(token, slug, fileId)
      .then(({ blob }) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setState({ url: objectUrl, error: false })
      })
      .catch(() => {
        if (active) setState({ url: null, error: true })
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [fileId, slug, token])
  return state
}
