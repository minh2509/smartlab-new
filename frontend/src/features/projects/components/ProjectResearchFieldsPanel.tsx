import { FlaskConical, RotateCcw, Save } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import type { ResearchField } from '../../../shared/types/api'
import { useAuth } from '../../auth/authContext'
import { getResearchFields } from '../../profile/api'
import { listProjectResearchFields, replaceProjectResearchFields } from '../api'
import type { Project, ProjectResearchField } from '../types'

type ProjectResearchFieldsPanelProps = {
  project: Project | null
  onDirtyChange?: (dirty: boolean) => void
}

export function ProjectResearchFieldsPanel({ project, onDirtyChange }: ProjectResearchFieldsPanelProps) {
  const { token, profile } = useAuth()
  const projectId = project?.id ?? null
  const [catalog, setCatalog] = useState<ResearchField[]>([])
  const [assignedFields, setAssignedFields] = useState<ProjectResearchField[]>([])
  const [selectedFieldIds, setSelectedFieldIds] = useState<number[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const isAdmin = Boolean(profile?.roles.includes('ADMIN') && profile.permissions.includes('PROJECT_MANAGE'))
  const isProjectLeader = Boolean(
    profile && project?.leaders.some((leader) => leader.userId === profile.userId),
  )
  const canManage = isAdmin || isProjectLeader
  const availableFields = useMemo(() => {
    const fields = new Map<number, ProjectResearchField>()
    catalog.forEach((field) => fields.set(field.id, {
      id: field.id,
      code: field.code,
      name: field.name,
      isActive: field.isActive,
    }))
    assignedFields.forEach((field) => fields.set(field.id, field))
    return [...fields.values()].sort((left, right) => left.name.localeCompare(right.name, 'vi'))
  }, [assignedFields, catalog])
  const dirty = useMemo(
    () => normalizeIds(selectedFieldIds) !== normalizeIds(assignedFields.map((field) => field.id)),
    [assignedFields, selectedFieldIds],
  )

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  const loadFields = useCallback(async () => {
    if (!token || !projectId) return
    const [catalogResult, assignedResult] = await Promise.all([
      getResearchFields(),
      listProjectResearchFields(token, projectId),
    ])
    setCatalog(catalogResult)
    setAssignedFields(assignedResult)
    setSelectedFieldIds(assignedResult.map((field) => field.id))
  }, [projectId, token])

  useEffect(() => {
    setCatalog([])
    setAssignedFields([])
    setSelectedFieldIds([])
    setMessage('')
    setError('')
    if (!projectId || !token) return

    let active = true
    setLoading(true)
    void loadFields()
      .catch((reason: unknown) => {
        if (active) setError(errorMessage(reason, 'Không tải được lĩnh vực của dự án.'))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [loadFields, projectId, token])

  async function handleSave() {
    if (!token || !project || !canManage || saving || !dirty) return
    setSaving(true)
    clearFeedback()
    try {
      const updated = await replaceProjectResearchFields(token, project.id, selectedFieldIds)
      setAssignedFields(updated)
      setSelectedFieldIds(updated.map((field) => field.id))
      setMessage('Đã cập nhật lĩnh vực nghiên cứu của dự án.')
    } catch (reason: unknown) {
      setError(errorMessage(reason, 'Không thể cập nhật lĩnh vực nghiên cứu.'))
    } finally {
      setSaving(false)
    }
  }

  function toggleField(fieldId: number) {
    if (!canManage || saving) return
    clearFeedback()
    setSelectedFieldIds((current) => current.includes(fieldId)
      ? current.filter((id) => id !== fieldId)
      : [...current, fieldId])
  }

  function resetDraft() {
    clearFeedback()
    setSelectedFieldIds(assignedFields.map((field) => field.id))
  }

  function clearFeedback() {
    setMessage('')
    setError('')
  }

  if (!project) {
    return (
      <section className="panel page-section">
        <EmptyState title="Chưa chọn dự án" description="Chọn một dự án để xem và gắn lĩnh vực nghiên cứu." />
      </section>
    )
  }

  return (
    <section className="panel page-section">
      <div className="panel-head">
        <div>
          <h2>Lĩnh vực nghiên cứu</h2>
          <p>Một dự án có thể thuộc nhiều lĩnh vực; lưu sẽ thay toàn bộ danh sách hiện tại.</p>
        </div>
        <FlaskConical size={20} />
      </div>

      <Feedback message={message} error={error} />

      {loading ? <div className="empty tight">Đang tải lĩnh vực...</div> : null}
      {!loading ? (
        <>
          <div className="inline-badges" style={{ marginBottom: 16 }}>
            {assignedFields.map((field) => (
              <span className={`badge ${field.isActive ? 'info' : 'danger'}`} key={field.id}>
                {field.name}{field.isActive ? '' : ' · Đã tắt'}
              </span>
            ))}
            {!assignedFields.length ? <span className="muted small">Dự án chưa có lĩnh vực nghiên cứu.</span> : null}
          </div>

          <div className="field-options">
            {availableFields.map((field) => {
              const selected = selectedFieldIds.includes(field.id)
              return (
                <label className={`field-option ${selected ? 'selected' : ''}`} key={field.id}>
                  <input
                    type="checkbox"
                    checked={selected}
                    disabled={
                      !canManage
                      || saving
                      || (!field.isActive && !assignedFields.some((assigned) => assigned.id === field.id))
                    }
                    onChange={() => toggleField(field.id)}
                  />
                  <span>
                    <strong>{field.name}{!field.isActive ? ' · Đã tắt' : ''}</strong>
                    <small>{field.code}{catalog.find((item) => item.id === field.id)?.description ? ` · ${catalog.find((item) => item.id === field.id)?.description}` : ''}</small>
                  </span>
                </label>
              )
            })}
          </div>
          {!availableFields.length ? (
            <EmptyState title="Chưa có lĩnh vực đang hoạt động" description="Catalog lĩnh vực nghiên cứu hiện đang trống." />
          ) : null}

          {canManage ? (
            <div className="form-actions" style={{ marginTop: 16 }}>
              <button className="btn primary" type="button" disabled={!dirty || saving} onClick={() => void handleSave()}>
                <Save /> {saving ? 'Đang lưu...' : 'Lưu lĩnh vực'}
              </button>
              <button className="btn ghost" type="button" disabled={!dirty || saving} onClick={resetDraft}>
                <RotateCcw /> Hoàn tác
              </button>
              <span className="muted small">Có thể bỏ chọn tất cả để xóa toàn bộ liên kết.</span>
            </div>
          ) : (
            <p className="muted small" style={{ marginTop: 16 }}>
              Chỉ Admin hoặc leader đang hoạt động của dự án có thể thay đổi lĩnh vực.
            </p>
          )}
        </>
      ) : null}
    </section>
  )
}

function normalizeIds(ids: number[]) {
  return [...new Set(ids)].sort((left, right) => left - right).join(',')
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
