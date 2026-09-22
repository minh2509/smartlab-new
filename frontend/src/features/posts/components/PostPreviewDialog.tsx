import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { FileText, Globe2, LockKeyhole, Users, X } from 'lucide-react'
import { downloadFile } from '../../files/api'
import type { PostContentAttachmentReference, PostContentDocument, PostVisibility } from '../types'
import { inlineImageFileIds } from '../postContent'
import { PostRichText } from './PostRichText'

type Props = {
  token: string
  title: string
  excerpt: string
  visibility: PostVisibility
  categoryLabel?: string
  contentDocument: PostContentDocument
  attachments: PostContentAttachmentReference[]
  onClose: () => void
}

const VISIBILITY_LABELS: Record<PostVisibility, string> = {
  PUBLIC: 'Công khai',
  LAB: 'Nội bộ Lab',
  PROJECT: 'Theo dự án',
}

export function PostPreviewDialog({
  token,
  title,
  excerpt,
  visibility,
  categoryLabel,
  contentDocument,
  attachments,
  onClose,
}: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const previousOverflow = documentBodyOverflow()
    document.body.style.overflow = 'hidden'
    dialogRef.current?.focus()
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
      if (event.key === 'Tab' && dialogRef.current) keepFocusInside(event, dialogRef.current)
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose])

  const VisibilityIcon = visibility === 'PUBLIC' ? Globe2 : visibility === 'PROJECT' ? Users : LockKeyhole
  const inlineImageIds = inlineImageFileIds(contentDocument)
  const standaloneAttachments = attachments.filter((attachment) => attachment.type !== 'image' || !inlineImageIds.has(attachment.fileId))

  return createPortal(
    <div className="post-preview-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose()
    }}>
      <div
        ref={dialogRef}
        className="post-preview-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="post-preview-title"
        tabIndex={-1}
      >
        <header className="post-preview-dialog-head">
          <div>
            <span>Bản xem trước</span>
            <strong>Nội dung chưa được lưu hoặc xuất bản</strong>
          </div>
          <button type="button" onClick={onClose} aria-label="Đóng bản xem trước"><X aria-hidden="true" /></button>
        </header>
        <div className="post-preview-scroll">
          <article className="post-preview-article">
            <div className="post-preview-eyebrow">
              {categoryLabel ? <span>{categoryLabel}</span> : null}
              <span><VisibilityIcon aria-hidden="true" /> {VISIBILITY_LABELS[visibility]}</span>
            </div>
            <h1 id="post-preview-title">{title.trim() || 'Bài viết chưa có tiêu đề'}</h1>
            {excerpt.trim() ? <p className="post-preview-excerpt">{excerpt}</p> : null}
            <div className="post-preview-divider" />
            <PostRichText
              document={contentDocument}
              className="post-preview-content"
              renderImage={(fileId, alt, key) => <PreviewImage key={key} token={token} fileId={fileId} alt={alt} inline />}
            />
            {standaloneAttachments.length ? <section className="post-preview-attachments" aria-label="Ảnh và tệp đính kèm">
              {standaloneAttachments.map((attachment) => attachment.type === 'image'
                ? <PreviewImage key={`image-${attachment.fileId}`} token={token} fileId={attachment.fileId} alt={attachment.alt ?? ''} />
                : <div className="post-preview-file" key={`file-${attachment.fileId}`}>
                    <FileText aria-hidden="true" />
                    <span>{attachment.label || `Tệp đính kèm #${attachment.fileId}`}</span>
                  </div>)}
            </section> : null}
          </article>
        </div>
        <footer className="post-preview-dialog-foot">
          <span>Đây là cách bài viết sẽ hiển thị sau khi được lưu.</span>
          <button className="btn primary" type="button" onClick={onClose}>Tiếp tục chỉnh sửa</button>
        </footer>
      </div>
    </div>,
    document.body,
  )
}

function PreviewImage({ token, fileId, alt, inline = false }: { token: string; fileId: number; alt: string; inline?: boolean }) {
  const [state, setState] = useState<{ url: string | null; failed: boolean }>({ url: null, failed: false })

  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    void downloadFile(token, fileId)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setState({ url: objectUrl, failed: false })
      })
      .catch(() => {
        if (active) setState({ url: null, failed: true })
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [fileId, token])

  if (state.failed) return <div className="post-preview-image-state">Không thể tải ảnh #{fileId} để xem trước.</div>
  if (!state.url) return <div className="post-preview-image-state">Đang tải ảnh xem trước…</div>
  return <figure className={inline ? 'post-rich-inline-image' : 'post-preview-image'}><img src={state.url} alt={alt} />{alt ? <figcaption>{alt}</figcaption> : null}</figure>
}

function documentBodyOverflow() {
  return document.body.style.overflow
}

function keepFocusInside(event: KeyboardEvent, container: HTMLElement) {
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(
    'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
  ))
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable.at(-1)!
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
