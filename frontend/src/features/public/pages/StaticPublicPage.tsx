import { BadgeCheck, CalendarDays, Clock3, FileText, FlaskConical, GraduationCap, MapPin, Search, Video } from 'lucide-react'
import type { CSSProperties } from 'react'
import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getMembers } from '../../profile/api'
import type { MemberProfile } from '../../../shared/types/api'
import { aboutQuickFacts, coreValues, documents, events, fields, gallery, members, operatingSteps, posts, projects, stats } from '../publicData'
import { PublicPageHead } from '../components/PublicPageHead'
import { listPublicEvents, listEvents } from '../../events/api'
import type { LabEvent } from '../../events/types'
import { EVENT_MODE_LABELS, EVENT_STATUS_LABELS, EVENT_STATUS_BADGES } from '../../events/types'
import { useAuth } from '../../auth/authContext'

type StaticPublicPageProps = {
  title: string
  description: string
  kind: 'about' | 'fields' | 'projects' | 'members' | 'blog' | 'documents' | 'events' | 'gallery' | 'contact' | 'search'
}

type Field = (typeof fields)[number]
type Project = (typeof projects)[number]
type Member = (typeof members)[number]
type Post = (typeof posts)[number]

export function StaticPublicPage({ title, description, kind }: StaticPublicPageProps) {
  return (
    <>
      <PublicPageHead title={title} description={description} />
      {kind === 'about' ? <AboutContent /> : null}
      {kind === 'fields' ? <FieldsContent /> : null}
      {kind === 'projects' ? <ProjectsContent /> : null}
      {kind === 'members' ? <ApiMembersContent /> : null}
      {kind === 'blog' ? <BlogContent /> : null}
      {kind === 'documents' ? <DocumentsContent /> : null}
      {kind === 'events' ? <EventsContent /> : null}
      {kind === 'gallery' ? <GalleryContent /> : null}
      {kind === 'contact' ? <ContactContent /> : null}
      {kind === 'search' ? <SearchContent /> : null}
    </>
  )
}

export function FieldCard({ field }: { field: Field }) {
  return (
    <Link className="fieldcard" to="/linh-vuc" style={{ '--c': field.color } as CSSProperties}>
      <span className="fieldcard-media">
        <img src={field.image} alt={field.imageAlt} loading="lazy" />
      </span>
      <h3>{field.name}</h3>
      <p>{field.description}</p>
      <div className="tags">
        {field.tags.map((tag) => (
          <span className="chip" key={tag}>
            {tag}
          </span>
        ))}
      </div>
      <span className="more">Xem lĩnh vực</span>
    </Link>
  )
}

export function ProjectCard({ project }: { project: Project }) {
  return (
    <Link className="card hover projcard" to="/du-an">
      <div className={`cover ph ${project.cover}`}>
        <span className={`lbl badge ${project.badge}`}>
          <span className="dot" />
          {project.status}
        </span>
      </div>
      <div className="body">
        <div className="row gap-6 wrapf">
          <span className="chip accent">{project.field}</span>
          <span className="chip">{project.type}</span>
        </div>
        <h3>{project.title}</h3>
        <p>{project.description}</p>
        <div className="foot">
          <span className="ava-stack">
            {project.members.map((member) => (
              <span className="ava xs" style={{ background: member.startsWith('+') ? 'var(--text-3)' : 'var(--s1)' }} key={member}>
                {member}
              </span>
            ))}
          </span>
          <span className="pct">{project.progress}</span>
        </div>
      </div>
    </Link>
  )
}

export function MemberCard({ member }: { member: Member }) {
  return (
    <Link className="card hover person" to="/thanh-vien">
      <span className="ava lg" style={{ background: member.color }}>
        {member.initials}
      </span>
      <h3>{member.name}</h3>
      <div className="role">{member.role}</div>
      <div className="exp">{member.exp}</div>
      <div className="tags">
        <span className="chip accent">{member.field}</span>
      </div>
    </Link>
  )
}

export function PostCard({ post }: { post: Post }) {
  return (
    <Link className="card hover postcard" to="/bai-viet">
      <div className={`cover ph ${post.cover}`} />
      <div className="body">
        <div className="pmeta">
          <span className="chip accent">{post.category}</span>
          <span>{post.date}</span>
          <span>·</span>
          <span>{post.read}</span>
        </div>
        <h3>{post.title}</h3>
        <p>{post.description}</p>
        <div className="by">
          <span className="ava xs" style={{ background: 'var(--s1)' }}>
            {post.initials}
          </span>
          {post.author}
        </div>
      </div>
    </Link>
  )
}

function AboutContent() {
  const valueIcons = [FlaskConical, GraduationCap, BadgeCheck]

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
                      <dd>{fact.value}</dd>
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
          <div className="grid c4">
            {stats.map((stat) => (
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

function FieldsContent() {
  return (
    <section className="section">
      <div className="wrap">
        <div className="grid c3">
          {fields.map((field) => (
            <FieldCard key={field.id} field={field} />
          ))}
        </div>
      </div>
    </section>
  )
}

function ProjectsContent() {
  return (
    <section className="section">
      <div className="wrap">
        <Toolbar placeholder="Tìm dự án theo tên, lĩnh vực hoặc công nghệ..." />
        <div className="grid c3">
          {projects.map((project) => (
            <ProjectCard key={project.title} project={project} />
          ))}
        </div>
      </div>
    </section>
  )
}

export function MembersContent() {
  return (
    <section className="section">
      <div className="wrap">
        <Toolbar placeholder="Tìm thành viên theo tên, role hoặc lĩnh vực..." />
        <div className="grid c4">
          {members.map((member) => (
            <MemberCard key={member.name} member={member} />
          ))}
        </div>
      </div>
    </section>
  )
}

function ApiMembersContent() {
  const [apiMembers, setApiMembers] = useState<MemberProfile[] | null>(null)
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getMembers()
      .then(setApiMembers)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Không tải được danh sách thành viên.'))
  }, [])

  const visibleMembers = apiMembers?.filter((member) => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return true
    return member.name.toLowerCase().includes(normalized)
      || (member.bio ?? '').toLowerCase().includes(normalized)
      || member.researchFields.some((field) => field.name.toLowerCase().includes(normalized))
  })

  if (apiMembers === null && !error) {
    return <section className="section"><div className="wrap"><div className="public-empty empty tight">Đang tải danh sách thành viên...</div></div></section>
  }

  if (apiMembers && apiMembers.length > 0) {
    return (
      <section className="section">
        <div className="wrap">
          <div className="toolbar">
            <div className="searchbar" style={{ flex: 1, minWidth: 220 }}>
              <Search aria-hidden="true" />
              <input className="input" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm thành viên theo tên hoặc lĩnh vực..." />
            </div>
          </div>
          {visibleMembers?.length ? (
            <div className="grid c4">
              {visibleMembers.map((member) => <ApiMemberCard key={member.userId} member={member} />)}
            </div>
          ) : <div className="public-empty empty tight">Không tìm thấy thành viên phù hợp.</div>}
        </div>
      </section>
    )
  }

  return (
    <section className="section">
      <div className="wrap">
        {error && <div className="alert error">{error}</div>}
        {!error && <div className="public-empty empty tight">Chưa có thành viên công khai.</div>}
      </div>
    </section>
  )
}

function ApiMemberCard({ member }: { member: MemberProfile }) {
  const initials = member.name.split(' ').filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase()
  const avatarUrl = member.avatar ? `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'}/files/${member.avatar.id}` : null
  return (
    <article className="card hover person">
      {avatarUrl ? <img className="public-member-avatar" src={avatarUrl} alt={member.name} /> : <span className="ava lg" style={{ background: 'var(--s1)' }}>{initials}</span>}
      <h3>{member.name}</h3>
      <div className="role">{member.activeStatus}</div>
      <div className="exp">{member.bio || 'Thành viên Smart Lab'}</div>
      <div className="tags">
        {member.researchFields.map((field) => <span className="chip accent" key={field.id}>{field.name}</span>)}
      </div>
    </article>
  )
}

function BlogContent() {
  return (
    <>
      <section className="section">
        <div className="wrap">
          <div className="sec-head">
            <div className="kicker">Đáng chú ý</div>
            <h2>Bài viết nổi bật</h2>
            <p>Những nội dung được ban biên tập chọn lọc trong tháng này.</p>
          </div>
          <div className="grid c3">
            {posts.slice(0, 3).map((post) => (
              <PostCard key={post.title} post={post} />
            ))}
          </div>
        </div>
      </section>
      <section className="section alt">
        <div className="wrap">
          <div className="sec-head">
            <div className="kicker">Tất cả bài viết</div>
            <h2>Mới nhất</h2>
            <p>Lọc theo loại nội dung hoặc tìm kiếm nhanh theo từ khoá.</p>
          </div>
          <Toolbar placeholder="Tìm bài viết theo tiêu đề, tác giả..." />
          <div className="grid c3">
            {posts.map((post) => (
              <PostCard key={post.title} post={post} />
            ))}
          </div>
        </div>
      </section>
    </>
  )
}

function DocumentsContent() {
  return (
    <section className="section">
      <div className="wrap">
        <div className="grid c3">
          {documents.map((document) => (
            <article className="fieldcard" style={{ '--c': 'var(--s3)' } as CSSProperties} key={document.title}>
              <span className="ico">
                <FileText />
              </span>
              <div className="pmeta">
                <span className="chip accent">{document.category}</span>
                <span>Cập nhật {document.updatedAt}</span>
              </div>
              <h3>{document.title}</h3>
              <p>{document.description}</p>
              <span className="more">Xem tài liệu</span>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}

function EventsContent() {
  const { token, isAuthenticated } = useAuth()
  const [apiEvents, setApiEvents] = useState<LabEvent[] | null>(null)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    const fetch = isAuthenticated && token
      // Đã đăng nhập → backend tự lọc đúng theo quyền:
      //   PUBLIC → mọi người login thấy
      //   LAB    → mọi người login thấy
      //   PROJECT → chỉ active member của project đó thấy
      ? listEvents(token, {}, controller.signal).then((result) =>
          result.filter((e) => e.status !== 'CANCELLED')
        )
      // Guest → chỉ thấy PUBLIC (endpoint không cần token)
      : listPublicEvents(controller.signal)
    fetch
      .then((result) => { if (!controller.signal.aborted) setApiEvents(result) })
      .catch(() => { if (!controller.signal.aborted) setLoadError(true) })
    return () => controller.abort()
  }, [isAuthenticated, token])

  const isLoading = apiEvents === null && !loadError

  return (
    <section className="section">
      <div className="wrap">
        {isLoading ? (
          <div className="event-list">
            {[1, 2, 3].map((i) => (
              <div className="eventrow" key={i} style={{ opacity: 0.4, pointerEvents: 'none' }}>
                <div className="datebox"><b>--</b><span>---</span></div>
                <div className="info"><h3 style={{ background: 'var(--border)', borderRadius: 4, color: 'transparent' }}>Đang tải...</h3></div>
              </div>
            ))}
          </div>
        ) : loadError ? (
          <div className="public-empty empty tight">Không tải được danh sách sự kiện. Vui lòng thử lại sau.</div>
        ) : apiEvents && apiEvents.length > 0 ? (
          <div className="event-list">
            {apiEvents.map((event) => {
              const start = new Date(event.startAt)
              const day = Number.isNaN(start.getTime()) ? '--' : new Intl.DateTimeFormat('vi-VN', { day: '2-digit' }).format(start)
              const month = Number.isNaN(start.getTime()) ? '---' : new Intl.DateTimeFormat('vi-VN', { month: 'short' }).format(start)
              const timeStr = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(start)
              const location = event.mode === 'ONLINE'
                ? (event.meetingUrl || 'Link họp chưa cập nhật')
                : (event.location || 'Địa điểm chưa cập nhật')
              return (
                <div className="eventrow" key={event.id}>
                  <div className="datebox">
                    <b>{day}</b>
                    <span>{month}</span>
                  </div>
                  <div className="info">
                    <h3>{event.title}</h3>
                    <div className="emeta">
                      <span>
                        <CalendarDays />
                        {timeStr}
                      </span>
                      <span>
                        {event.mode === 'ONLINE' ? <Video /> : <MapPin />}
                        {EVENT_MODE_LABELS[event.mode]} · {location}
                      </span>
                      <span>
                        <Clock3 />
                        <span className={`badge ${EVENT_STATUS_BADGES[event.status]}`}>
                          {EVENT_STATUS_LABELS[event.status]}
                        </span>
                      </span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="public-empty empty tight">
            {isAuthenticated
              ? 'Hiện chưa có sự kiện nào để hiển thị.'
              : 'Hiện chưa có sự kiện công khai nào. Đăng nhập để xem thêm.'}
          </div>
        )}
      </div>
    </section>
  )
}


function GalleryContent() {
  return (
    <section className="section">
      <div className="wrap">
        <div className="gallery">
          {gallery.map((item) => (
            <Link className={`gitem ph ${item.cls}`} to="/thu-vien-anh" key={item.title}>
              <span>{item.title}</span>
            </Link>
          ))}
        </div>
      </div>
    </section>
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
  return (
    <section className="section">
      <div className="wrap">
        <Toolbar placeholder="Tìm dự án, bài viết, thành viên..." />
        <div className="grid c3">
          {projects.slice(0, 2).map((project) => (
            <ProjectCard key={project.title} project={project} />
          ))}
          {posts.slice(0, 2).map((post) => (
            <PostCard key={post.title} post={post} />
          ))}
          {members.slice(0, 2).map((member) => (
            <MemberCard key={member.name} member={member} />
          ))}
        </div>
      </div>
    </section>
  )
}

function Toolbar({ placeholder }: { placeholder: string }) {
  return (
    <div className="toolbar">
      <div className="searchbar" style={{ flex: 1, minWidth: 220 }}>
        <Search aria-hidden="true" />
        <input className="input" type="search" placeholder={placeholder} />
      </div>
      <div className="pills sp">
        <button className="pill" type="button" aria-pressed="true">
          Tất cả
        </button>
        <button className="pill" type="button" aria-pressed="false">
          AI
        </button>
        <button className="pill" type="button" aria-pressed="false">
          Robotics
        </button>
        <button className="pill" type="button" aria-pressed="false">
          SE
        </button>
      </div>
    </div>
  )
}
