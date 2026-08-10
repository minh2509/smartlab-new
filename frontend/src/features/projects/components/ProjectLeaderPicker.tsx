import { Plus, Search, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { listProjectLeaderCandidates } from '../api'
import type { ProjectLeaderCandidate } from '../types'

type ProjectLeaderPickerProps = {
  idPrefix: string
  token: string
  leaders: ProjectLeaderCandidate[]
  primaryLeaderUserId: string | null
  disabled?: boolean
  onLeadersChange: (leaders: ProjectLeaderCandidate[]) => void
  onPrimaryLeaderChange: (userId: string | null) => void
}

const MIN_SEARCH_LENGTH = 2
const MAX_LEADERS = 100

export function ProjectLeaderPicker({
  idPrefix,
  token,
  leaders,
  primaryLeaderUserId,
  disabled = false,
  onLeadersChange,
  onPrimaryLeaderChange,
}: ProjectLeaderPickerProps) {
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<ProjectLeaderCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const normalizedQuery = query.trim()
  const reachedLeaderLimit = leaders.length >= MAX_LEADERS

  useEffect(() => {
    setQuery('')
    setCandidates([])
    setSearchError('')
  }, [idPrefix])

  useEffect(() => {
    if (normalizedQuery.length < MIN_SEARCH_LENGTH) {
      setCandidates([])
      setSearching(false)
      setSearchError('')
      return
    }

    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setSearching(true)
      setSearchError('')
      void listProjectLeaderCandidates(token, normalizedQuery, controller.signal)
        .then(setCandidates)
        .catch((reason: unknown) => {
          if (!isAbortError(reason)) {
            setCandidates([])
            setSearchError(errorMessage(reason, 'Không thể tìm ứng viên leader.'))
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearching(false)
        })
    }, 300)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [normalizedQuery, token])

  const selectedIds = useMemo(() => new Set(leaders.map((leader) => leader.userId)), [leaders])
  const visibleCandidates = useMemo(
    () => candidates.filter((candidate) => !selectedIds.has(candidate.userId)),
    [candidates, selectedIds],
  )

  function addLeader(candidate: ProjectLeaderCandidate) {
    if (selectedIds.has(candidate.userId) || reachedLeaderLimit) return
    onLeadersChange([...leaders, candidate])
    if (primaryLeaderUserId === null) onPrimaryLeaderChange(candidate.userId)
    setQuery('')
    setCandidates([])
    setSearchError('')
  }

  function removeLeader(userId: string) {
    onLeadersChange(leaders.filter((leader) => leader.userId !== userId))
    if (primaryLeaderUserId === userId) onPrimaryLeaderChange(null)
  }

  return (
    <div className="form-stack">
      <div className="field">
        <label htmlFor={`${idPrefix}-search`}>Tìm thành viên</label>
        <div className="searchbar">
          <Search aria-hidden="true" />
          <input
            className="input"
            id={`${idPrefix}-search`}
            type="search"
            autoComplete="off"
            value={query}
            disabled={disabled || reachedLeaderLimit}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') event.preventDefault()
            }}
            placeholder="Nhập tên hoặc email..."
            aria-describedby={`${idPrefix}-search-help`}
          />
        </div>
        <small className="muted" id={`${idPrefix}-search-help`}>
          Nhập ít nhất {MIN_SEARCH_LENGTH} ký tự; hệ thống sẽ tự xử lý mã thành viên.
        </small>
      </div>

      {reachedLeaderLimit ? (
        <p className="field-error" role="status">
          Mỗi dự án có tối đa {MAX_LEADERS} leader. Hãy gỡ một người trước khi thêm leader mới.
        </p>
      ) : null}

      <div aria-live="polite" aria-atomic="true">
        {searching ? <p className="muted small" role="status">Đang tìm thành viên...</p> : null}
        {searchError ? <p className="danger-text small" role="alert">{searchError}</p> : null}
        {!searching && !searchError && normalizedQuery.length >= MIN_SEARCH_LENGTH && candidates.length === 0 ? (
          <p className="muted small">Không tìm thấy thành viên phù hợp.</p>
        ) : null}
        {!searching && !searchError && candidates.length > 0 && visibleCandidates.length === 0 ? (
          <p className="muted small">Tất cả thành viên tìm thấy đã có trong nhóm leader.</p>
        ) : null}
      </div>

      {visibleCandidates.length > 0 ? (
        <div className="field-admin-list" aria-label="Kết quả tìm ứng viên leader">
          {visibleCandidates.map((candidate) => (
            <button
              className="member-select-row"
              type="button"
              key={candidate.userId}
              disabled={disabled || reachedLeaderLimit}
              onClick={() => addLeader(candidate)}
              aria-label={`Thêm ${candidate.name} vào nhóm leader`}
            >
              <span className="member-avatar">{initialsOf(candidate.name)}</span>
              <span>
                <strong>{candidate.name}</strong>
                <small>{candidate.email}</small>
              </span>
              <span className="badge info"><Plus size={13} aria-hidden="true" /> Thêm</span>
            </button>
          ))}
        </div>
      ) : null}

      <div>
        <span className="field-label">Nhóm leader đã chọn ({leaders.length})</span>
        <div className="field-admin-list" role="radiogroup" aria-label="Chọn leader chính" style={{ marginTop: 10 }}>
          <label className={`field-option ${primaryLeaderUserId === null ? 'selected' : ''}`}>
            <input
              type="radio"
              name={`${idPrefix}-primary`}
              checked={primaryLeaderUserId === null}
              disabled={disabled}
              onChange={() => onPrimaryLeaderChange(null)}
            />
            <span>
              <strong>Chưa chọn leader chính</strong>
              <small>Nhóm vẫn có thể có nhiều leader mà chưa chỉ định người phụ trách chính.</small>
            </span>
          </label>

          {leaders.map((leader) => {
            const isPrimary = primaryLeaderUserId === leader.userId
            return (
              <div className="row gap-8" key={leader.userId}>
                <label className={`field-option ${isPrimary ? 'selected' : ''}`} style={{ flex: 1 }}>
                  <input
                    type="radio"
                    name={`${idPrefix}-primary`}
                    checked={isPrimary}
                    disabled={disabled}
                    onChange={() => onPrimaryLeaderChange(leader.userId)}
                  />
                  <span>
                    <strong>{leader.name}</strong>
                    <small>{leader.email || 'Thành viên hiện tại của dự án'}</small>
                  </span>
                  {isPrimary ? <strong className="badge success">Leader chính</strong> : null}
                </label>
                <button
                  className="btn ghost table-btn danger-text"
                  type="button"
                  disabled={disabled}
                  onClick={() => removeLeader(leader.userId)}
                  title={`Gỡ ${leader.name}`}
                  aria-label={`Gỡ ${leader.name} khỏi nhóm leader`}
                >
                  <Trash2 aria-hidden="true" />
                </button>
              </div>
            )
          })}
        </div>
      </div>

      {leaders.length === 0 ? (
        <p className="muted small">Chưa có leader. Hãy tìm và thêm thành viên ở phía trên.</p>
      ) : null}
    </div>
  )
}

function initialsOf(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toUpperCase() || 'U'
}

function isAbortError(reason: unknown) {
  return reason instanceof DOMException && reason.name === 'AbortError'
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
