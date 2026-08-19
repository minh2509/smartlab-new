import { Save, Undo2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { updateProjectLeadership } from '../api'
import type { Project, ProjectLeaderCandidate } from '../types'
import { ProjectLeaderPicker } from './ProjectLeaderPicker'
import { confirmDialog } from '../../../shared/ui/projectConfirmDialog'

type ProjectLeadershipEditorProps = {
  project: Project
  token: string
  disabled: boolean
  onClearFeedback: () => void
  onError: (message: string) => void
  onSaved: (project: Project) => void
  onSavingChange: (saving: boolean) => void
  onDirtyChange?: (dirty: boolean) => void
}

export function ProjectLeadershipEditor({
  project,
  token,
  disabled,
  onClearFeedback,
  onError,
  onSaved,
  onSavingChange,
  onDirtyChange,
}: ProjectLeadershipEditorProps) {
  const [leaders, setLeaders] = useState<ProjectLeaderCandidate[]>(() => toDraftLeaders(project))
  const [primaryLeaderUserId, setPrimaryLeaderUserId] = useState<string | null>(
    project.primaryLeader?.userId ?? null,
  )
  const [saving, setSaving] = useState(false)

  const dirty = useMemo(
    () => !sameLeadership(project, leaders, primaryLeaderUserId),
    [leaders, primaryLeaderUserId, project],
  )

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange])

  function resetDraft() {
    setLeaders(toDraftLeaders(project))
    setPrimaryLeaderUserId(project.primaryLeader?.userId ?? null)
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!dirty || disabled || saving) return

    const leaderUserIds = leaders.map((leader) => leader.userId)
    if (primaryLeaderUserId && !leaderUserIds.includes(primaryLeaderUserId)) {
      onError('Leader chính phải nằm trong nhóm leader.')
      return
    }

    const removedLeaders = project.leaders.filter((leader) => !leaderUserIds.includes(leader.userId))
    if (removedLeaders.length > 0) {
      const removesAll = leaderUserIds.length === 0
      const removesPrimary = Boolean(
        project.primaryLeader
          && removedLeaders.some((leader) => leader.userId === project.primaryLeader?.userId),
      )
      const description = removesAll
        ? `Tất cả leader sẽ được gỡ khỏi ${project.code}. Họ vẫn là thành viên dự án nhưng không còn quyền leader.`
        : `${removedLeaders.length} leader sẽ được gỡ khỏi ${project.code}${removesPrimary ? ', bao gồm leader chính hiện tại' : ''}. Họ vẫn là thành viên dự án.`
      if (!await confirmDialog({ title: 'Cập nhật nhóm leader?', description, confirmLabel: 'Cập nhật leader', destructive: true })) return
    }

    setSaving(true)
    onSavingChange(true)
    onClearFeedback()
    try {
      const updated = await updateProjectLeadership(token, project.id, {
        primaryLeaderUserId,
        leaderUserIds,
      })
      onSaved(updated)
    } catch (reason: unknown) {
      onError(errorMessage(reason, 'Không thể cập nhật nhóm leader.'))
    } finally {
      setSaving(false)
      onSavingChange(false)
    }
  }

  return (
    <form className="panel page-section project-leadership-editor" onSubmit={handleSave} noValidate>
      <div className="project-leadership-header">
        <div>
          <h2>Nhóm leader</h2>
          <p>Tìm thành viên, sau đó chỉ định người phụ trách chính nếu cần.</p>
        </div>
        <span className="muted small">{leaders.length} leader</span>
      </div>

      <ProjectLeaderPicker
        idPrefix={`project-${project.id}-leadership`}
        token={token}
        leaders={leaders}
        primaryLeaderUserId={primaryLeaderUserId}
        disabled={disabled || saving}
        onLeadersChange={setLeaders}
        onPrimaryLeaderChange={setPrimaryLeaderUserId}
      />

      <div className="project-overview-action-footer project-leadership-action-footer">
        <span className="muted small" aria-live="polite">
          {dirty ? 'Có thay đổi chưa lưu.' : 'Nhóm leader đã được đồng bộ.'}
        </span>
        <div className="form-actions">
          <button className="btn ghost" type="button" disabled={!dirty || disabled || saving} onClick={resetDraft}><Undo2 aria-hidden="true" /> Hủy thay đổi</button>
          <button className="btn primary" type="submit" disabled={!dirty || disabled || saving}><Save aria-hidden="true" />{saving ? 'Đang lưu...' : 'Lưu thay đổi leader'}</button>
        </div>
      </div>
    </form>
  )
}

function toDraftLeaders(project: Project): ProjectLeaderCandidate[] {
  const leaders = project.leaders.map((leader) => ({ ...leader, email: '' }))
  const primary = project.primaryLeader
  if (primary && !leaders.some((leader) => leader.userId === primary.userId)) {
    leaders.unshift({ ...primary, email: '' })
  }
  return leaders
}

function sameLeadership(
  project: Project,
  leaders: ProjectLeaderCandidate[],
  primaryLeaderUserId: string | null,
) {
  const currentIds = project.leaders.map((leader) => leader.userId).sort()
  const draftIds = leaders.map((leader) => leader.userId).sort()
  return (
    (project.primaryLeader?.userId ?? null) === primaryLeaderUserId
    && currentIds.length === draftIds.length
    && currentIds.every((userId, index) => userId === draftIds[index])
  )
}

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof Error && reason.message ? reason.message : fallback
}
