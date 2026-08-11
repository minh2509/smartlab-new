import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { Save } from 'lucide-react'
import { Feedback } from '../../../shared/components/Feedback'
import { listProjects } from '../../projects/api'
import type { Project } from '../../projects/types'
import { listContentCategories } from '../api'
import type {
  ContentCategory,
  CreatePostRequest,
  PostDetail,
  PostVisibility,
  UpdatePostRequest,
} from '../types'

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
  const initialContentBody = getContentBody(initialPost?.contentJson)
  const [contentText, setContentText] = useState(initialContentBody ?? '')
  const [contentEdited, setContentEdited] = useState(false)
  const [categories, setCategories] = useState<ContentCategory[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [referencesLoading, setReferencesLoading] = useState(true)
  const [referenceError, setReferenceError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    if (!title.trim()) {
      setFormError('Tiêu đề không được để trống.')
      return
    }
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
          contentText,
          contentEdited,
        })
      : buildCreateRequest({
          title,
          excerpt,
          visibility,
          categoryId,
          projectId,
          contentText,
          contentEdited,
        })

    if (initialPost && Object.keys(request).length === 0) {
      setFormError('Chưa có thay đổi nào để lưu.')
      return
    }

    setFormError(null)
    await onSubmit(request)
  }

  return (
    <form className="post-editor-form" onSubmit={(event) => void handleSubmit(event)}>
      <div className="post-editor-main">
        <div className="field">
          <label htmlFor="post-title">Tiêu đề <span className="req">*</span></label>
          <input
            id="post-title"
            className="input"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={250}
            disabled={submitting}
            required
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
            disabled={submitting}
            rows={3}
          />
          <span className="hint">{excerpt.length}/500 ký tự</span>
        </div>

        <div className="field">
          <label htmlFor="post-content">Nội dung bài viết</label>
          <textarea
            id="post-content"
            className="textarea post-content-editor"
            value={contentText}
            onChange={(event) => {
              if (event.target.value !== contentText) setContentEdited(true)
              setContentText(event.target.value)
            }}
            disabled={submitting}
            aria-describedby="post-content-help"
            placeholder="Viết nội dung bài viết..."
            rows={12}
          />
          <span className="hint" id="post-content-help">
            Bạn có thể xuống dòng để trình bày nội dung rõ ràng hơn.
          </span>
          {initialPost && initialContentBody === null ? (
            <p className="post-content-legacy-note">
              Nội dung hiện có chưa thể chỉnh sửa bằng trình soạn thảo này. Chỉ nhập nội dung mới khi bạn muốn thay thế nội dung hiện tại.
            </p>
          ) : null}
        </div>
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
            disabled={submitting}
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
              disabled={submitting || referencesLoading}
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
            disabled={submitting || referencesLoading}
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

        <button className="btn primary post-editor-submit" type="submit" disabled={submitting || referencesLoading}>
          <Save aria-hidden="true" />
          {submitting ? 'Đang lưu...' : initialPost ? 'Lưu thay đổi' : 'Tạo bài viết'}
        </button>
      </aside>
    </form>
  )
}

type EditorValues = {
  title: string
  excerpt: string
  visibility: PostVisibility
  categoryId: string
  projectId: string
  contentText: string
  contentEdited: boolean
}

function buildCreateRequest(values: EditorValues): CreatePostRequest {
  const request: CreatePostRequest = {
    title: values.title,
    contentJson: toContentDocument(values.contentText),
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

  const initialContentBody = getContentBody(initial.contentJson)
  if (initialContentBody !== null ? values.contentText !== initialContentBody : values.contentEdited) {
    request.contentJson = toContentDocument(values.contentText)
  }
  return request
}

function toContentDocument(body: string): Record<string, unknown> {
  return { type: 'doc', body }
}

function getContentBody(contentJson: Record<string, unknown> | undefined): string | null {
  return typeof contentJson?.body === 'string' ? contentJson.body : null
}
