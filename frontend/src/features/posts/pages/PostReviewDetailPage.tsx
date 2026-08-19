import { type FormEvent, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CalendarDays, ClipboardCheck, Send } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { getReviewablePost, reviewPost } from '../api'
import { PostContent } from '../components/PostContent'
import type { PostDetail, ReviewDecision } from '../types'
import { useToast } from '../../../shared/toast/useToast'

const DECISIONS: Array<{ value: ReviewDecision; label: string; description: string }> = [
  { value: 'APPROVED', label: 'Duyệt', description: 'Đạt yêu cầu và xuất bản ngay lên Bảng tin.' },
  { value: 'REVISION_REQUIRED', label: 'Yêu cầu chỉnh sửa', description: 'Cần tác giả cập nhật trước khi gửi duyệt lại.' },
  { value: 'REJECTED', label: 'Từ chối', description: 'Không chấp nhận bài viết ở trạng thái hiện tại.' },
]

export function PostReviewDetailPage() {
  const { token } = useAuth()
  const toast = useToast()
  const { id } = useParams<{ id: string }>()
  const [post, setPost] = useState<PostDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [decision, setDecision] = useState<ReviewDecision | ''>('')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [reviewSucceeded, setReviewSucceeded] = useState(false)

  useEffect(() => {
    if (!token || !id) {
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)
    setPost(null)
    setDecision('')
    setReason('')
    setFormError(null)
    setReviewSucceeded(false)

    void getReviewablePost(token, id)
      .then(setPost)
      .catch((value: unknown) => {
        setError(value instanceof Error ? value.message : 'Không thể tải bài viết để duyệt.')
      })
      .finally(() => setLoading(false))
  }, [id, token])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!token || !post || !decision || submitting) return

    if (requiresReviewReason(decision) && !reason.trim()) {
      setFormError(decision === 'REVISION_REQUIRED'
        ? 'Vui lòng nêu nội dung cần chỉnh sửa.'
        : 'Vui lòng nêu lý do từ chối.')
      return
    }

    setFormError(null)
    setSubmitting(true)

    try {
      const updated = await reviewPost(token, post.id, {
        decision,
        reason: reason || null,
      })
      setPost(updated)
      setReviewSucceeded(true)
      toast.success('Đã gửi quyết định duyệt bài')
    } catch (value) {
      toast.error('Không thể gửi quyết định duyệt bài', value instanceof Error ? value.message : 'Vui lòng thử lại.')
    } finally {
      setSubmitting(false)
    }
  }

  const canReview = post?.status === 'PENDING_REVIEW' && !reviewSucceeded
  const reasonRequired = requiresReviewReason(decision)

  return (
    <section className="section post-review-page">
      <div className="wrap">
        {loading ? <div className="empty">Đang tải bài viết để duyệt...</div> : null}

        {!loading && (!id || error) ? (
          <div className="empty">
            <h3>Không thể tải bài viết</h3>
            <p>{error ?? 'Đường dẫn bài viết không hợp lệ.'}</p>
          </div>
        ) : null}

        {!loading && !error && post ? (
          <div className="post-review-workspace">
            <article className="post-page-detail post-review-detail post-review-content-pane">
              <Link className="more post-review-back" to="/posts/review-queue">
                <ArrowLeft aria-hidden="true" />
                Hàng chờ duyệt bài
              </Link>

              <header className="post-page-header">
                <div className="post-page-eyebrow">
                  {post.category ? <span className="post-page-category">{post.category.name}</span> : null}
                </div>

                <h1 className="post-page-title">{post.title}</h1>

                {post.excerpt ? <p className="post-page-excerpt">{post.excerpt}</p> : null}

                <div className="post-page-meta">
                  <span className="post-page-dates">
                    <CalendarDays aria-hidden="true" />
                    <span>{post.author?.name ?? 'Tác giả không còn khả dụng'} · {formatDate(post.createdAt)}</span>
                    {post.updatedAt !== post.createdAt ? <span>Cập nhật: {formatDate(post.updatedAt)}</span> : null}
                  </span>
                  <span className="post-page-status-visibility">{statusLabel(post.status)}</span>
                  <span className="post-page-status-visibility">{visibilityLabel(post.visibility)}</span>
                </div>
              </header>

              <hr className="post-page-divider" />

              <PostContent contentJson={post.contentJson} slug={post.slug} token={token} className="post-page-content-body" />
            </article>

            <aside className="post-review-sidebar">
              <section className="post-review-panel" aria-labelledby="review-form-title">
                <div className="post-review-panel-head">
                  <span className="post-review-panel-icon" aria-hidden="true">
                    <ClipboardCheck />
                  </span>
                  <div>
                    <h2 id="review-form-title">Quyết định duyệt bài</h2>
                    <p>Chọn quyết định phù hợp với trạng thái bài viết.</p>
                  </div>
                </div>

                {reviewSucceeded ? (
                  <div className="post-review-success" role="status">
                    {post.status === 'PUBLISHED'
                      ? 'Đã duyệt và xuất bản bài viết.'
                      : <>Đã gửi quyết định. Trạng thái hệ thống trả về: <strong>{statusLabel(post.status)}</strong>.</>}
                  </div>
                ) : null}

                {!canReview ? (
                  <p className="post-review-closed">
                    Bài viết hiện không còn ở trạng thái chờ duyệt. Không có thao tác duyệt nào khả dụng.
                  </p>
                ) : null}

                {canReview ? (
                  <form className="post-review-form" onSubmit={(event) => void handleSubmit(event)}>
                    <fieldset disabled={submitting}>
                      <legend>Chọn quyết định</legend>
                      <div className="review-choice-grid">
                        {DECISIONS.map((option) => (
                          <label className="review-choice" key={option.value}>
                            <input
                              type="radio"
                              name="review-decision"
                              value={option.value}
                              checked={decision === option.value}
                              onChange={() => setDecision(option.value)}
                            />
                            <span className="review-choice-copy">
                              <span className="review-choice-label">{option.label}</span>
                              <span className="review-choice-description">{option.description}</span>
                            </span>
                          </label>
                        ))}
                      </div>
                    </fieldset>

                    <div className="post-review-reason">
                      <label htmlFor="review-reason">
                        {decision === 'REJECTED'
                          ? 'Lý do từ chối'
                          : decision === 'REVISION_REQUIRED' ? 'Nội dung cần chỉnh sửa' : 'Lý do hoặc ghi chú'}
                        <span className="post-review-reason-required">
                          {reasonRequired ? 'Bắt buộc cho quyết định này' : 'Không bắt buộc'}
                        </span>
                      </label>
                      <textarea
                        id="review-reason"
                        className={formError ? 'textarea invalid' : 'textarea'}
                        value={reason}
                        onChange={(event) => setReason(event.target.value)}
                        maxLength={1000}
                        rows={4}
                        aria-required={reasonRequired}
                        aria-describedby="review-reason-help review-reason-count"
                        placeholder={decision === 'REJECTED'
                          ? 'Nêu lý do từ chối bài viết.'
                          : decision === 'REVISION_REQUIRED'
                            ? 'Nêu các nội dung tác giả cần chỉnh sửa.'
                            : 'Thêm ghi chú nếu cần.'}
                      />
                      <div className="post-review-reason-meta">
                        <span id="review-reason-help">
                          {reasonHelp(decision)}
                        </span>
                        <span id="review-reason-count">{reason.length}/1000</span>
                      </div>
                      {formError ? <span className="field-error">{formError}</span> : null}
                    </div>

                    <div className="review-actions">
                      <button className="btn primary" type="submit" disabled={!decision || submitting}>
                        <Send aria-hidden="true" />
                        {submitting ? 'Đang gửi...' : 'Gửi quyết định'}
                      </button>
                    </div>
                  </form>
                ) : null}
              </section>
            </aside>
          </div>
        ) : null}
      </div>
    </section>
  )
}

function reasonHelp(decision: ReviewDecision | '') {
  if (decision === 'REJECTED') return 'Vui lòng nêu lý do trước khi gửi.'
  if (decision === 'REVISION_REQUIRED') return 'Vui lòng nêu các điểm cần cập nhật trước khi gửi.'
  return 'Có thể thêm ghi chú nếu cần.'
}

function requiresReviewReason(decision: ReviewDecision | '') {
  return decision === 'REVISION_REQUIRED' || decision === 'REJECTED'
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value))
}

function statusLabel(status: PostDetail['status']) {
  const labels: Record<PostDetail['status'], string> = {
    DRAFT: 'Bản nháp',
    PENDING_REVIEW: 'Chờ duyệt',
    REVISION_REQUIRED: 'Cần chỉnh sửa',
    APPROVED: 'Đã duyệt',
    PUBLISHED: 'Đã xuất bản',
    REJECTED: 'Bị từ chối',
  }
  return labels[status]
}

function visibilityLabel(visibility: PostDetail['visibility']) {
  const labels: Record<PostDetail['visibility'], string> = {
    PUBLIC: 'Công khai',
    LAB: 'Nội bộ Lab',
    PROJECT: 'Theo dự án',
  }
  return labels[visibility]
}
