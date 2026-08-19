import { CalendarDays, ClipboardList, UserRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { EmptyState } from '../../../shared/components/EmptyState'
import { Feedback } from '../../../shared/components/Feedback'
import { PopupSelect } from '../../../shared/ui/PopupSelect'
import { useAuth } from '../../auth/authContext'
import { getMyEvaluations } from '../api'
import type { Evaluation } from '../types'
import './MyEvaluationsPage.css'

type EvaluationSort = 'NEWEST' | 'OLDEST' | 'HIGHEST_SCORE' | 'LOWEST_SCORE'
type ProjectAverage = {
  projectId: number
  projectName: string
  evaluationCount: number
  averageScore: number | null
}

const SORT_OPTIONS = [
  { value: 'NEWEST', label: 'Mới nhất' },
  { value: 'OLDEST', label: 'Cũ nhất' },
  { value: 'HIGHEST_SCORE', label: 'Điểm cao nhất' },
  { value: 'LOWEST_SCORE', label: 'Điểm thấp nhất' },
]

export function MyEvaluationsPage() {
  const { token } = useAuth()
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [projectFilter, setProjectFilter] = useState('ALL')
  const [sort, setSort] = useState<EvaluationSort>('NEWEST')

  useEffect(() => {
    let active = true

    if (!token) {
      setEvaluations([])
      setLoading(false)
      return () => { active = false }
    }

    setLoading(true)
    setError('')
    void getMyEvaluations(token)
      .then((result) => {
        if (active) setEvaluations(result)
      })
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách đánh giá.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [token])

  const projectOptions = useMemo(() => {
    const projects = new Map<number, string>()
    evaluations.forEach((evaluation) => {
      if (!projects.has(evaluation.projectId)) projects.set(evaluation.projectId, evaluation.projectName)
    })
    return [
      { value: 'ALL', label: 'Tất cả dự án' },
      ...Array.from(projects, ([id, label]) => ({ value: String(id), label })),
    ]
  }, [evaluations])

  const visibleEvaluations = useMemo(() => {
    const filtered = projectFilter === 'ALL'
      ? evaluations
      : evaluations.filter((evaluation) => String(evaluation.projectId) === projectFilter)

    return [...filtered].sort((left, right) => compareEvaluations(left, right, sort))
  }, [evaluations, projectFilter, sort])
  const summary = useMemo(() => buildSummary(visibleEvaluations), [visibleEvaluations])
  const projectAverages = useMemo(() => buildProjectAverages(evaluations), [evaluations])
  const allProjects = projectFilter === 'ALL'

  return (
    <div className="my-evaluations-page">
      <header className="page-title my-evaluations-header">
        <div>
          <h1>Đánh giá của tôi</h1>
          <p>Theo dõi kết quả đánh giá và nhận xét từ các dự án bạn tham gia.</p>
        </div>
      </header>

      {error ? <Feedback error={error} /> : loading ? <EvaluationLoadingState /> : evaluations.length === 0 ? (
        <EmptyState title="Chưa có đánh giá" description="Các đánh giá từ dự án bạn tham gia sẽ xuất hiện tại đây." />
      ) : (
        <>
          {visibleEvaluations.length > 0 ? (
            <>
              <section className="evaluation-summary-strip" aria-label={allProjects ? 'Tổng quan đánh giá' : 'Tổng quan đánh giá theo dự án'}>
                <SummaryItem label="Đánh giá" value={`${summary.total} đánh giá`} />
                <SummaryItem
                  label={allProjects ? 'Điểm TB tổng' : 'Điểm TB dự án'}
                  value={summary.averageScore === null ? '--' : `${formatScore(summary.averageScore)} / 10`}
                />
                <SummaryItem label="Gần nhất" value={summary.latestDate ? formatDate(summary.latestDate) : '--'} />
              </section>

              {allProjects ? <ProjectAverageSection averages={projectAverages} /> : null}
            </>
          ) : null}

          <section className="evaluation-toolbar" aria-label="Lọc và sắp xếp đánh giá">
            <div className="evaluation-toolbar-field">
              <span>Dự án</span>
              <PopupSelect
                className="evaluation-popup-select"
                value={projectFilter}
                onChange={setProjectFilter}
                ariaLabel="Lọc đánh giá theo dự án"
                options={projectOptions}
              />
            </div>
            <div className="evaluation-toolbar-field">
              <span>Sắp xếp</span>
              <PopupSelect
                className="evaluation-popup-select"
                value={sort}
                onChange={(value) => setSort(value as EvaluationSort)}
                ariaLabel="Sắp xếp đánh giá"
                options={SORT_OPTIONS}
              />
            </div>
          </section>

          {visibleEvaluations.length === 0 ? (
            <EmptyState title="Không tìm thấy đánh giá" description="Không có đánh giá nào khớp với dự án đã chọn." />
          ) : (
            <div className="evaluation-record-list">
              {visibleEvaluations.map((evaluation) => <EvaluationRecord key={evaluation.id} evaluation={evaluation} />)}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function EvaluationRecord({ evaluation }: { evaluation: Evaluation }) {
  const totalScore = evaluation.scores.reduce((sum, item) => sum + item.score, 0)
  const totalMaxScore = evaluation.scores.reduce((sum, item) => sum + item.maxScore, 0)

  return (
    <article className="evaluation-record" aria-labelledby={`evaluation-project-${evaluation.id}`}>
      <header className="evaluation-record-header">
        <div className="evaluation-record-context">
          <h2 id={`evaluation-project-${evaluation.id}`}>{evaluation.projectName}</h2>
          <div className="evaluation-record-meta">
            <span><CalendarDays aria-hidden="true" />{formatDate(evaluation.createdAt)}</span>
            <span><UserRound aria-hidden="true" />{evaluation.evaluatorName}</span>
          </div>
        </div>
        <div className="evaluation-total-score" aria-label={`Tổng điểm ${formatScore(totalScore)} trên ${formatScore(totalMaxScore)}`}>
          <span>Tổng điểm</span>
          <strong>{formatScore(totalScore)} / {formatScore(totalMaxScore)}</strong>
        </div>
      </header>

      {evaluation.note ? (
        <section className="evaluation-feedback" aria-labelledby={`evaluation-note-${evaluation.id}`}>
          <h3 id={`evaluation-note-${evaluation.id}`}>Nhận xét</h3>
          <p>{evaluation.note}</p>
        </section>
      ) : null}

      <section className="evaluation-criteria" aria-labelledby={`evaluation-criteria-${evaluation.id}`}>
        <h3 id={`evaluation-criteria-${evaluation.id}`}><ClipboardList size={16} aria-hidden="true" />Chi tiết đánh giá</h3>
        <div className="evaluation-score-list">
          {evaluation.scores.map((score, index) => (
            <div className="evaluation-score-row" key={`${score.criterionId}-${index}`}>
              <div>
                <strong>{score.criterionName}</strong>
                {score.note ? <p>{score.note}</p> : null}
              </div>
              <span aria-label={`${score.criterionName}: ${formatScore(score.score)} trên ${formatScore(score.maxScore)}`}>
                {formatScore(score.score)} / {formatScore(score.maxScore)}
              </span>
            </div>
          ))}
        </div>
      </section>
    </article>
  )
}

function EvaluationLoadingState() {
  return (
    <div className="evaluation-loading" aria-live="polite" aria-label="Đang tải danh sách đánh giá">
      <span className="evaluation-skeleton evaluation-skeleton-summary" />
      <span className="evaluation-skeleton evaluation-skeleton-toolbar" />
      {[0, 1].map((item) => <span className="evaluation-skeleton evaluation-skeleton-record" key={item} />)}
    </div>
  )
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong>{value}</strong></div>
}

function ProjectAverageSection({ averages }: { averages: ProjectAverage[] }) {
  return (
    <section className="project-average-section" aria-labelledby="project-average-heading">
      <h2 id="project-average-heading">Điểm trung bình theo dự án</h2>
      <div className="project-average-list">
        {averages.map((project) => (
          <div className="project-average-row" key={project.projectId}>
            <div>
              <strong>{project.projectName}</strong>
              <span>{project.evaluationCount} đánh giá</span>
            </div>
            <output aria-label={`Điểm trung bình dự án ${project.projectName}: ${project.averageScore === null ? 'chưa có điểm hợp lệ' : `${formatScore(project.averageScore)} trên 10`}`}>
              {project.averageScore === null ? '--' : `${formatScore(project.averageScore)} / 10`}
            </output>
          </div>
        ))}
      </div>
    </section>
  )
}

function buildSummary(evaluations: Evaluation[]) {
  const normalizedScores = evaluations.flatMap((evaluation) => {
    const score = normalizedScore(evaluation)
    return score === null ? [] : [score]
  })
  const latest = evaluations.reduce<string | null>((current, evaluation) => (
    !current || timestamp(evaluation.createdAt) > timestamp(current) ? evaluation.createdAt : current
  ), null)

  return {
    total: evaluations.length,
    averageScore: normalizedScores.length
      ? normalizedScores.reduce((sum, score) => sum + score, 0) / normalizedScores.length
      : null,
    latestDate: latest,
  }
}

function buildProjectAverages(evaluations: Evaluation[]) {
  const projects = new Map<number, { projectName: string; scores: number[]; evaluationCount: number }>()

  evaluations.forEach((evaluation) => {
    const project = projects.get(evaluation.projectId) ?? {
      projectName: evaluation.projectName,
      scores: [],
      evaluationCount: 0,
    }
    const score = normalizedScore(evaluation)
    if (score !== null) project.scores.push(score)
    project.evaluationCount += 1
    projects.set(evaluation.projectId, project)
  })

  return Array.from(projects, ([projectId, project]) => ({
    projectId,
    projectName: project.projectName,
    evaluationCount: project.evaluationCount,
    averageScore: project.scores.length
      ? project.scores.reduce((sum, score) => sum + score, 0) / project.scores.length
      : null,
  }))
}

function normalizedScore(evaluation: Evaluation) {
  const totalScore = evaluation.scores.reduce((sum, item) => sum + item.score, 0)
  const totalMaxScore = evaluation.scores.reduce((sum, item) => sum + item.maxScore, 0)
  return totalMaxScore > 0 ? (totalScore / totalMaxScore) * 10 : null
}

function compareEvaluations(left: Evaluation, right: Evaluation, sort: EvaluationSort) {
  const leftTotal = totalScore(left)
  const rightTotal = totalScore(right)

  switch (sort) {
    case 'OLDEST': return timestamp(left.createdAt) - timestamp(right.createdAt)
    case 'HIGHEST_SCORE': return rightTotal - leftTotal || timestamp(right.createdAt) - timestamp(left.createdAt)
    case 'LOWEST_SCORE': return leftTotal - rightTotal || timestamp(right.createdAt) - timestamp(left.createdAt)
    case 'NEWEST': return timestamp(right.createdAt) - timestamp(left.createdAt)
  }

  return 0
}

function totalScore(evaluation: Evaluation) {
  return evaluation.scores.reduce((sum, item) => sum + item.score, 0)
}

function timestamp(value: string) {
  const result = new Date(value).getTime()
  return Number.isNaN(result) ? 0 : result
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('vi-VN')
}

function formatScore(value: number) {
  return Number.isFinite(value) ? value.toFixed(1) : String(value)
}
