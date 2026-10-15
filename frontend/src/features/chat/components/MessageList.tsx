import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { AlertCircle, ArrowDown, CheckCheck, Download, FileText, Forward, MoreHorizontal, Pencil, Reply, SmilePlus, Trash2, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { downloadFile } from '../../files/api'
import { useChat } from '../chatContext'
import type { ChatMessageFileResponse, ChatMessageItem } from '../types'

type MessageListProps = { conversationId: string }
const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '😡']

export function MessageList({ conversationId }: MessageListProps) {
  const { profile, token } = useAuth()
  const chat = useChat()
  const containerRef = useRef<HTMLDivElement>(null)
  const previousHeight = useRef(0)
  const previousTop = useRef(0)
  const nearBottom = useRef(true)
  const previousCount = useRef(0)
  const [unseen, setUnseen] = useState(0)
  const [menuId, setMenuId] = useState<number | null>(null)
  const [reactionId, setReactionId] = useState<number | null>(null)
  const [forwarding, setForwarding] = useState<ChatMessageItem | null>(null)
  const [lightbox, setLightbox] = useState<ChatMessageFileResponse | null>(null)
  const messages = chat.messagesByConversation[conversationId] ?? []
  const conversation = chat.conversations.find((item) => item.conversationId === conversationId)

  useLayoutEffect(() => {
    const element = containerRef.current
    if (!element || !previousHeight.current) return
    element.scrollTop = previousTop.current + element.scrollHeight - previousHeight.current
    previousHeight.current = 0
  }, [messages.length])

  useEffect(() => {
    const element = containerRef.current
    if (!element) return
    const diff = messages.length - previousCount.current
    previousCount.current = messages.length
    if (diff > 0 && !nearBottom.current) setUnseen((value) => value + diff)
    else if (diff >= 0) element.scrollTop = element.scrollHeight
  }, [messages.length])

  const loadOlder = () => {
    const element = containerRef.current
    if (!element) return
    previousHeight.current = element.scrollHeight
    previousTop.current = element.scrollTop
    void chat.loadOlderMessages(conversationId)
  }

  const scrollToMessage = (id: number) => {
    const target = containerRef.current?.querySelector<HTMLElement>(`[data-message-id="${id}"]`)
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    target?.classList.add('is-highlighted')
    setTimeout(() => target?.classList.remove('is-highlighted'), 1400)
  }

  const newestOwn = [...messages].reverse().find((item) => item.sender?.userId === profile?.userId)
  const currentMembership = conversation?.members.find((member) => member.userId === profile?.userId)
  const canModerate = conversation?.type === 'GROUP'
    && (currentMembership?.role === 'OWNER' || currentMembership?.role === 'ADMIN')
  const readers = newestOwn && conversation ? conversation.members.filter((member) =>
    member.userId !== profile?.userId && member.lastReadSeq >= newestOwn.messageSeq) : []

  return <div ref={containerRef} className="chat-message-list" role="log" aria-label="Danh sách tin nhắn"
    onScroll={() => { const el = containerRef.current; if (!el) return; nearBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 70; if (nearBottom.current) setUnseen(0) }}>
    {chat.hasMoreOlderMessages(conversationId) && <button className="chat-load-older-btn" type="button" disabled={chat.isLoadingOlder(conversationId)} onClick={loadOlder}>{chat.isLoadingOlder(conversationId) ? 'Đang tải…' : 'Tải thêm tin nhắn cũ'}</button>}
    {!messages.length && <div className="chat-empty-thread"><p>Chưa có tin nhắn. Hãy bắt đầu trò chuyện!</p></div>}
    {messages.map((message, index) => {
      const own = message.sender?.userId === profile?.userId
      const canDelete = own || canModerate
      const previous = messages[index - 1]
      const next = messages[index + 1]
      const groupedBefore = previous?.sender?.userId === message.sender?.userId && closeInTime(previous.createdAt, message.createdAt)
      const groupedAfter = next?.sender?.userId === message.sender?.userId && closeInTime(message.createdAt, next.createdAt)
      return <div key={message.id || message.clientMessageId} data-message-id={message.id}
        className={`chat-msg-row ${own ? 'is-own' : 'is-other'} ${groupedBefore ? 'is-grouped-before' : ''} ${groupedAfter ? 'is-grouped-after' : ''}`}>
        {!own && !groupedBefore && <span className="chat-msg-sender-name">{message.sender?.name}</span>}
        <div className="chat-message-line">
          <div className="chat-bubble">{message.deletedAt ? <em className="chat-deleted-message">Tin nhắn đã được thu hồi</em> : <>
            {message.forwardedFromMessageId && <span className="chat-forwarded-label"><Forward size={11} /> Đã chuyển tiếp</span>}
            {message.replyTo && <button type="button" className="chat-reply-quote" onClick={() => scrollToMessage(message.replyTo!.id)}><strong>{message.replyTo.sender?.name ?? 'Tin nhắn'}</strong><span>{message.replyTo.deleted ? 'Tin nhắn đã bị thu hồi' : previewText(message.replyTo.content, message.replyTo.messageType)}</span></button>}
            <MessageBody message={message} token={token} currentUserId={profile?.userId} onOpenImage={setLightbox} onVote={conversation?.closedAt ? undefined : (optionId) => void chat.votePoll(conversationId, message.id, optionId)} />
            {!!message.reactions?.length && <div className="chat-reaction-summary">{message.reactions.map((reaction) => <button key={reaction.emoji} type="button" disabled={Boolean(conversation?.closedAt)} className={reaction.userIds.includes(profile?.userId ?? '') ? 'is-mine' : ''} title={reaction.userIds.join(', ')} onClick={() => void chat.toggleReaction(conversationId, message.id, reaction.emoji)}>{reaction.emoji} {reaction.count}</button>)}</div>}
          </>}</div>
          {!conversation?.closedAt && !message.deletedAt && message.status === 'SENT' && <div className="chat-message-actions">
            <button type="button" title="Cảm xúc" onClick={() => setReactionId(reactionId === message.id ? null : message.id)}><SmilePlus size={15} /></button>
            <button type="button" title="Trả lời" onClick={() => window.dispatchEvent(new CustomEvent('chat:reply', { detail: { conversationId, message } }))}><Reply size={15} /></button>
            <button type="button" title="Tùy chọn" onClick={() => setMenuId(menuId === message.id ? null : message.id)}><MoreHorizontal size={16} /></button>
            {reactionId === message.id && <div className="chat-reaction-picker">{QUICK_REACTIONS.map((emoji) => <button key={emoji} type="button" onClick={() => { void chat.toggleReaction(conversationId, message.id, emoji); setReactionId(null) }}>{emoji}</button>)}</div>}
            {menuId === message.id && <div className="chat-message-menu">
              {own && ['TEXT', 'MIXED'].includes(message.messageType) && <button type="button" onClick={() => { const value = prompt('Sửa tin nhắn', message.content ?? ''); if (value?.trim()) void chat.editMessage(conversationId, message.id, value); setMenuId(null) }}><Pencil size={14} /> Sửa</button>}
              <button type="button" onClick={() => { setForwarding(message); setMenuId(null) }}><Forward size={14} /> Chuyển tiếp</button>
              {canDelete && <button type="button" className="is-danger" onClick={() => { if (confirm(own ? 'Thu hồi tin nhắn này?' : 'Gỡ tin nhắn này khỏi nhóm?')) void chat.deleteMessage(conversationId, message.id); setMenuId(null) }}><Trash2 size={14} /> {own ? 'Thu hồi' : 'Gỡ khỏi nhóm'}</button>}
            </div>}
          </div>}
        </div>
        {!groupedAfter && <div className="chat-msg-meta"><time>{formatTime(message.createdAt)}</time>{message.editedAt && <span>đã sửa</span>}{own && message.status === 'SENDING' && <span>Đang gửi…</span>}{own && !conversation?.closedAt && message.status === 'FAILED' && <span className="chat-msg-failed"><AlertCircle size={12} /> Gửi thất bại <button type="button" onClick={() => void chat.retrySendMessage(conversationId, message.clientMessageId)}>Thử lại</button></span>}{own && message.status === 'SENT' && <CheckCheck size={13} />}</div>}
        {message.id === newestOwn?.id && readers.length > 0 && <div className="chat-seen-row">Đã xem bởi {readers.map((reader) => reader.name).join(', ')}</div>}
      </div>
    })}
    {!!chat.typingUsers[conversationId]?.length && <div className="chat-typing-indicator"><span>{chat.typingUsers[conversationId].map((user) => user.name).join(', ')} đang nhập</span><span className="chat-typing-dots"><span /><span /><span /></span></div>}
    {unseen > 0 && <button className="chat-new-messages-pill" type="button" onClick={() => { const el = containerRef.current; if (el) el.scrollTop = el.scrollHeight; setUnseen(0) }}><ArrowDown size={14} /> {unseen} tin nhắn mới</button>}
    {forwarding && !conversation?.closedAt && <div className="chat-modal-backdrop"><div className="chat-forward-dialog"><div className="chat-dialog-head"><strong>Chuyển tiếp tin nhắn</strong><button type="button" onClick={() => setForwarding(null)}><X size={18} /></button></div>{chat.conversations.filter((item) => !item.archived).map((item) => <button type="button" className="chat-forward-target" key={item.conversationId} onClick={() => { void chat.forwardMessage(conversationId, forwarding.id, item.conversationId); setForwarding(null) }}>{item.displayName}</button>)}</div></div>}
    {lightbox && token && <div className="chat-lightbox" role="dialog" onClick={() => setLightbox(null)}><button type="button"><X /></button><AuthenticatedAsset token={token} file={lightbox} kind="image" /></div>}
  </div>
}

function MessageBody({ message, token, onOpenImage, onVote, currentUserId }: { message: ChatMessageItem; token: string | null; onOpenImage: (file: ChatMessageFileResponse) => void; onVote?: (id: string) => void; currentUserId?: string }) {
  const poll = useMemo(() => parsePoll(message.metadataJson), [message.metadataJson])
  const urls = extractUrls(message.content)
  if (message.messageType === 'POLL' && poll) {
    const total = poll.options.reduce((sum, option) => sum + option.voterIds.length, 0)
    return <div className="chat-poll"><strong>{poll.question}</strong>{poll.options.map((option) => { const selected = option.voterIds.includes(currentUserId ?? ''); const percent = total ? Math.round(option.voterIds.length * 100 / total) : 0; return <button key={option.id} type="button" className={selected ? 'is-selected' : ''} disabled={!onVote} onClick={() => onVote?.(option.id)}><span>{option.text}</span><small>{option.voterIds.length} phiếu · {percent}%</small><i style={{ width: `${percent}%` }} /></button> })}<small>{total} lượt chọn</small></div>
  }
  return <>{message.messageType === 'STICKER' ? <div className="chat-sticker">{message.content}</div> : message.messageType === 'GIF' && message.content ? <img className="chat-inline-image" src={message.content} alt="GIF" loading="lazy" /> : message.content && <p className="chat-message-text">{renderMentions(message.content)}</p>}
    {!!message.files?.length && <div className="chat-bubble-files">{message.files.map((file) => token && (file.mimeType.startsWith('image/') ? <button className="chat-image-button" type="button" key={file.fileId} onClick={() => onOpenImage(file)}><AuthenticatedAsset token={token} file={file} kind="image" /></button> : file.mimeType.startsWith('audio/') ? <AuthenticatedAsset key={file.fileId} token={token} file={file} kind="audio" /> : <DownloadChip key={file.fileId} token={token} file={file} />))}</div>}
    {urls.map((url) => <a className="chat-link-preview" href={url} target="_blank" rel="noreferrer" key={url}><strong>{safeHost(url)}</strong><span>{url}</span></a>)}</>
}

function AuthenticatedAsset({ token, file, kind }: { token: string; file: ChatMessageFileResponse; kind: 'image' | 'audio' }) {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => { let active = true; let objectUrl = ''; void downloadFile(token, file.fileId).then((blob) => { if (active) { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl) } }); return () => { active = false; if (objectUrl) URL.revokeObjectURL(objectUrl) } }, [file.fileId, token])
  if (!url) return <span className="chat-media-loading">Đang tải…</span>
  return kind === 'image' ? <img src={url} alt={file.originalName} loading="lazy" /> : <audio src={url} controls preload="metadata" />
}

function DownloadChip({ token, file }: { token: string; file: ChatMessageFileResponse }) { const open = async () => { const blob = await downloadFile(token, file.fileId); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = file.originalName; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }; return <button type="button" className="chat-file-chip" onClick={() => void open()}><FileText size={16} /><span>{file.originalName}</span><Download size={14} /></button> }
function parsePoll(value: string | null) { try { return value ? JSON.parse(value) as { question: string; multiple: boolean; options: { id: string; text: string; voterIds: string[] }[] } : null } catch { return null } }
function extractUrls(value: string | null) { return value?.match(/https?:\/\/[^\s<]+/g)?.slice(0, 3) ?? [] }
function safeHost(value: string) { try { return new URL(value).hostname } catch { return value } }
function renderMentions(value: string) { return value.split(/(@[\p{L}\p{N}._-]+)/gu).map((part, index) => part.startsWith('@') ? <mark key={`${part}-${index}`} className="chat-mention">{part}</mark> : part) }
function previewText(content: string | null, type: string) { return content?.slice(0, 90) || ({ IMAGE: 'Ảnh', VOICE: 'Tin nhắn thoại', FILE: 'Tệp', POLL: 'Cuộc thăm dò' }[type] ?? 'Tin nhắn') }
function closeInTime(a: string, b: string) { return Math.abs(new Date(b).getTime() - new Date(a).getTime()) < 300_000 }
function formatTime(value: string) { try { return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value)) } catch { return '' } }
