import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Edit3, Send, ShieldCheck, Trash2, Upload } from 'lucide-react'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { deletePost, directPublishPost, getPostBySlug, publishPost, submitPost } from '../api'
import { PostContent } from '../components/PostContent'
import type { PostDetail, PostStatus } from '../types'
import { getProject } from '../../projects/api'
import type { Project } from '../../projects/types'

const STATUS_LABELS: Record<PostStatus, string> = {
  DRAFT: 'Bản nháp',
  PENDING_REVIEW: 'Chờ duyệt',
  REVISION_REQUIRED: 'Cần chỉnh sửa',
  APPROVED: 'Đã duyệt',
  PUBLISHED: 'Đã xuất bản',
  REJECTED: 'Bị từ chối',
}

type MutationKind = 'submit' | 'publish' | 'direct-publish' | 'delete'

export function PostDetailPage() {
  const { token, profile } = useAuth()
  const { slug } = useParams<{ slug: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(() => {
    const state = location.state as { message?: unknown } | null
    return typeof state?.message === 'string' ? state.message : null
  })
  const [pending, setPending] = useState<MutationKind | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [postProject, setPostProject] = useState<Project | null>(null)

  useEffect(() => {
    if (!slug) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    setPost(null)
    void getPostBySlug(token, slug)
      .then(setPost)
      .catch((value: unknown) => setError(token
        ? (value instanceof Error ? value.message : 'Không thể tải bài viết.')
        : 'Bài viết không tồn tại hoặc không khả dụng.'))
      .finally(() => setLoading(false))
  }, [slug, token])

  const permissions = profile?.permissions ?? []
  const canSubmit = permissions.includes('posts.submit')
  const canPublish = permissions.includes('posts.publish')
  const canProjectManage = permissions.includes('PROJECT_MANAGE')
  const canDirectPublishGlobally = permissions.includes('posts.publish.direct')

  useEffect(() => {
    const projectId = post?.visibility === 'PROJECT' ? post.projectId : null
    setPostProject(null)
    if (!token || !canProjectManage || !projectId) return

    let active = true
    void getProject(projectId, token)
      .then((project) => {
        if (active) setPostProject(project)
      })
      .catch(() => {
        if (active) setPostProject(null)
      })

    return () => {
      active = false
    }
  }, [post?.projectId, post?.visibility, token, canProjectManage])

  const canDirectPublishAsProjectLeader = Boolean(
    post?.visibility === 'PROJECT'
      && post.projectId
      && canProjectManage
      && profile
      && postProject?.id === post.projectId
      && postProject.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canDirectPublish = canDirectPublishGlobally || canDirectPublishAsProjectLeader

  async function mutate(kind: Exclude<MutationKind, 'delete'>) {
    if (!token || !post || pending) return
    setPending(kind)
    setError(null)
    setSuccess(null)
    try {
      const updated = kind === 'submit'
        ? await submitPost(token, post.id)
        : kind === 'publish'
          ? await publishPost(token, post.id)
          : await directPublishPost(token, post.id)
      setPost(updated)
      setSuccess(kind === 'submit' ? 'Đã gửi bài viết để duyệt.' : 'Bài viết đã được xuất bản.')
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể cập nhật trạng thái bài viết.')
    } finally {
      setPending(null)
    }
  }

  async function remove() {
    if (!token || !post || pending) return
    setPending('delete')
    setError(null)
    try {
      await deletePost(token, post.id)
      navigate('/my-posts', { replace: true })
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể xóa bài viết.')
      setPending(null)
      setConfirmingDelete(false)
    }
  }

  return (
    <section className="section">
      <div className="wrap">
        <Link className="more" to={post && post.status !== 'PUBLISHED' ? '/my-posts' : '/posts'}>
          {post && post.status !== 'PUBLISHED' ? '← Bài viết của tôi' : '← Quay lại Bảng tin'}
        </Link>

        {loading ? <div className="empty">Đang tải bài viết...</div> : null}
        {!loading && (!slug || (error && !post)) ? (
          <div className="empty">
            <h3>Không thể tải bài viết</h3>
            <p>{error ?? 'Đường dẫn bài viết không hợp lệ.'}</p>
          </div>
        ) : null}

        {!loading && post ? (
          <article className="post-page-detail">
            <header className="post-page-header">
              <div className="post-page-eyebrow">
                {post.category ? <span className="post-page-category">{post.category.name}</span> : null}
              </div>
              <h1 className="post-page-title">{post.title}</h1>
              {post.excerpt ? <p className="post-page-excerpt">{post.excerpt}</p> : null}
              <div className="post-page-meta">
                <span className="post-page-dates">
                  <span>{post.author?.name ?? 'Tác giả không còn khả dụng'} · {formatDate(post.createdAt)}</span>
                  {post.updatedAt !== post.createdAt ? <span>Cập nhật: {formatDate(post.updatedAt)}</span> : null}
                  {post.publishedAt ? <span>Xuất bản: {formatDate(post.publishedAt)}</span> : null}
                </span>
                <span className="post-page-status-visibility">
                  {STATUS_LABELS[post.status]} · {post.visibility}
                  {post.visibility === 'PROJECT' && post.projectId ? ` #${post.projectId}` : ''}
                </span>
              </div>
            </header>

            {token ? <PostActions
              post={post}
              pending={pending}
              canSubmit={canSubmit}
              canPublish={canPublish}
              canDirectPublish={canDirectPublish}
              confirmingDelete={confirmingDelete}
              setConfirmingDelete={setConfirmingDelete}
              onMutate={mutate}
              onDelete={remove}
            /> : null}
            <div className="post-detail-feedback" aria-live="polite">
              <Feedback message={success ?? undefined} error={error ?? undefined} />
            </div>

            <hr className="post-page-divider" />
            <PostContent contentJson={post.contentJson} slug={post.slug} token={token} className="post-page-content-body" />
          </article>
        ) : null}
      </div>
    </section>
  )
}

type PostActionsProps = {
  post: PostDetail
  pending: MutationKind | null
  canSubmit: boolean
  canPublish: boolean
  canDirectPublish: boolean
  confirmingDelete: boolean
  setConfirmingDelete: (value: boolean) => void
  onMutate: (kind: Exclude<MutationKind, 'delete'>) => Promise<void>
  onDelete: () => Promise<void>
}

function PostActions(props: PostActionsProps) {
  const { post, pending, confirmingDelete } = props
  const isDraft = post.status === 'DRAFT'
  const hasActions = isDraft || (post.status === 'APPROVED' && props.canPublish)
  if (!hasActions) return null

  return (
    <section className="post-author-actions" aria-label="Thao tác bài viết">
      <div className="post-author-action-row">
        {isDraft ? (
          <Link className="btn" to={`/posts/${encodeURIComponent(post.slug)}/edit`}>
            <Edit3 aria-hidden="true" /> Chỉnh sửa
          </Link>
        ) : null}
        {isDraft && props.canSubmit ? (
          <button className="btn" type="button" disabled={Boolean(pending)} onClick={() => void props.onMutate('submit')}>
            <Send aria-hidden="true" /> {pending === 'submit' ? 'Đang gửi...' : 'Gửi duyệt'}
          </button>
        ) : null}
        {isDraft && props.canDirectPublish ? (
          <button className="btn primary" type="button" disabled={Boolean(pending)} onClick={() => void props.onMutate('direct-publish')}>
            <Upload aria-hidden="true" /> {pending === 'direct-publish' ? 'Đang xuất bản...' : 'Xuất bản trực tiếp'}
          </button>
        ) : null}
        {post.status === 'APPROVED' && props.canPublish ? (
          <button className="btn primary" type="button" disabled={Boolean(pending)} onClick={() => void props.onMutate('publish')}>
            <ShieldCheck aria-hidden="true" /> {pending === 'publish' ? 'Đang xuất bản...' : 'Xuất bản'}
          </button>
        ) : null}
        {isDraft ? (
          <button className="btn post-delete-trigger" type="button" disabled={Boolean(pending)} onClick={() => props.setConfirmingDelete(true)}>
            <Trash2 aria-hidden="true" /> Xóa
          </button>
        ) : null}
      </div>

      {confirmingDelete ? (
        <div className="post-delete-confirm" role="alertdialog" aria-labelledby="delete-post-title" aria-describedby="delete-post-description">
          <div>
            <strong id="delete-post-title">Xóa bản nháp này?</strong>
            <p id="delete-post-description">Bài viết sẽ không còn xuất hiện trong danh sách của bạn.</p>
          </div>
          <div>
            <button className="btn" type="button" disabled={Boolean(pending)} onClick={() => props.setConfirmingDelete(false)}>Giữ lại</button>
            <button className="btn danger" type="button" disabled={Boolean(pending)} onClick={() => void props.onDelete()}>
              {pending === 'delete' ? 'Đang xóa...' : 'Xác nhận xóa'}
            </button>
          </div>
        </div>
      ) : null}
    </section>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(value))
}
