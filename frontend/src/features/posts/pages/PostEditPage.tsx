import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import { getPostBySlug, updatePost } from '../api'
import { PostEditorForm } from '../components/PostEditorForm'
import type { CreatePostRequest, PostDetail, UpdatePostRequest } from '../types'

export function PostEditPage() {
  const { token } = useAuth()
  const { slug } = useParams<{ slug: string }>()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isAuthorEditable = post?.status === 'DRAFT' || post?.status === 'REVISION_REQUIRED'

  useEffect(() => {
    if (!token || !slug) {
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    void getPostBySlug(token, slug)
      .then(setPost)
      .catch((value: unknown) => setError(value instanceof Error ? value.message : 'Không thể tải bài viết.'))
      .finally(() => setLoading(false))
  }, [slug, token])

  if (!token) return <Navigate to="/login" replace />

  async function handleUpdate(request: CreatePostRequest | UpdatePostRequest) {
    if (!token || !post || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const updated = await updatePost(token, post.id, request as UpdatePostRequest)
      navigate(`/posts/${encodeURIComponent(updated.slug)}`, {
        replace: true,
        state: { message: 'Đã lưu thay đổi bài viết.' },
      })
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể cập nhật bài viết.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="post-editor-page">
      <div className="post-editor-shell">
        <Link className="more post-editor-back" to={slug ? `/posts/${encodeURIComponent(slug)}` : '/my-posts'}>
          <ArrowLeft aria-hidden="true" /> Bài viết của tôi
        </Link>
        <header className="post-editor-head">
          <h1>Chỉnh sửa bài viết</h1>
          <p>Cập nhật nội dung và thiết lập của bài viết cần hoàn thiện.</p>
        </header>

        {loading ? <div className="empty tight">Đang tải dữ liệu bài viết...</div> : null}
        {!loading && !post ? <Feedback error={error ?? 'Không tìm thấy bài viết.'} /> : null}
        {!loading && post && !isAuthorEditable ? (
          <Feedback error="Backend hiện chỉ cho phép chỉnh sửa bản nháp hoặc bài viết cần chỉnh sửa." />
        ) : null}
        {!loading && post && isAuthorEditable ? (
          <PostEditorForm
            token={token}
            initialPost={post}
            submitting={submitting}
            serverError={error}
            onSubmit={handleUpdate}
          />
        ) : null}
      </div>
    </section>
  )
}
