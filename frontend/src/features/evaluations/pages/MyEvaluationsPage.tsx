import { AlertCircle, Award, Calendar, CheckCircle2, User } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useAuth } from '../../auth/authContext'
import { getMyEvaluations } from '../api'
import type { Evaluation } from '../types'

export function MyEvaluationsPage() {
  const { token } = useAuth()
  const [evaluations, setEvaluations] = useState<Evaluation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    setLoading(true)
    getMyEvaluations(token)
      .then((res) => setEvaluations(res))
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false))
  }, [token])

  return (
    <div className="admin-page-wrap">
      <header className="page-head">
        <div>
          <h1>Màn hình Xem đánh giá của tôi (D4)</h1>
          <p>Danh sách kết quả chấm điểm và đánh giá đóng góp cá nhân từ các dự án bạn tham gia.</p>
        </div>
      </header>

      {error && (
        <div className="feedback error" style={{ margin: '16px 0' }}>
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="panel" style={{ textAlign: 'center', padding: 40 }}>Đang tải danh sách đánh giá...</div>
      ) : evaluations.length === 0 ? (
        <div className="panel" style={{ textAlign: 'center', padding: 40, color: 'var(--text-3)' }}>
          Bạn chưa có bản đánh giá nào.
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 20 }}>
          {evaluations.map((ev) => {
            const totalScore = ev.scores.reduce((acc, s) => acc + s.score, 0)
            const totalMaxScore = ev.scores.reduce((acc, s) => acc + s.maxScore, 0)
            const percent = totalMaxScore > 0 ? Math.round((totalScore / totalMaxScore) * 100) : 0

            return (
              <div key={ev.id} className="panel">
                <div
                  style={{
                    display: 'flex',
                    justify: 'space-between',
                    alignItems: 'flex-start',
                    borderBottom: '1px solid var(--border)',
                    paddingBottom: 12,
                    marginBottom: 16,
                  }}
                >
                  <div>
                    <span className="badge badge-info" style={{ marginBottom: 6 }}>
                      Dự án: {ev.projectName}
                    </span>
                    <h3 style={{ margin: 0 }}>Đánh giá ngày {new Date(ev.createdAt).toLocaleDateString('vi-VN')}</h3>
                    <div style={{ fontSize: 13, color: 'var(--text-2)', marginTop: 4, display: 'flex', gap: 12 }}>
                      <span><User size={13} inline /> Người đánh giá: {ev.evaluatorName}</span>
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 24, fontWeight: 800, color: 'var(--primary)' }}>
                      {totalScore.toFixed(1)} / {totalMaxScore.toFixed(1)}
                    </div>
                    <span className="badge badge-success">{percent}% Tổng điểm</span>
                  </div>
                </div>

                {ev.note && (
                  <div
                    style={{
                      background: 'var(--bg-2)',
                      padding: 12,
                      borderRadius: 8,
                      marginBottom: 16,
                      fontSize: 14,
                      fontStyle: 'italic',
                    }}
                  >
                    💬 "{ev.note}"
                  </div>
                )}

                <h4>Chi tiết điểm theo tiêu chí:</h4>
                <div style={{ display: 'grid', gap: 10, marginTop: 10 }}>
                  {ev.scores.map((s) => {
                    const scorePercent = s.maxScore > 0 ? Math.round((s.score / s.maxScore) * 100) : 0
                    return (
                      <div
                        key={s.criterionId}
                        style={{
                          background: 'var(--bg-2)',
                          padding: '10px 14px',
                          borderRadius: 8,
                        }}
                      >
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                          <strong>{s.criterionName}</strong>
                          <span style={{ fontWeight: 700, color: 'var(--primary)' }}>
                            {s.score} / {s.maxScore} điểm
                          </span>
                        </div>
                        <div style={{ height: 6, background: 'var(--border)', borderRadius: 3, overflow: 'hidden' }}>
                          <div
                            style={{
                              height: '100%',
                              width: `${scorePercent}%`,
                              background: 'var(--primary)',
                              borderRadius: 3,
                            }}
                          />
                        </div>
                        {s.note && <div style={{ fontSize: 12, color: 'var(--text-2)', marginTop: 4 }}>{s.note}</div>}
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
