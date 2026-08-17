import { ArrowRight, CheckCircle2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'
import { listPosts } from '../../posts/api'
import type { PostFeedItem } from '../../posts/types'
import { getMembers, getResearchFields } from '../../profile/api'
import { listProjects } from '../../projects/api'
import { PROJECT_STATUS_LABELS, type Project } from '../../projects/types'
import {
  PublicMemberCard,
  PublicPostCard,
  PublicProjectCard,
  ResearchFieldCard,
} from '../components/PublicDataCards'

type ResourceKey = 'fields' | 'projects' | 'members' | 'posts'
type ResourceErrors = Partial<Record<ResourceKey, string>>

export function HomePage() {
  const [fields, setFields] = useState<ResearchField[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [members, setMembers] = useState<MemberProfile[]>([])
  const [posts, setPosts] = useState<PostFeedItem[]>([])
  const [loading, setLoading] = useState(true)
  const [errors, setErrors] = useState<ResourceErrors>({})
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setErrors({})

    function reportError(key: ResourceKey, reason: unknown) {
      if (!active) return
      setErrors((current) => ({
        ...current,
        [key]: reason instanceof Error ? reason.message : 'Không thể tải dữ liệu.',
      }))
    }

    const requests = [
      getResearchFields()
        .then((result) => { if (active) setFields(result) })
        .catch((reason: unknown) => reportError('fields', reason)),
      listProjects(null)
        .then((result) => { if (active) setProjects(result) })
        .catch((reason: unknown) => reportError('projects', reason)),
      getMembers()
        .then((result) => { if (active) setMembers(result) })
        .catch((reason: unknown) => reportError('members', reason)),
      listPosts(null, undefined, 3)
        .then((result) => { if (active) setPosts(result.items) })
        .catch((reason: unknown) => reportError('posts', reason)),
    ]

    void Promise.allSettled(requests).then(() => {
      if (active) setLoading(false)
    })

    return () => { active = false }
  }, [reloadKey])

  const featuredProjects = useMemo(
    () => [...projects].sort((left, right) => Number(right.isFeatured) - Number(left.isFeatured)).slice(0, 3),
    [projects],
  )
  const featuredMembers = useMemo(() => {
    const sorted = [...members].sort((left, right) => {
      const byFeatured = Number(right.isFeatured) - Number(left.isFeatured)
      if (byFeatured !== 0) return byFeatured
      return (left.featuredOrder ?? Number.MAX_SAFE_INTEGER) - (right.featuredOrder ?? Number.MAX_SAFE_INTEGER)
    })
    return sorted.slice(0, 4)
  }, [members])
  const publicLeaderIds = useMemo(
    () => new Set(projects.flatMap((project) => project.leaders.map((leader) => leader.userId))),
    [projects],
  )
  const hasError = Object.keys(errors).length > 0

  return (
    <>
      <section className="hero">
        <div className="hero-in">
          <div>
            <h1>
              Nơi sinh viên làm <em>nghiên cứu thật</em>, trên dự án thật.
            </h1>
            <p className="lead">
              Smart Lab là phòng nghiên cứu về Trí tuệ nhân tạo, Robotics và Kỹ thuật phần mềm. Chúng tôi nhận sinh viên
              vào các nhóm dự án có người hướng dẫn, có nhiệm vụ rõ ràng và có đánh giá định kỳ.
            </p>
            <div className="hero-cta">
              <Link className="btn primary lg" to="/du-an"><ArrowRight />Xem dự án đang chạy</Link>
              <Link className="btn outline-light lg" to="/lien-he">Liên hệ Smart Lab</Link>
            </div>
            <div className="hero-note">
              <CheckCircle2 />
              {loading
                ? 'Đang cập nhật hoạt động của Lab...'
                : `${errors.members ? '—' : members.length} thành viên công khai · ${errors.projects ? '—' : projects.length} dự án có thể xem`}
            </div>
          </div>

          <div className="window" aria-label="Các dự án mới nhất">
            <div className="window-bar"><i /><i /><i /><span>smartlab.fpt.edu.vn / khong-gian-lam-viec</span></div>
            <div className="window-body">
              {loading && featuredProjects.length === 0 ? <div className="wrow"><span className="t">Đang tải dự án...</span></div> : null}
              {!loading && featuredProjects.length === 0 ? <div className="wrow"><span className="t">Chưa có dự án công khai.</span></div> : null}
              {featuredProjects.map((project) => (
                <div className="wrow" key={project.id}>
                  <span className="ava sm">{initialsOf(project.primaryLeader?.name ?? project.name)}</span>
                  <span className="t"><b>{project.name}</b><span>{PROJECT_STATUS_LABELS[project.status]} · {project.leaders.length} leader</span></span>
                  <span className="chip">{project.code}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {hasError ? (
        <section className="section tight">
          <div className="wrap">
            <div className="alert error" role="alert">Một phần dữ liệu công khai chưa tải được.</div>
            <button className="btn" type="button" onClick={() => setReloadKey((value) => value + 1)}>Thử tải lại</button>
          </div>
        </section>
      ) : null}

      <section className="section">
        <div className="wrap">
          <div className="sec-head">
            <div className="kicker">Định hướng nghiên cứu</div>
            <h2>Lĩnh vực nghiên cứu</h2>
            <p>Mỗi lĩnh vực có nhóm dự án riêng, người hướng dẫn riêng và lộ trình kỹ năng riêng cho thành viên mới.</p>
          </div>
          <CollectionState loading={loading} error={errors.fields} empty={fields.length === 0} emptyText="Chưa có lĩnh vực nghiên cứu công khai." />
          {fields.length > 0 ? <div className="grid c3">{fields.map((field) => <ResearchFieldCard key={field.id} field={field} />)}</div> : null}
        </div>
      </section>

      <section className="section alt">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Dự án nổi bật</div><h2>Đang thực hiện tại Lab</h2>
              <p>Chỉ hiển thị phần thông tin được công khai. Nội dung nội bộ cần đăng nhập và là thành viên dự án.</p>
            </div>
            <Link className="section-cta" to="/du-an"><span>Xem tất cả {errors.projects ? '—' : projects.length} dự án</span><ArrowRight /></Link>
          </div>
          <CollectionState loading={loading} error={errors.projects} empty={featuredProjects.length === 0} emptyText="Chưa có dự án công khai." />
          {featuredProjects.length > 0 ? <div className="grid c3">{featuredProjects.map((project) => <PublicProjectCard key={project.id} project={project} />)}</div> : null}
        </div>
      </section>

      <section className="section">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Con người</div><h2>Thành viên nổi bật</h2>
              <p>Thông tin chuyên môn do thành viên công khai trên hồ sơ Smart Lab.</p>
            </div>
            <Link className="section-cta" to="/thanh-vien"><span>Xem toàn bộ thành viên</span><ArrowRight /></Link>
          </div>
          <CollectionState loading={loading} error={errors.members} empty={featuredMembers.length === 0} emptyText="Chưa có hồ sơ thành viên công khai." />
          {featuredMembers.length > 0 ? <div className="grid c4">{featuredMembers.map((member) => <PublicMemberCard key={member.userId} member={member} isProjectLeader={publicLeaderIds.has(member.userId)} />)}</div> : null}
        </div>
      </section>

      <section className="section alt">
        <div className="wrap">
          <div className="sec-row">
            <div className="sec-head">
              <div className="kicker">Bài viết & tin tức</div><h2>Cập nhật mới nhất</h2>
              <p>Thông báo, kết quả nghiên cứu, hướng dẫn kỹ thuật và chia sẻ kinh nghiệm từ các nhóm dự án.</p>
            </div>
            <Link className="section-cta" to="/bai-viet"><span>Xem bài viết</span><ArrowRight /></Link>
          </div>
          <CollectionState loading={loading} error={errors.posts} empty={posts.length === 0} emptyText="Chưa có bài viết công khai." />
          {posts.length > 0 ? <div className="grid c3">{posts.map((post) => <PublicPostCard key={post.id} post={post} />)}</div> : null}
        </div>
      </section>
    </>
  )
}

function CollectionState({ loading, error, empty, emptyText }: {
  loading: boolean
  error?: string
  empty: boolean
  emptyText: string
}) {
  if (loading && empty) return <div className="public-empty empty tight">Đang tải dữ liệu...</div>
  if (error && empty) return <div className="public-empty empty tight" role="alert">{error}</div>
  if (!loading && empty) return <div className="public-empty empty tight">{emptyText}</div>
  return null
}

function initialsOf(name: string | null | undefined) {
  if (!name) return '?'
  return name.trim().split(/\s+/).filter(Boolean).slice(-2).map((part) => part.charAt(0)).join('').toLocaleUpperCase('vi')
}
