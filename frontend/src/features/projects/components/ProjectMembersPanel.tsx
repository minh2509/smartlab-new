import { Check, Plus, RefreshCw, RotateCcw, Search, Trash2, UserCheck, UsersRound, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useToast } from '../../../shared/toast/useToast'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import {
  addProjectMember,
  listProjectMemberCandidates,
  listProjectMembers,
  listProjectJoinRequests,
  removeProjectMember,
  reviewProjectJoinRequest,
} from '../api'
import {
  PROJECT_MEMBER_ROLE_LABELS,
  PROJECT_MEMBER_STATUS_LABELS,
} from '../types'
import type {
  Project,
  ProjectMember,
  ProjectMemberCandidate,
  ProjectMemberFilter,
  ProjectJoinRequest,
} from '../types'

export function ProjectMembersPanel({ project }: { project: Project | null }) {
  const { token, profile } = useAuth()
  const toast = useToast()
  const projectId = project?.id ?? null
  const [members, setMembers] = useState<ProjectMember[]>([])
  const [filter, setFilter] = useState<ProjectMemberFilter>('ACTIVE')
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<ProjectMemberCandidate[]>([])
  const [joinRequests, setJoinRequests] = useState<ProjectJoinRequest[]>([])
  const [loading, setLoading] = useState(false)
  const [searching, setSearching] = useState(false)
  const [loadingRequests, setLoadingRequests] = useState(false)
  const [busyUserId, setBusyUserId] = useState<string | null>(null)
  const [busyRequestId, setBusyRequestId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const memberLoadIdRef = useRef(0)
  const joinRequestLoadIdRef = useRef(0)

  const isAdmin = Boolean(profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'))
  const isProjectLeader = Boolean(
    profile && project?.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canManage = isAdmin || isProjectLeader

  const loadMembers = useCallback(async () => {
    if (!token || !projectId) return
    const loadId = ++memberLoadIdRef.current
    const [activeMembers, removedMembers] = await Promise.all([
      listProjectMembers(token, projectId, 'ACTIVE'),
      listProjectMembers(token, projectId, 'REMOVED'),
    ])
    if (loadId === memberLoadIdRef.current) setMembers([...activeMembers, ...removedMembers])
  }, [projectId, token])

  const loadJoinRequests = useCallback(async () => {
    if (!token || !projectId || !canManage) return
    const loadId = ++joinRequestLoadIdRef.current
    const result = await listProjectJoinRequests(token, projectId)
    if (loadId === joinRequestLoadIdRef.current) setJoinRequests(result)
  }, [canManage, projectId, token])

  useEffect(() => {
    setMembers([])
    memberLoadIdRef.current += 1
    setQuery('')
    setCandidates([])
    setError('')
    if (!projectId || !token) return

    let active = true
    setLoading(true)
    void loadMembers()
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được thành viên dự án.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [loadMembers, projectId, token])

  useEffect(() => {
    setJoinRequests([])
    joinRequestLoadIdRef.current += 1
    if (!canManage || !projectId || !token) return

    let active = true
    setLoadingRequests(true)
    void loadJoinRequests()
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được yêu cầu tham gia.'))
      })
      .finally(() => {
        if (active) setLoadingRequests(false)
      })
    return () => {
      active = false
      memberLoadIdRef.current += 1
      joinRequestLoadIdRef.current += 1
    }
  }, [canManage, loadJoinRequests, projectId, token])

  useEffect(() => {
    const normalizedQuery = query.trim()
    if (!token || !projectId || !canManage || normalizedQuery.length < 2) {
      setCandidates([])
      setSearching(false)
      return
    }

    const controller = new AbortController()
    const timeoutId = window.setTimeout(() => {
      setSearching(true)
      void listProjectMemberCandidates(token, projectId, normalizedQuery, controller.signal)
        .then(setCandidates)
        .catch((reason: unknown) => {
          if (!isAbortError(reason)) setError(errorMessage(reason, 'Không tìm được ứng viên thành viên.'))
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearching(false)
        })
    }, 250)

    return () => {
      window.clearTimeout(timeoutId)
      controller.abort()
    }
  }, [canManage, projectId, query, token])

  const visibleMembers = useMemo(
    () => members.filter((member) => filter === 'ALL' || member.status === filter),
    [filter, members],
  )
  const removedMemberIds = useMemo(
    () => new Set(members.filter((member) => member.status === 'REMOVED').map((member) => member.userId)),
    [members],
  )

  async function handleAdd(candidate: ProjectMemberCandidate) {
    if (!token || !project || !canManage || busyUserId) return
    const isReactivation = removedMemberIds.has(candidate.userId)
    setBusyUserId(candidate.userId)
    clearFeedback()
    try {
      await addProjectMember(token, project.id, candidate.userId)
      await loadMembers()
      setQuery('')
      setCandidates([])
      setFilter('ACTIVE')
      toast.success(isReactivation
        ? `Đã kích hoạt lại ${candidate.name} trong dự án.`
        : `Đã thêm ${candidate.name} vào dự án.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể thêm thành viên.')
      setError(message)
      toast.error('Không thể thêm thành viên', message)
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleRemove(member: ProjectMember) {
    if (!token || !project || !canManage || busyUserId || member.projectRole === 'LEADER') return
    if (!await confirmDialog({ title: 'Gỡ thành viên?', description: `Membership của ${member.name} sẽ được giữ lại ở trạng thái REMOVED để bảo toàn lịch sử tham gia.`, confirmLabel: 'Gỡ thành viên', destructive: true })) return

    setBusyUserId(member.userId)
    clearFeedback()
    try {
      await removeProjectMember(token, project.id, member.userId)
      await loadMembers()
      toast.success('Đã gỡ thành viên', `${member.name}; lịch sử membership vẫn được giữ lại.`)
    } catch (reason: unknown) {
      const message = errorMessage(reason, 'Không thể gỡ thành viên.')
      setError(message)
      toast.error('Không thể gỡ thành viên', message)
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleReview(request: ProjectJoinRequest, decision: 'APPROVE' | 'REJECT') {
    if (!token || !project || !canManage || busyRequestId || busyUserId) return
    const actionLabel = decision === 'APPROVE' ? 'chấp nhận' : 'từ chối'
    if (decision === 'APPROVE' && !await confirmDialog({ title: 'Chấp nhận yêu cầu?', description: `${request.requesterName} sẽ được thêm hoặc kích hoạt lại với vai trò thành viên.`, confirmLabel: 'Chấp nhận' })) return
    if (decision === 'REJECT' && !await confirmDialog({ title: 'Từ chối yêu cầu?', description: `Yêu cầu tham gia của ${request.requesterName} sẽ bị từ chối.`, confirmLabel: 'Từ chối', destructive: true })) return

    setBusyRequestId(request.id)
    clearFeedback()
    try {
      await reviewProjectJoinRequest(token, project.id, request.id, decision)
      setJoinRequests((current) => current.filter((item) => item.id !== request.id))
      if (decision === 'APPROVE') {
        setFilter('ACTIVE')
        try {
          await loadMembers()
        } catch (reason: unknown) {
          setError(errorMessage(reason, 'Yêu cầu đã được chấp nhận nhưng chưa tải lại được danh sách thành viên.'))
        }
      }
      toast.success(`Đã ${actionLabel} yêu cầu tham gia`, request.requesterName)
    } catch (reason: unknown) {
      const message = errorMessage(reason, `Không thể ${actionLabel} yêu cầu tham gia.`)
      setError(message)
      toast.error(`Không thể ${actionLabel} yêu cầu`, message)
    } finally {
      setBusyRequestId(null)
    }
  }

  function clearFeedback() {
    setError('')
  }

  if (!project) {
    return (
      <section className="panel page-section">
        <EmptyState title="Chưa chọn dự án" description="Chọn một dự án để xem thành viên và lịch sử tham gia." />
      </section>
    )
  }

  return (
    <section className="panel page-section">
      <div className="panel-head">
        <div>
          <h2>Thành viên dự án</h2>
          <p>Gỡ thành viên bằng REMOVED để không mất lịch sử tham gia.</p>
        </div>
        <UsersRound size={20} />
      </div>

      <Feedback error={error} />

      <div className="form-actions" style={{ marginBottom: 16 }}>
        <PopupSelect className="dense" ariaLabel="Lọc lịch sử thành viên" value={filter} onChange={(value) => setFilter(value as ProjectMemberFilter)} options={[{ value: 'ACTIVE', label: 'Đang tham gia' }, { value: 'REMOVED', label: 'Đã rời dự án' }, { value: 'ALL', label: 'Toàn bộ lịch sử' }]} />
        <button
          className="btn ghost table-btn"
          type="button"
          disabled={loading || Boolean(busyUserId)}
          onClick={() => {
            setLoading(true)
            clearFeedback()
            void loadMembers()
              .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải lại được thành viên.')))
              .finally(() => setLoading(false))
          }}
        >
          <RefreshCw /> Tải lại
        </button>
      </div>

      {loading ? <div className="empty tight">Đang tải thành viên...</div> : null}
      {!loading && visibleMembers.length ? (
        <div className="field-admin-list">
          {visibleMembers.map((member) => (
            <div className="field-admin-row" key={`${member.userId}-${member.status}`}>
              <div>
                <strong>{member.name}</strong>
                <span>{member.email}</span>
                <span>
                  Tham gia {formatDateTime(member.joinedAt)}
                  {member.removedAt ? ` · Rời dự án ${formatDateTime(member.removedAt)}` : ''}
                </span>
              </div>
              <span className="field-admin-actions">
                <span className={`badge ${member.projectRole === 'LEADER' ? 'info' : 'success'}`}>
                  {PROJECT_MEMBER_ROLE_LABELS[member.projectRole]}
                </span>
                <span className={`badge ${member.status === 'ACTIVE' ? 'success' : 'danger'}`}>
                  {PROJECT_MEMBER_STATUS_LABELS[member.status]}
                </span>
                {canManage && member.status === 'ACTIVE' ? (
                  <button
                    className="btn ghost table-btn danger-text"
                    type="button"
                    title={member.projectRole === 'LEADER' ? 'Hãy bỏ thành viên khỏi nhóm leader trước' : 'Gỡ khỏi dự án'}
                    disabled={Boolean(busyUserId) || member.projectRole === 'LEADER'}
                    onClick={() => void handleRemove(member)}
                  >
                    <Trash2 /> Gỡ
                  </button>
                ) : null}
                {canManage && member.status === 'REMOVED' ? (
                  <button
                    className="btn ghost table-btn"
                    type="button"
                    title="Kích hoạt lại thành viên"
                    disabled={Boolean(busyUserId)}
                    style={{ width: 'auto' }}
                    onClick={() => void handleAdd(member)}
                  >
                    <RotateCcw /> Kích hoạt lại
                  </button>
                ) : null}
              </span>
            </div>
          ))}
        </div>
      ) : null}
      {!loading && !visibleMembers.length ? (
        <EmptyState title="Không có thành viên" description="Không có membership phù hợp với bộ lọc hiện tại." />
      ) : null}

      {canManage ? (
        <div className="page-section project-join-review">
          <div className="panel-head">
            <div>
              <h3>Yêu cầu tham gia</h3>
              <p>Duyệt thành viên muốn tham gia dự án. Khi chấp nhận, membership sẽ được tạo hoặc kích hoạt lại.</p>
            </div>
            <span className="badge info">{joinRequests.length} chờ duyệt</span>
          </div>

          <div className="form-actions" style={{ marginBottom: 12 }}>
            <button
              className="btn ghost table-btn"
              type="button"
              disabled={loadingRequests || Boolean(busyRequestId) || Boolean(busyUserId)}
              onClick={() => {
                setLoadingRequests(true)
                clearFeedback()
                void loadJoinRequests()
                  .catch((reason: unknown) => setError(errorMessage(reason, 'Không tải lại được yêu cầu tham gia.')))
                  .finally(() => setLoadingRequests(false))
              }}
            >
              <RefreshCw aria-hidden="true" /> Tải lại yêu cầu
            </button>
          </div>

          {loadingRequests ? <div className="empty tight">Đang tải yêu cầu tham gia...</div> : null}
          {!loadingRequests && joinRequests.length ? (
            <div className="field-admin-list">
              {joinRequests.map((request) => (
                <article className="field-admin-row" key={request.id}>
                  <div>
                    <strong>{request.requesterName}</strong>
                    <span>{request.requesterEmail}</span>
                    <span>Gửi {formatDateTime(request.createdAt)}</span>
                    {request.message ? <p className="project-join-review-message">“{request.message}”</p> : null}
                  </div>
                  <span className="field-admin-actions">
                    <button
                      className="btn primary table-btn"
                      type="button"
                      disabled={Boolean(busyRequestId) || Boolean(busyUserId)}
                      onClick={() => void handleReview(request, 'APPROVE')}
                    >
                      {busyRequestId === request.id ? <UserCheck aria-hidden="true" /> : <Check aria-hidden="true" />}
                      {busyRequestId === request.id ? 'Đang xử lý...' : 'Chấp nhận'}
                    </button>
                    <button
                      className="btn ghost table-btn danger-text"
                      type="button"
                      disabled={Boolean(busyRequestId) || Boolean(busyUserId)}
                      onClick={() => void handleReview(request, 'REJECT')}
                    >
                      <X aria-hidden="true" /> Từ chối
                    </button>
                  </span>
                </article>
              ))}
            </div>
          ) : null}
          {!loadingRequests && !joinRequests.length ? (
            <EmptyState title="Không có yêu cầu chờ duyệt" description="Yêu cầu mới từ thành viên sẽ xuất hiện tại đây." />
          ) : null}
        </div>
      ) : null}

      {canManage ? (
        <div className="page-section">
          <label className="field">
            <span>Thêm hoặc kích hoạt lại thành viên</span>
            <div className="searchbar">
              <Search aria-hidden="true" />
              <input
                className="input"
                type="search"
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value)
                  clearFeedback()
                }}
                placeholder="Nhập ít nhất 2 ký tự tên hoặc email..."
              />
            </div>
          </label>
          {searching ? <p className="muted small">Đang tìm thành viên...</p> : null}
          {!searching && query.trim().length >= 2 && !candidates.length ? (
            <p className="muted small">Không tìm thấy tài khoản có thể thêm.</p>
          ) : null}
          {candidates.length ? (
            <div className="field-admin-list" style={{ marginTop: 10 }}>
              {candidates.map((candidate) => (
                <button
                  className="member-select-row"
                  type="button"
                  disabled={Boolean(busyUserId)}
                  onClick={() => void handleAdd(candidate)}
                  key={candidate.userId}
                >
                  <span className="member-avatar">{initialsOf(candidate.name)}</span>
                  <span><strong>{candidate.name}</strong><small>{candidate.email}</small></span>
                  <span className="badge info">
                    {removedMemberIds.has(candidate.userId)
                      ? <><RotateCcw size={13} /> Kích hoạt lại</>
                      : <><Plus size={13} /> Thêm</>}
                  </span>
                </button>
              ))}
            </div>
          ) : null}
        </div>
      ) : (
        <p className="muted small" style={{ marginTop: 16 }}>
          Chỉ Admin hoặc leader đang hoạt động của dự án có thể thêm và gỡ thành viên.
        </p>
      )}
    </section>
  )
}

function initialsOf(name: string) {
  return name.trim().split(/\s+/).filter(Boolean).slice(-2).map((part) => part.charAt(0)).join('').toLocaleUpperCase('vi')
}

function formatDateTime(value: string | null) {
  if (!value) return 'chưa rõ'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function isAbortError(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
