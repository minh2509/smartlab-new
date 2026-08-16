import { Plus, RefreshCw, RotateCcw, Search, Trash2, UsersRound } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { useAuth } from '../../auth/authContext'
import {
  addProjectMember,
  listProjectMemberCandidates,
  listProjectMembers,
  removeProjectMember,
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
} from '../types'

export function ProjectMembersPanel({ project }: { project: Project | null }) {
  const { token, profile } = useAuth()
  const projectId = project?.id ?? null
  const [members, setMembers] = useState<ProjectMember[]>([])
  const [filter, setFilter] = useState<ProjectMemberFilter>('ACTIVE')
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<ProjectMemberCandidate[]>([])
  const [loading, setLoading] = useState(false)
  const [searching, setSearching] = useState(false)
  const [busyUserId, setBusyUserId] = useState<string | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const isAdmin = Boolean(profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'))
  const isProjectLeader = Boolean(
    profile && project?.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canManage = isAdmin || isProjectLeader

  const loadMembers = useCallback(async () => {
    if (!token || !projectId) return
    const [activeMembers, removedMembers] = await Promise.all([
      listProjectMembers(token, projectId, 'ACTIVE'),
      listProjectMembers(token, projectId, 'REMOVED'),
    ])
    setMembers([...activeMembers, ...removedMembers])
  }, [projectId, token])

  useEffect(() => {
    setMembers([])
    setQuery('')
    setCandidates([])
    setMessage('')
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
      setMessage(isReactivation
        ? `Đã kích hoạt lại ${candidate.name} trong dự án.`
        : `Đã thêm ${candidate.name} vào dự án.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể thêm thành viên.'))
    } finally {
      setBusyUserId(null)
    }
  }

  async function handleRemove(member: ProjectMember) {
    if (!token || !project || !canManage || busyUserId || member.projectRole === 'LEADER') return
    if (!window.confirm(`Gỡ “${member.name}” khỏi dự án? Membership sẽ được giữ lại ở trạng thái REMOVED.`)) return

    setBusyUserId(member.userId)
    clearFeedback()
    try {
      await removeProjectMember(token, project.id, member.userId)
      await loadMembers()
      setMessage(`Đã gỡ ${member.name}; lịch sử membership vẫn được giữ lại.`)
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể gỡ thành viên.'))
    } finally {
      setBusyUserId(null)
    }
  }

  function clearFeedback() {
    setMessage('')
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

      <Feedback message={message} error={error} />

      <div className="form-actions" style={{ marginBottom: 16 }}>
        <select
          className="select dense"
          aria-label="Lọc lịch sử thành viên"
          value={filter}
          onChange={(event) => setFilter(event.target.value as ProjectMemberFilter)}
        >
          <option value="ACTIVE">Đang tham gia</option>
          <option value="REMOVED">Đã rời dự án</option>
          <option value="ALL">Toàn bộ lịch sử</option>
        </select>
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
