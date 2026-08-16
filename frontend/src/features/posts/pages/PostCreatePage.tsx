import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { createPost } from '../api'
import { PostEditorForm } from '../components/PostEditorForm'
import type { CreatePostRequest, UpdatePostRequest } from '../types'

export function PostCreatePage() {
  const { token } = useAuth()
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!token) return <Navigate to="/login" replace />

  async function handleCreate(request: CreatePostRequest | UpdatePostRequest) {
    if (!token || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createPost(token, request as CreatePostRequest)
      navigate(`/posts/${encodeURIComponent(created.slug)}`, { replace: true })
    } catch (value) {
      setError(value instanceof Error ? value.message : 'Không thể tạo bài viết.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="post-editor-page">
      <div className="post-editor-shell">
        <Link className="more post-editor-back" to="/my-posts"><ArrowLeft aria-hidden="true" /> Bài viết của tôi</Link>
        <header className="post-editor-head">
          <h1>Tạo bài viết</h1>
          <p>Soạn bài viết và chọn phạm vi truy cập phù hợp.</p>
        </header>
        <PostEditorForm token={token} submitting={submitting} serverError={error} onSubmit={handleCreate} />
      </div>
    </section>
  )
}
