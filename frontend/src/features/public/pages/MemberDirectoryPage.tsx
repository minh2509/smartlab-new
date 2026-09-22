import { RotateCcw, Search, UsersRound } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { PublicPageHead } from '../components/PublicPageHead'
import { getResearchFields, listPublicMembers } from '../../profile/api'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'
import './member-directory.css'

type DirectoryStatus = 'ACTIVE' | 'ALUMNI'

export function MemberDirectoryPage() {
  const [members, setMembers] = useState<MemberProfile[]>([])
  const [fields, setFields] = useState<ResearchField[]>([])
  const [query, setQuery] = useState('')
  const [fieldCode, setFieldCode] = useState('')
  const [status, setStatus] = useState<DirectoryStatus>('ACTIVE')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    void Promise.all([
      listPublicMembers({ keyword: query.trim() || undefined, fieldCode: fieldCode || undefined, status }),
      getResearchFields(),
    ])
      .then(([memberData, fieldData]) => {
        if (controller.signal.aborted) return
        setMembers(memberData)
        setFields(fieldData.filter((field) => field.isActive))
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : 'Không thể tải danh sách thành viên.')
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [fieldCode, query, reloadKey, status])

  const selectedField = useMemo(() => fields.find((field) => field.code === fieldCode), [fieldCode, fields])

  return (
    <>
      <PublicPageHead title="Thành viên" description="Khám phá cộng đồng thành viên đang học tập, nghiên cứu và phát triển sản phẩm tại Smart Lab." />
      <section className="section member-directory-section">
        <div className="wrap">
          <div className="member-directory-toolbar" aria-label="Bộ lọc thành viên">
            <label className="member-directory-search"><Search size={17} aria-hidden="true" /><span className="sr-only">Tìm thành viên</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo tên hoặc giới thiệu..." /></label>
            <div className="member-directory-filters">
              <div className="member-directory-tabs" role="tablist" aria-label="Trạng thái thành viên">
                <button type="button" role="tab" aria-selected={status === 'ACTIVE'} className={status === 'ACTIVE' ? 'is-active' : ''} onClick={() => setStatus('ACTIVE')}>Đang hoạt động</button>
                <button type="button" role="tab" aria-selected={status === 'ALUMNI'} className={status === 'ALUMNI' ? 'is-active' : ''} onClick={() => setStatus('ALUMNI')}>Cựu thành viên</button>
              </div>
              <label className="member-directory-field-filter"><span className="sr-only">Lọc lĩnh vực nghiên cứu</span><select value={fieldCode} onChange={(event) => setFieldCode(event.target.value)}><option value="">Tất cả lĩnh vực</option>{fields.map((field) => <option value={field.code} key={field.id}>{field.name}</option>)}</select></label>
            </div>
          </div>
          {selectedField ? <p className="member-directory-filter-summary">Đang lọc theo <strong>{selectedField.name}</strong></p> : null}
          {error ? <div className="member-directory-feedback" role="alert"><div className="alert error">{error}</div><button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}><RotateCcw size={15} aria-hidden="true" /> Thử tải lại</button></div> : null}
          {loading ? <div className="public-empty empty tight" aria-busy="true">Đang tải danh sách thành viên...</div> : null}
          {!loading && !error && !members.length ? <div className="public-empty empty tight"><UsersRound size={24} aria-hidden="true" /><span>Chưa có thành viên phù hợp với bộ lọc hiện tại.</span></div> : null}
          {!loading && !error && members.length ? <div className="member-directory-grid">{members.map((member) => <MemberCard key={member.userId} member={member} />)}</div> : null}
        </div>
      </section>
    </>
  )
}

function MemberCard({ member }: { member: MemberProfile }) {
  const initials = member.name.split(' ').filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase() || '?'
  return <article className="member-directory-card"><div className="member-directory-avatar" aria-hidden="true">{initials}</div><div className="member-directory-card-body"><div className="member-directory-card-head"><h2>{member.name}</h2><span>{member.activeStatus === 'ALUMNI' ? 'Cựu thành viên' : 'Đang hoạt động'}</span></div>{member.bio ? <p>{member.bio}</p> : <p className="muted">Thành viên Smart Lab.</p>}{member.researchFields.length ? <ul className="member-directory-tags" aria-label="Lĩnh vực nghiên cứu">{member.researchFields.map((field) => <li key={field.id}>{field.name}</li>)}</ul> : null}{member.publicEmail ? <a className="member-directory-email" href={`mailto:${member.publicEmail}`}>{member.publicEmail}</a> : null}</div></article>
}
