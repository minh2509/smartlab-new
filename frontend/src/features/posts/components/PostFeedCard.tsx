import { useState } from 'react'
import { Link } from 'react-router-dom'
import { LogIn, MessageCircle } from 'lucide-react'
import { PostReactionPicker } from './PostReactionPicker'
import { reactionPresentation } from '../reactions'
import { exactDateTime, relativeTime } from '../relativeTime'
import type { PostFeedItem, ReactionState, ReactionType } from '../types'

type Props = {
  post: PostFeedItem
  reactionPending: boolean
  canInteract: boolean
  onReaction: (reaction: ReactionType) => Promise<ReactionState>
  onRemoveReaction: () => Promise<ReactionState>
  onComments: () => void
  onRequireLogin: () => void
}

const VISIBILITY = { PUBLIC: 'Công khai', LAB: 'Nội bộ Lab', PROJECT: 'Theo dự án' } as const

export function PostFeedCard({
  post,
  reactionPending,
  canInteract,
  onReaction,
  onRemoveReaction,
  onComments,
  onRequireLogin,
}: Props) {
  const body = typeof post.contentJson.body === 'string' ? post.contentJson.body : null
  const [expanded, setExpanded] = useState(false)
  const long = Boolean(body && (body.length > 520 || body.split('\n').length > 7))
  const reactionIcons = (Object.entries(post.reactionCounts) as Array<[ReactionType, number]>)
    .filter(([, count]) => count > 0)
    .slice(0, 3)

  return (
    <article className="social-post-card">
      <header className="social-post-author">
        <div className="social-avatar" aria-hidden="true">{initials(post.author?.name)}</div>
        <div>
          <Link to={`/posts/${encodeURIComponent(post.slug)}`}>{post.author?.name ?? 'Tác giả không còn khả dụng'}</Link>
          <div className="social-post-meta">
            <time dateTime={post.publishedAt ?? undefined} title={post.publishedAt ? exactDateTime(post.publishedAt) : undefined}>
              {post.publishedAt ? relativeTime(post.publishedAt) : ''}
            </time>
            <span aria-hidden="true">·</span>
            <span>{VISIBILITY[post.visibility]}{post.category ? `, ${post.category.name}` : ''}</span>
          </div>
        </div>
      </header>

      <div className="social-post-copy">
        {post.title ? <h2><Link to={`/posts/${encodeURIComponent(post.slug)}`}>{post.title}</Link></h2> : null}
        {body ? (
          <p className={!expanded && long ? 'is-collapsed' : undefined}>{body}</p>
        ) : post.excerpt ? <p>{post.excerpt}</p> : (
          <p className="social-content-fallback">Nội dung này hiện chưa hỗ trợ hiển thị đầy đủ.</p>
        )}
        {long ? (
          <button className="social-more" type="button" onClick={() => setExpanded((value) => !value)}>
            {expanded ? 'Thu gọn' : 'Xem thêm'}
          </button>
        ) : null}
      </div>

      <div className="future-media-slot" aria-hidden="true" />

      <div className="social-summary">
        <span aria-label={`${post.reactionCount} cảm xúc`}>
          {reactionIcons.map(([type]) => <span key={type} aria-hidden="true">{reactionPresentation(type).emoji}</span>)}
          {post.reactionCount > 0 ? <strong>{post.reactionCount}</strong> : null}
        </span>
        <button type="button" onClick={canInteract ? onComments : onRequireLogin}>{post.commentCount} bình luận</button>
      </div>

      <div className="social-actions">
        {canInteract ? <>
          <PostReactionPicker
            selected={post.viewerReaction}
            disabled={reactionPending}
            onSelect={(reaction) => void onReaction(reaction)}
            onRemove={() => void onRemoveReaction()}
          />
          <button className="social-action" type="button" onClick={onComments}>
            <MessageCircle aria-hidden="true" /> Bình luận
          </button>
        </> : (
          <button className="social-action social-login-action" type="button" onClick={onRequireLogin}>
            <LogIn aria-hidden="true" /> Đăng nhập để tương tác
          </button>
        )}
      </div>
    </article>
  )
}

function initials(name: string | undefined) {
  if (!name) return '?'
  return name.trim().split(/\s+/).slice(-2).map((part) => part[0]).join('').toUpperCase()
}
