import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import { Paperclip, Save, Trash2, Upload } from 'lucide-react'
import { Feedback } from '../../../shared/components/Feedback'
import { D2_UPLOAD_ACCEPT, uploadFile } from '../../files/api'
import { listProjects } from '../../projects/api'
import type { Project } from '../../projects/types'
import { listContentCategories } from '../api'
import type {
  ContentCategory,
  CreatePostRequest,
  PostDetail,
  PostContentAttachmentReference,
  PostContentDocument,
  PostVisibility,
  UpdatePostRequest,
} from '../types'
import { inlineImageFileIds, parsePostContent, serializePostContent } from '../postContent'
import { PostPreviewDialog } from './PostPreviewDialog'
import { RichTextEditor } from './RichTextEditor'

type EditorProps = {
  token: string
  initialPost?: PostDetail
  submitting: boolean
  serverError?: string | null
  onSubmit: (request: CreatePostRequest | UpdatePostRequest) => Promise<void>
}

export function PostEditorForm({ token, initialPost, submitting, serverError, onSubmit }: EditorProps) {
  const [title, setTitle] = useState(initialPost?.title ?? '')
  const [excerpt, setExcerpt] = useState(initialPost?.excerpt ?? '')
  const [visibility, setVisibility] = useState<PostVisibility>(initialPost?.visibility ?? 'LAB')
  const [categoryId, setCategoryId] = useState(initialPost?.category?.id ? String(initialPost.category.id) : '')
  const [projectId, setProjectId] = useState(initialPost?.projectId ? String(initialPost.projectId) : '')
  const initialContent = parsePostContent(initialPost?.contentJson)
  const [contentDocument, setContentDocument] = useState<PostContentDocument>(
    initialContent ?? { type: 'doc', content: [] },
  )
  const [contentEdited, setContentEdited] = useState(false)
  const [attachments, setAttachments] = useState<PostContentAttachmentReference[]>(
    () => parsePostContent(initialPost?.contentJson)?.files ?? [],
  )
  const [attachmentsEdited, setAttachmentsEdited] = useState(false)
  const [uploading, setUploading] = useState(false)
  const attachmentInputRef = useRef<HTMLInputElement>(null)
  const [categories, setCategories] = useState<ContentCategory[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [referencesLoading, setReferencesLoading] = useState(true)
  const [referenceError, setReferenceError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)

  useEffect(() => {
    let active = true
    setReferencesLoading(true)
    setReferenceError(null)

    void Promise.all([listContentCategories(token), listProjects(token)])
      .then(([categoryResult, projectResult]) => {
        if (!active) return
        setCategories(categoryResult)
        setProjects(projectResult)
      })
      .catch((value: unknown) => {
        if (active) {
          setReferenceError(value instanceof Error ? value.message : 'Không thể tải danh mục và dự án.')
        }
      })
      .finally(() => {
        if (active) setReferencesLoading(false)
      })

    return () => {
      active = false
    }
  }, [token])

  const selectedProjectMissing = useMemo(
    () => Boolean(projectId) && !projects.some((project) => String(project.id) === projectId),
    [projectId, projects],
  )
  const selectedCategoryMissing = useMemo(
    () => Boolean(categoryId) && !categories.some((category) => String(category.id) === categoryId),
    [categoryId, categories],
  )
  const previewCategoryLabel = categories.find((category) => String(category.id) === categoryId)?.name
    ?? (selectedCategoryMissing ? initialPost?.category?.name : undefined)
  const inlineImageIds = inlineImageFileIds(contentDocument)
  const listedAttachments = attachments
    .map((reference, index) => ({ reference, index }))
    .filter(({ reference }) => reference.type !== 'image' || !inlineImageIds.has(reference.fileId))

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || uploading) return

    if (title.length > 250) {
      setFormError('Tiêu đề tối đa 250 ký tự.')
      return
    }
    if (excerpt.length > 500) {
      setFormError('Tóm tắt tối đa 500 ký tự.')
      return
    }
    if (visibility === 'PROJECT' && !projectId) {
      setFormError('Vui lòng chọn dự án cho bài viết theo dự án.')
      return
    }

    const request = initialPost
      ? buildUpdateRequest(initialPost, {
          title,
          excerpt,
          visibility,
          categoryId,
          projectId,
          contentDocument,
          contentEdited,
          attachments,
          attachmentsEdited,
        })
      : buildCreateRequest({
          title,
          excerpt,
          visibility,
          categoryId,
          projectId,
          contentDocument,
          contentEdited,
          attachments,
        })

    if (initialPost && Object.keys(request).length === 0) {
      setFormError('Chưa có thay đổi nào để lưu.')
      return
    }

    setFormError(null)
    await onSubmit(request)
  }

  async function uploadAttachment(file?: File) {
    if (!file || uploading || submitting) return
    setUploading(true)
    setFormError(null)
    try {
      const uploaded = await uploadFile(token, file, 'PRIVATE', '')
      const reference: PostContentAttachmentReference = uploaded.mimeType.startsWith('image/')
        ? { type: 'image', fileId: uploaded.id, alt: uploaded.originalName }
        : { type: 'file', fileId: uploaded.id, label: uploaded.originalName }
      setAttachments((current) => [...current, reference])
      setAttachmentsEdited(true)
      if (attachmentInputRef.current) attachmentInputRef.current.value = ''
    } catch (value) {
      setFormError(value instanceof Error ? value.message : 'Không thể tải tệp đính kèm.')
    } finally {
      setUploading(false)
    }
  }

  async function uploadInlineImage(file: File) {
    if (uploading || submitting) throw new Error('Đang có thao tác tải tệp khác.')
    setUploading(true)
    setFormError(null)
    try {
      const uploaded = await uploadFile(token, file, 'PRIVATE', '')
      if (!uploaded.mimeType.startsWith('image/')) throw new Error('Tệp đã chọn không phải là ảnh hợp lệ.')
      const reference: Extract<PostContentAttachmentReference, { type: 'image' }> = {
        type: 'image',
        fileId: uploaded.id,
        alt: uploaded.originalName,
      }
      setAttachments((current) => [...current, reference])
      setAttachmentsEdited(true)
      return reference
    } catch (value) {
      setFormError(value instanceof Error ? value.message : 'Không thể tải ảnh.')
      throw value
    } finally {
      setUploading(false)
    }
  }

  function updateAttachment(index: number, value: string) {
    setAttachments((current) => current.map((reference, currentIndex) => {
      if (currentIndex !== index) return reference
      return reference.type === 'image' ? { ...reference, alt: value } : { ...reference, label: value }
    }))
    setAttachmentsEdited(true)
  }

  function removeAttachment(index: number) {
    setAttachments((current) => current.filter((_, currentIndex) => currentIndex !== index))
    setAttachmentsEdited(true)
  }

  const busy = submitting || uploading

  return (
    <form className="post-editor-form" onSubmit={(event) => void handleSubmit(event)}>
      <div className="post-editor-main">
        <div className="field">
          <label htmlFor="post-title">Tiêu đề</label>
          <input
            id="post-title"
            className="input"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={250}
            disabled={busy}
          />
          <span className="hint">{title.length}/250 ký tự</span>
        </div>

        <div className="field">
          <label htmlFor="post-excerpt">Tóm tắt</label>
          <textarea
            id="post-excerpt"
            className="textarea post-editor-excerpt"
            value={excerpt}
            onChange={(event) => setExcerpt(event.target.value)}
            maxLength={500}
            disabled={busy}
            rows={3}
          />
          <span className="hint">{excerpt.length}/500 ký tự</span>
        </div>

        <div className="field post-content-editor-field">
          <label htmlFor="post-content">Nội dung bài viết</label>
          <RichTextEditor
            token={token}
            value={contentDocument}
            disabled={busy}
            onChange={(nextDocument) => {
              const previousInlineImageIds = inlineImageFileIds(contentDocument)
              const nextInlineImageIds = inlineImageFileIds(nextDocument)
              if ([...previousInlineImageIds].some((fileId) => !nextInlineImageIds.has(fileId))) {
                setAttachments((current) => current.filter((reference) => (
                  reference.type !== 'image'
                  || !previousInlineImageIds.has(reference.fileId)
                  || nextInlineImageIds.has(reference.fileId)
                )))
                setAttachmentsEdited(true)
              }
              setContentDocument(nextDocument)
              setContentEdited(true)
            }}
            onImageUpload={uploadInlineImage}
            onPreviewRequest={() => setPreviewOpen(true)}
          />
          {initialPost && initialContent === null ? (
            <p className="post-content-legacy-note">
              Nội dung hiện có chưa thể chỉnh sửa bằng trình soạn thảo này. Chỉ nhập nội dung mới khi bạn muốn thay thế nội dung hiện tại.
            </p>
          ) : null}
        </div>

        <section className="post-editor-attachments" aria-labelledby="post-attachments-title">
          <div className="post-editor-attachments-head">
            <div>
              <h2 id="post-attachments-title">Tệp đính kèm</h2>
              <p>Ảnh chèn từ thanh công cụ nằm ngay trong nội dung; khu vực này dành cho tệp đính kèm riêng.</p>
            </div>
            <input
              ref={attachmentInputRef}
              className="post-editor-file-input"
              type="file"
              accept={D2_UPLOAD_ACCEPT}
              disabled={busy}
              onChange={(event) => void uploadAttachment(event.target.files?.[0])}
            />
            <button className="btn" type="button" disabled={busy} onClick={() => attachmentInputRef.current?.click()}>
              <Upload aria-hidden="true" /> {uploading ? 'Đang tải tệp...' : 'Thêm tệp'}
            </button>
          </div>
          {listedAttachments.length ? <div className="post-editor-attachment-list">
            {listedAttachments.map(({ reference, index }) => <div className="post-editor-attachment" key={`${reference.type}-${reference.fileId}`}>
              <Paperclip aria-hidden="true" />
              <span className="post-editor-attachment-type">{reference.type === 'image' ? 'Ảnh' : 'Tệp'} · #{reference.fileId}</span>
              <input
                className="input"
                aria-label={reference.type === 'image' ? `Mô tả ảnh ${index + 1}` : `Nhãn tệp ${index + 1}`}
                value={reference.type === 'image' ? reference.alt ?? '' : reference.label ?? ''}
                placeholder={reference.type === 'image' ? 'Mô tả ảnh' : 'Tên tệp'}
                onChange={(event) => updateAttachment(index, event.target.value)}
                disabled={busy}
              />
              <button className="btn ghost table-btn" type="button" disabled={busy} onClick={() => removeAttachment(index)} aria-label="Xóa tệp đính kèm">
                <Trash2 aria-hidden="true" />
              </button>
            </div>)}
          </div> : <p className="post-editor-attachment-empty">Chưa có tệp đính kèm riêng.</p>}
        </section>
      </div>

      <aside className="post-editor-settings" aria-label="Thiết lập bài viết">
        <div>
          <h2>Thiết lập</h2>
          <p>Chọn phạm vi hiển thị và thông tin phân loại.</p>
        </div>

        <div className="field">
          <label htmlFor="post-visibility">Phạm vi hiển thị</label>
          <select
            id="post-visibility"
            className="select"
            value={visibility}
            onChange={(event) => {
              const nextVisibility = event.target.value as PostVisibility
              setVisibility(nextVisibility)
              if (nextVisibility !== 'PROJECT') setProjectId('')
            }}
            disabled={busy}
          >
            <option value="LAB">Nội bộ Lab</option>
            <option value="PUBLIC">Công khai</option>
            <option value="PROJECT">Theo dự án</option>
          </select>
        </div>

        {visibility === 'PROJECT' ? (
          <div className="field">
            <label htmlFor="post-project">Dự án <span className="req">*</span></label>
            <select
              id="post-project"
              className="select"
              value={projectId}
              onChange={(event) => setProjectId(event.target.value)}
              disabled={busy || referencesLoading}
              required
            >
              <option value="">Chọn dự án</option>
              {selectedProjectMissing ? <option value={projectId}>Dự án #{projectId}</option> : null}
              {projects.map((project) => (
                <option value={project.id} key={project.id}>{project.code} - {project.name}</option>
              ))}
            </select>
            <span className="hint">
              Danh sách này không xác nhận tư cách thành viên. Backend sẽ kiểm tra khi lưu.
            </span>
          </div>
        ) : null}

        <div className="field">
          <label htmlFor="post-category">Danh mục</label>
          <select
            id="post-category"
            className="select"
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            disabled={busy || referencesLoading}
          >
            <option value="">Không có danh mục</option>
            {selectedCategoryMissing ? (
              <option value={categoryId}>{initialPost?.category?.name ?? `Danh mục #${categoryId}`}</option>
            ) : null}
            {categories.map((category) => (
              <option value={category.id} key={category.id}>{category.name}</option>
            ))}
          </select>
        </div>

        {referencesLoading ? <p className="post-editor-reference-state">Đang tải danh mục và dự án...</p> : null}
        <Feedback error={referenceError ?? undefined} />
        <Feedback error={formError ?? serverError ?? undefined} />

        <button className="btn primary post-editor-submit" type="submit" disabled={busy || referencesLoading}>
          <Save aria-hidden="true" />
          {uploading ? 'Đang tải tệp...' : submitting ? 'Đang lưu...' : initialPost ? 'Lưu thay đổi' : 'Lưu nháp'}
        </button>
      </aside>
      {previewOpen ? <PostPreviewDialog
        token={token}
        title={title}
        excerpt={excerpt}
        visibility={visibility}
        categoryLabel={previewCategoryLabel}
        contentDocument={contentDocument}
        attachments={attachments}
        onClose={() => setPreviewOpen(false)}
      /> : null}
    </form>
  )
}

type EditorValues = {
  title: string
  excerpt: string
  visibility: PostVisibility
  categoryId: string
  projectId: string
  contentDocument: PostContentDocument
  contentEdited: boolean
  attachments: PostContentAttachmentReference[]
  attachmentsEdited?: boolean
}

function buildCreateRequest(values: EditorValues): CreatePostRequest {
  const request: CreatePostRequest = {
    title: values.title,
    contentJson: serializePostContent(values.contentDocument, values.attachments),
    visibility: values.visibility,
  }
  if (values.excerpt) request.excerpt = values.excerpt
  if (values.categoryId) request.categoryId = Number(values.categoryId)
  if (values.visibility === 'PROJECT') request.projectId = Number(values.projectId)
  return request
}

function buildUpdateRequest(initial: PostDetail, values: EditorValues): UpdatePostRequest {
  const request: UpdatePostRequest = {}
  if (values.title !== initial.title) request.title = values.title
  if (values.excerpt !== (initial.excerpt ?? '')) request.excerpt = values.excerpt || null
  if (values.visibility !== initial.visibility) request.visibility = values.visibility

  const nextCategoryId = values.categoryId ? Number(values.categoryId) : null
  if (nextCategoryId !== (initial.category?.id ?? null)) request.categoryId = nextCategoryId

  const nextProjectId = values.visibility === 'PROJECT' ? Number(values.projectId) : null
  if (nextProjectId !== initial.projectId) request.projectId = nextProjectId

  if (values.contentEdited || values.attachmentsEdited) {
    request.contentJson = serializePostContent(values.contentDocument, values.attachments)
  }
  return request
}
