import { CalendarDays, CheckCircle2, Clock3, LogIn, RefreshCw, Send, Undo2, UserPlus } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import {
  cancelMyProjectJoinRequest,
  createProjectJoinRequest,
  getMyProjectJoinRequest,
  listMyProjectMemberships,
} from '../api'
import {
  PROJECT_JOIN_REQUEST_STATUS_LABELS,
  PROJECT_MEMBER_ROLE_LABELS,
} from '../types'
import type { Project, ProjectJoinRequest, ProjectMembershipHistory } from '../types'
import './ProjectJoinRequestCard.css'

export function ProjectJoinRequestCard({ project }: { project: Project }) {
  const { token, profile } = useAuth()
  const [memberships, setMemberships] = useState<ProjectMembershipHistory[]>([])
  const [joinRequest, setJoinRequest] = useState<ProjectJoinRequest | null>(null)
  const [note, setNote] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const loadIdRef = useRef(0)

  const projectMemberships = useMemo(
    () => memberships.filter((membership) => membership.projectId === project.id),
    [memberships, project.id],
  )
  const activeMembership = projectMemberships.find((membership) => membership.status === 'ACTIVE') ?? null
  const removedMembership = projectMemberships.find((membership) => membership.status === 'REMOVED') ?? null

  const loadState = useCallback(async () => {
    if (!token) return
    const loadId = ++loadIdRef.current
    const [membershipResult, requestResult] = await Promise.all([
      listMyProjectMemberships(token),
      getMyProjectJoinRequest(token, project.id),
    ])
    if (loadId !== loadIdRef.current) return
    setMemberships(membershipResult)
    setJoinRequest(requestResult)
  }, [project.id, token])

  useEffect(() => {
    loadIdRef.current += 1
    setMemberships([])
    setJoinRequest(null)
    setNote('')
    setMessage('')
    setError('')
  }, [project.id])

  useEffect(() => {
    if (!token || profile?.roles.includes('ADMIN')) {
      loadIdRef.current += 1
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    setError('')
    void loadState()
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được trạng thái tham gia dự án.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
      loadIdRef.current += 1
    }
  }, [loadState, profile?.roles, token])

  async function handleSend() {
    if (!token || busy || activeMembership || joinRequest?.status === 'PENDING') return
    setBusy(true)
    clearFeedback()
    try {
      const created = await createProjectJoinRequest(token, project.id, note)
      setJoinRequest(created)
      setNote('')
      setMessage('Yêu cầu tham gia đã được gửi tới leader dự án.')
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể gửi yêu cầu tham gia.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleCancel() {
    if (!token || busy || joinRequest?.status !== 'PENDING') return
    if (!window.confirm('Hủy yêu cầu tham gia dự án này?')) return
    setBusy(true)
    clearFeedback()
    try {
      await cancelMyProjectJoinRequest(token, project.id)
      setJoinRequest((current) => current ? { ...current, status: 'CANCELLED' } : null)
      setMessage('Đã hủy yêu cầu tham gia.')
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể hủy yêu cầu tham gia.'))
    } finally {
      setBusy(false)
    }
  }

  async function handleReload() {
    if (busy) return
    setBusy(true)
    clearFeedback()
    try {
      await loadState()
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không tải lại được trạng thái tham gia.'))
    } finally {
      setBusy(false)
    }
  }

  function clearFeedback() {
    setMessage('')
    setError('')
  }

  if (!token) return null

  if (profile?.roles.includes('ADMIN')) {
    return (
      <div className="project-join-card compact">
        <div>
          <span className="muted small">Tài khoản quản trị</span>
          <strong>Quản lý dự án trong workspace</strong>
        </div>
        <Link className="btn" to="/admin/projects">Mở quản lý</Link>
      </div>
    )
  }

  return (
    <section className="project-join-card" aria-labelledby="project-join-title">
      <div className="project-join-head">
        <span className="project-join-icon" aria-hidden="true"><UserPlus /></span>
        <div>
          <span className="muted small">Tài khoản Smart Lab</span>
          <h3 id="project-join-title">Tham gia dự án</h3>
        </div>
        <button
          className="btn ghost table-btn"
          type="button"
          aria-label="Tải lại trạng thái tham gia"
          disabled={loading || busy}
          onClick={() => void handleReload()}
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </div>

      <Feedback message={message} error={error} />

      {loading ? <p className="muted small">Đang kiểm tra trạng thái tham gia...</p> : null}

      {!loading && activeMembership ? (
        <div className="project-join-state success">
          <CheckCircle2 aria-hidden="true" />
          <div>
            <strong>Bạn đang tham gia dự án này</strong>
            <p>Vai trò: {PROJECT_MEMBER_ROLE_LABELS[activeMembership.projectRole]} · tham gia {formatDateTime(activeMembership.joinedAt)}</p>
          </div>
          <span className="project-join-actions">
            <Link className="btn primary" to="/admin/projects"><LogIn aria-hidden="true" /> Mở workspace</Link>
            <Link className="btn" to={`/admin/events?projectId=${project.id}`}><CalendarDays aria-hidden="true" /> Xem sự kiện</Link>
          </span>
        </div>
      ) : null}

      {!loading && !activeMembership && joinRequest?.status === 'PENDING' ? (
        <div className="project-join-state pending">
          <Clock3 aria-hidden="true" />
          <div>
            <strong>Yêu cầu đang chờ duyệt</strong>
            <p>Gửi lúc {formatDateTime(joinRequest.createdAt)}{joinRequest.message ? ` · “${joinRequest.message}”` : ''}</p>
          </div>
          <button className="btn ghost danger-text" type="button" disabled={busy} onClick={() => void handleCancel()}>
            <Undo2 aria-hidden="true" /> Hủy yêu cầu
          </button>
        </div>
      ) : null}

      {!loading && !activeMembership && joinRequest?.status === 'APPROVED' && !removedMembership ? (
        <div className="project-join-state success">
          <CheckCircle2 aria-hidden="true" />
          <div>
            <strong>Yêu cầu đã được chấp nhận</strong>
            <p>Tải lại trạng thái nếu workspace chưa hiển thị dự án.</p>
          </div>
        </div>
      ) : null}

      {!loading && !activeMembership && joinRequest?.status !== 'PENDING'
        && !(joinRequest?.status === 'APPROVED' && !removedMembership) ? (
        <div className="project-join-form">
          {removedMembership ? (
            <p className="project-join-note">
              Bạn đã rời dự án ngày {formatDateTime(removedMembership.removedAt)}. Bạn có thể gửi yêu cầu tham gia lại.
            </p>
          ) : null}
          {joinRequest && joinRequest.status !== 'APPROVED' ? (
            <p className="project-join-note">
              Yêu cầu gần nhất: <strong>{PROJECT_JOIN_REQUEST_STATUS_LABELS[joinRequest.status]}</strong>
              {joinRequest.reviewedAt ? ` · ${formatDateTime(joinRequest.reviewedAt)}` : ''}.
            </p>
          ) : null}
          <label className="field">
            <span>Lời nhắn cho leader <small>(không bắt buộc)</small></span>
            <textarea
              className="textarea"
              rows={3}
              maxLength={500}
              disabled={busy}
              value={note}
              onChange={(event) => {
                setNote(event.target.value)
                clearFeedback()
              }}
              placeholder="Giới thiệu ngắn về mong muốn tham gia của bạn..."
            />
            <small>{note.length}/500 ký tự</small>
          </label>
          <button className="btn primary" type="button" disabled={busy} onClick={() => void handleSend()}>
            <Send aria-hidden="true" /> {busy ? 'Đang gửi...' : removedMembership ? 'Gửi yêu cầu tham gia lại' : 'Gửi yêu cầu tham gia'}
          </button>
        </div>
      ) : null}
    </section>
  )
}

function formatDateTime(value: string | null) {
  if (!value) return 'chưa rõ'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
