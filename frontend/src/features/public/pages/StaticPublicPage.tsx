import { BadgeCheck, FlaskConical, GraduationCap, Search } from 'lucide-react'
import type { CSSProperties, ReactNode } from 'react'
import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listPosts } from '../../posts/api'
import type { PostFeedItem } from '../../posts/types'
import { getResearchFields } from '../../profile/api'
import { listPublicProjects } from '../../projects/api'
import type { PublicProjectSummary } from '../../projects/types'
import { aboutQuickFacts, coreValues, operatingSteps } from '../publicData'
import { PublicPostCard, PublicProjectCard } from '../components/PublicDataCards'
import { PublicPageHead } from '../components/PublicPageHead'
import { listPublicEvents } from '../../events/api'

type StaticPublicPageProps = {
  title: string
  description: string
  kind: 'about' | 'contact' | 'search'
}

export function StaticPublicPage({ title, description, kind }: StaticPublicPageProps) {
  return (
    <>
      <PublicPageHead title={title} description={description} />
      {kind === 'about' ? <AboutContent /> : null}
      {kind === 'contact' ? <ContactContent /> : null}
      {kind === 'search' ? <SearchContent /> : null}
    </>
  )
}



function AboutContent() {
  const valueIcons = [FlaskConical, GraduationCap, BadgeCheck]
  const [counts, setCounts] = useState({ projects: null as number | null, fields: null as number | null, events: null as number | null })

  useEffect(() => {
    let active = true
    void Promise.all([listPublicProjects(0, 1), getResearchFields(), listPublicEvents({ upcoming: true })])
      .then(([projects, fields, events]) => {
        if (active) setCounts({ projects: projects.totalElements, fields: fields.length, events: events.length })
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [])

  const liveStats = [
    { value: countLabel(counts.projects), label: 'dự án có thể xem' },
    { value: countLabel(counts.events), label: 'sự kiện công khai sắp tới' },
    { value: countLabel(counts.fields), label: 'lĩnh vực nghiên cứu' },
  ]

  return (
    <>
      <section className="section">
        <div className="wrap">
          <div className="layout-side">
            <div className="prose">
              <div className="kicker">Tổng quan</div>
              <h2 style={{ marginTop: 0 }}>Một phòng Lab của sinh viên, vận hành như một nhóm nghiên cứu thật</h2>
              <p>
                Smart Lab được thành lập với mục tiêu tạo môi trường để sinh viên làm nghiên cứu và phát triển sản phẩm một
                cách bài bản. Thành viên được đưa vào các nhóm dự án có mục tiêu rõ ràng, có leader dẫn dắt và có lịch đánh
                giá tiến độ định kỳ.
              </p>
              <h3>Mục tiêu</h3>
              <p>
                Mỗi thành viên hướng tới ít nhất một kết quả công khai được: một bài viết, một sản phẩm chạy được, hoặc một
                bộ dữ liệu mở trong thời gian tham gia Lab.
              </p>
              <h3>Định hướng</h3>
              <p>
                Ba lĩnh vực trọng tâm là Trí tuệ nhân tạo, Robotics và Kỹ thuật phần mềm. Lab ưu tiên các bài toán thực tế
                trong nước và các sản phẩm có thể sử dụng thật.
              </p>
              <p>
                Lab đề cao ba nguyên tắc: nghiên cứu phải thật, người hướng dẫn phải sát, và việc đánh giá phải minh bạch. Đó là những giá trị
                định hình cách chúng tôi phân công, theo dõi và ghi nhận đóng góp của từng thành viên.
              </p>
            </div>
            <aside>
              <div className="side-box sticky">
                <h4>Thông tin nhanh</h4>
                <dl className="deflist">
                  {aboutQuickFacts.map((fact) => (
                    <Fragment key={fact.label}>
                      <dt>{fact.label}</dt>
                      <dd>{fact.label === 'Lĩnh vực'
                        ? `${countLabel(counts.fields)} hướng nghiên cứu`
                        : fact.value}</dd>
                    </Fragment>
                  ))}
                </dl>
                <Link className="btn brand block mt-20" to="/du-an">
                  Xem dự án đang chạy
                </Link>
              </div>
            </aside>
          </div>
        </div>
      </section>

      <section className="section alt tight">
        <div className="wrap">
          <div className="grid c3">
            {liveStats.map((stat) => (
              <div className="card pad center stat-card" key={stat.label}>
                <b>{stat.value}</b>
                <span className="muted small">{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="wrap">
          <div className="sec-head">
            <div className="kicker">Giá trị cốt lõi</div>
            <h2>Ba điều Lab luôn giữ</h2>
            <p>Những nguyên tắc này quyết định cách Lab làm việc và cách một thành viên được đánh giá.</p>
          </div>
          <div className="grid c3">
            {coreValues.map((value, index) => {
              const Icon = valueIcons[index] ?? BadgeCheck

              return (
                <div className="fieldcard" style={{ '--c': value.color } as CSSProperties} key={value.title}>
                  <span className="ico">
                    <Icon />
                  </span>
                  <h3>{value.title}</h3>
                  <p>{value.description}</p>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      <section className="section alt">
        <div className="wrap">
          <div className="layout-side left">
            <div className="sec-head" style={{ marginBottom: 0 }}>
              <div className="kicker">Quy trình</div>
              <h2>Cách Lab vận hành</h2>
              <p>Từ lúc được cấp tài khoản đến lúc có kết quả, mỗi thành viên đi qua bốn bước sau.</p>
            </div>
            <div className="steps">
              {operatingSteps.map((step, index) => (
                <div className="step" key={step.title}>
                  <div className="rail">
                    <span className="n">{index + 1}</span>
                    {index < operatingSteps.length - 1 ? <span className="line" /> : null}
                  </div>
                  <div className="txt">
                    <b>{step.title}</b>
                    <p>{step.description}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="wrap">
          <div className="ctaband">
            <div>
              <h2>Sẵn sàng làm nghiên cứu thật?</h2>
              <p>Xem những dự án đang chạy tại Lab hoặc đọc các bài viết mới nhất để hiểu cách các nhóm đang làm việc.</p>
              <div className="hero-cta">
                <Link className="btn primary lg" to="/du-an">
                  Xem dự án đang chạy
                </Link>
                <Link className="btn outline-light lg" to="/bai-viet">
                  Xem bài viết
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  )
}

function ContactContent() {
  return (
    <section className="section">
      <div className="wrap">
        <div className="layout-side">
          <div className="card pad">
            <h2>Gửi lời nhắn cho Smart Lab</h2>
            <p className="muted">Form đang để mặc định theo mockup. Khi backend có API liên hệ, phần submit sẽ được nối vào.</p>
            <div className="form-stack mt-20">
              <label className="field">
                <span>Họ tên</span>
                <input className="input" placeholder="Nguyễn Văn A" />
              </label>
              <label className="field">
                <span>Email</span>
                <input className="input" placeholder="you@example.com" />
              </label>
              <label className="field">
                <span>Nội dung</span>
                <textarea className="textarea" placeholder="Bạn muốn trao đổi với Lab về..." />
              </label>
              <button className="btn primary" type="button">
                Gửi liên hệ
              </button>
            </div>
          </div>
          <aside>
            <div className="side-box">
              <h4>Thông tin liên hệ</h4>
              <div className="foot-contact">
                <div>Phòng A3-502, Hoà Lạc</div>
                <div>smartlab@example.edu.vn</div>
                <div>Thứ 2 - Thứ 6, 09:00 - 17:00</div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </section>
  )
}


function SearchContent() {
  const [query, setQuery] = useState('')
  const [projects, setProjects] = useState<PublicProjectSummary[]>([])
  const [posts, setPosts] = useState<PostFeedItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    void Promise.all([listPublicProjects(0, 48, { query: query.trim() }), listPosts(null, undefined, 30)])
      .then(([projectResult, postResult]) => {
        if (!active) return
        setProjects(projectResult.items)
        setPosts(postResult.items)
      })
      .catch((reason: unknown) => {
        if (active) setError(messageOf(reason, 'Không tải được dữ liệu tìm kiếm công khai.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [query, reloadKey])

  const normalized = query.trim().toLocaleLowerCase('vi')
  const matchingProjects = projects.filter((project) => includesQuery([
    project.code,
    project.name,
    project.description,
    ...project.leaders.map((leader) => leader.name),
  ], normalized))
  const matchingPosts = posts.filter((post) => includesQuery([
    post.title,
    post.excerpt,
    post.author?.name,
    post.category?.name,
  ], normalized))
  const total = matchingProjects.length + matchingPosts.length

  return (
    <section className="section">
      <div className="wrap">
        <div className="toolbar">
          <div className="searchbar" style={{ flex: 1, minWidth: 220 }}>
            <Search aria-hidden="true" />
            <input className="input" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm dự án, bài viết..." aria-label="Tìm nội dung công khai" autoFocus />
          </div>
        </div>
        {loading ? <div className="public-empty empty tight">Đang tải dữ liệu tìm kiếm...</div> : null}
        {error ? <LoadError message={error} onRetry={() => setReloadKey((value) => value + 1)} /> : null}
        {!loading && !error && total === 0 ? <div className="public-empty empty tight">Không tìm thấy nội dung phù hợp.</div> : null}
        {!loading && !error && matchingProjects.length > 0 ? <SearchGroup title={`Dự án (${matchingProjects.length})`}><div className="grid c3">{matchingProjects.map((project) => <PublicProjectCard key={project.id} project={project} />)}</div></SearchGroup> : null}
        {!loading && !error && matchingPosts.length > 0 ? <SearchGroup title={`Bài viết (${matchingPosts.length})`}><div className="grid c3">{matchingPosts.map((post) => <PublicPostCard key={post.id} post={post} />)}</div></SearchGroup> : null}
      </div>
    </section>
  )
}

function SearchGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="mt-20">
      <h2>{title}</h2>
      {children}
    </div>
  )
}

function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert">
      <div className="alert error">{message}</div>
      <button className="btn" type="button" onClick={onRetry}>Thử tải lại</button>
    </div>
  )
}

function includesQuery(values: Array<string | null | undefined>, normalizedQuery: string) {
  if (!normalizedQuery) return true
  return values.some((value) => value?.toLocaleLowerCase('vi').includes(normalizedQuery))
}

function messageOf(reason: unknown, fallback: string) {
  return reason instanceof Error ? reason.message : fallback
}

function countLabel(value: number | null) {
  return value === null ? '—' : String(value)
}
