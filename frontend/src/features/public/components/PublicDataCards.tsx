import { CalendarDays, UsersRound } from 'lucide-react'
import type { CSSProperties } from 'react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import aiResearchImage from '../../../assets/fields/ai-research.webp'
import roboticsResearchImage from '../../../assets/fields/robotics-research.webp'
import softwareEngineeringImage from '../../../assets/fields/software-engineering.webp'
import type { MemberProfile, ResearchField } from '../../../shared/types/api'
import type { PostFeedItem } from '../../posts/types'
import {
  PUBLIC_PROJECT_STATUS_LABELS,
  PROJECT_TYPE_LABELS,
  type PublicProjectSummary,
} from '../../projects/types'

const PUBLIC_PROJECT_STATUS_BADGES = {
  RECRUITING: 'ok',
  UPCOMING: 'info',
  ACTIVE: 'ok',
  COMPLETED: 'mute',
} as const

const FIELD_VISUALS = {
  ai: {
    color: 'var(--s1)',
    image: aiResearchImage,
    imageAlt: 'Minh họa nghiên cứu trí tuệ nhân tạo trong phòng lab',
    tags: ['Computer Vision', 'NLP', 'Deep Learning'],
  },
  robotics: {
    color: 'var(--s2)',
    image: roboticsResearchImage,
    imageAlt: 'Minh họa cánh tay robot và cảm biến trong phòng lab',
    tags: ['Embedded', 'Control', 'ROS'],
  },
  software: {
    color: 'var(--s3)',
    image: softwareEngineeringImage,
    imageAlt: 'Minh họa kiến trúc phần mềm và quy trình kiểm thử',
    tags: ['Kiến trúc', 'DevOps', 'Kiểm thử'],
  },
} as const

export function ResearchFieldCard({ field }: { field: ResearchField }) {
  const visual = fieldVisual(field)
  const [coverFailed, setCoverFailed] = useState(false)
  const image = field.coverFileId && !coverFailed ? publicFileUrl(field.coverFileId) : visual.image

  return (
    <article className="fieldcard" style={{ '--c': visual.color } as CSSProperties}>
      <span className="fieldcard-media">
        <img
          src={image}
          alt={visual.imageAlt}
          loading="lazy"
          onError={() => {
            if (field.coverFileId && !coverFailed) setCoverFailed(true)
          }}
        />
      </span>
      <h3>{field.name}</h3>
      <p>{field.description || 'Thông tin chi tiết về hướng nghiên cứu này đang được cập nhật.'}</p>
      <div className="tags">
        <span className="chip accent">{field.code}</span>
        {visual.tags.map((tag) => <span className="chip" key={tag}>{tag}</span>)}
      </div>
    </article>
  )
}

export function PublicProjectCard({ project }: { project: PublicProjectSummary }) {
  const shownLeaders = project.leaders.slice(0, 3)
  const remainingLeaderCount = project.leaders.length - shownLeaders.length

  return (
    <Link className="card hover projcard" to={`/du-an/${project.id}`}>
      <div className="body">
        <div className="row gap-6 wrapf">
          <span className={`lbl badge ${PUBLIC_PROJECT_STATUS_BADGES[project.publicStatus]}`}>
            <span className="dot" />
            {PUBLIC_PROJECT_STATUS_LABELS[project.publicStatus]}
          </span>
          <span className="chip accent">{PROJECT_TYPE_LABELS[project.projectType]}</span>
        </div>
        <div>
          <span className="muted small">{project.code}</span>
          <h3>{project.name}</h3>
        </div>
        <p>{project.description || 'Dự án chưa có mô tả công khai.'}</p>
        <div className="foot">
          {shownLeaders.length > 0 ? (
            <span className="ava-stack" aria-label={`${project.leaders.length} leader`}>
              {shownLeaders.map((leader, index) => (
                <span className="ava xs" title={leader.name} key={`${leader.name}-${index}`}>{initialsOf(leader.name)}</span>
              ))}
              {remainingLeaderCount > 0 ? <span className="ava xs">+{remainingLeaderCount}</span> : null}
            </span>
          ) : <span className="muted small"><UsersRound size={14} /> Chưa công bố leader</span>}
          <span className="muted-link">Xem chi tiết</span>
        </div>
      </div>
    </Link>
  )
}

export function PublicMemberCard({ member, isProjectLeader = false }: { member: MemberProfile; isProjectLeader?: boolean }) {
  const avatarUrl = member.avatar ? publicFileUrl(member.avatar.id) : null
  const roleLabel = isProjectLeader
    ? member.isFeatured ? 'Leader · Nổi bật' : 'Leader dự án'
    : member.isFeatured ? 'Thành viên nổi bật' : 'Thành viên Smart Lab'

  return (
    <article className="card hover person">
      {avatarUrl
        ? <img className="public-member-avatar" src={avatarUrl} alt={`Ảnh đại diện của ${member.name}`} loading="lazy" />
        : <span className="ava lg" style={{ background: 'var(--s1)' }}>{initialsOf(member.name)}</span>}
      <h3>{member.name}</h3>
      <div className="role">{roleLabel}</div>
      <div className="exp">{member.bio || 'Hồ sơ chuyên môn đang được cập nhật.'}</div>
      {member.researchFields.length > 0 ? (
        <div className="tags">
          {member.researchFields.map((field) => <span className="chip accent" key={field.id}>{field.name}</span>)}
        </div>
      ) : null}
    </article>
  )
}

export function PublicPostCard({ post }: { post: PostFeedItem }) {
  const body = typeof post.contentJson.body === 'string' ? post.contentJson.body : null
  const description = shorten(post.excerpt || body || 'Mở bài viết để xem nội dung đầy đủ.', 220)

  return (
    <Link className="card hover postcard" to={`/posts/${encodeURIComponent(post.slug)}`}>
      <div className="cover ph ph-news" />
      <div className="body">
        <div className="pmeta">
          {post.category ? <span className="chip accent">{post.category.name}</span> : null}
          {post.publishedAt ? <span><CalendarDays size={14} /> {formatDate(post.publishedAt)}</span> : null}
        </div>
        <h3>{post.title}</h3>
        <p>{description}</p>
        <div className="by">
          <span className="ava xs" style={{ background: 'var(--s1)' }}>{initialsOf(post.author?.name)}</span>
          {post.author?.name ?? 'Smart Lab'}
        </div>
      </div>
    </Link>
  )
}

export function PublicArticleCard({ article }: { article: import('../articleTypes').LabArticleSummary }) {
  const description = shorten(article.excerpt || 'Mở bài viết để xem nội dung đầy đủ.', 220)

  return (
    <Link className="card hover postcard landing-article-card" to={`/bai-viet/${encodeURIComponent(article.slug)}`}>
      <div className="cover ph ph-article" />
      <div className="body">
        <div className="pmeta">
          {article.publishedAt ? <span>{formatDate(article.publishedAt)}</span> : null}
        </div>
        <h3>{article.title}</h3>
        <p>{description}</p>
        <div className="foot">
          <span className="muted-link">Đọc bài viết →</span>
        </div>
      </div>
    </Link>
  )
}

function fieldVisual(field: ResearchField) {
  const key = `${field.code} ${field.name}`.toLocaleLowerCase('vi')
  if (key.includes('robot')) return FIELD_VISUALS.robotics
  if (key.includes('software') || key.includes('phần mềm') || /(^|\s)se(\s|$)/.test(key)) {
    return FIELD_VISUALS.software
  }
  return FIELD_VISUALS.ai
}

function initialsOf(name: string | null | undefined) {
  if (!name) return '?'
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0))
    .join('')
    .toLocaleUpperCase('vi')
}

function publicFileUrl(fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  return `${baseUrl}/files/${fileId}`
}

function formatDate(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' }).format(parsed)
}

function shorten(value: string, maximum: number) {
  return value.length > maximum ? `${value.slice(0, maximum).trimEnd()}…` : value
}
