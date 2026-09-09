import { CalendarDays, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
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

function formatDate(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' }).format(parsed)
}

function shorten(value: string, maximum: number) {
  return value.length > maximum ? `${value.slice(0, maximum).trimEnd()}…` : value
}
