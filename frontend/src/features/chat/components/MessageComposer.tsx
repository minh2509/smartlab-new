import { useCallback, useEffect, useRef, useState, type ClipboardEvent, type DragEvent, type KeyboardEvent } from 'react'
import { BarChart3, Image, Loader2, Mic, Paperclip, Reply, Send, Smile, Square, X } from 'lucide-react'
import { useAuth } from '../../auth/authContext'
import { uploadFile } from '../../files/api'
import { useChat } from '../chatContext'
import type { ChatMessageItem, ChatMessageType } from '../types'

type Props = { conversationId: string; placeholder?: string }
type Attachment = { fileId: number; name: string; mimeType: string }
const EMOJIS = ['😀', '😂', '😍', '🥳', '😎', '🤔', '😭', '🔥', '🎉', '❤️', '👍', '👏']
const STICKERS = ['👋', '🙌', '🎉', '💯', '🚀', '✨', '🧠', '🔬']

export function MessageComposer({ conversationId, placeholder = 'Nhập tin nhắn...' }: Props) {
  const { token } = useAuth()
  const chat = useChat()
  const draftKey = `smartlab-chat-draft:${conversationId}`
  const [text, setText] = useState(() => localStorage.getItem(draftKey) ?? '')
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [replyTo, setReplyTo] = useState<ChatMessageItem | null>(null)
  const [showEmoji, setShowEmoji] = useState(false)
  const [showExtras, setShowExtras] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [sending, setSending] = useState(false)
  const [recording, setRecording] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastTyping = useRef(0)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const mentionMatch = text.match(/(?:^|\s)@([^\s@]*)$/)
  const mentionCandidates = mentionMatch ? (chat.conversations.find((item) => item.conversationId === conversationId)?.members ?? [])
    .filter((member) => member.active && member.name.toLowerCase().includes(mentionMatch[1].toLowerCase()))
    .slice(0, 5) : []

  useEffect(() => { setText(localStorage.getItem(draftKey) ?? ''); setReplyTo(null); setAttachments([]) }, [draftKey])
  useEffect(() => { localStorage.setItem(draftKey, text) }, [draftKey, text])
  useEffect(() => {
    const syncDraft = (event: StorageEvent) => { if (event.key === draftKey) setText(event.newValue ?? '') }
    window.addEventListener('storage', syncDraft)
    return () => window.removeEventListener('storage', syncDraft)
  }, [draftKey])
  useEffect(() => {
    const listener = (event: Event) => { const detail = (event as CustomEvent<{ conversationId: string; message: ChatMessageItem }>).detail; if (detail.conversationId === conversationId) { setReplyTo(detail.message); setTimeout(() => document.querySelector<HTMLTextAreaElement>(`.chat-composer[data-conversation-id="${conversationId}"] textarea`)?.focus(), 0) } }
    window.addEventListener('chat:reply', listener)
    return () => window.removeEventListener('chat:reply', listener)
  }, [conversationId])

  const signalTyping = useCallback(() => {
    const now = Date.now()
    if (now - lastTyping.current > 3000) { lastTyping.current = now; chat.sendTypingSignal(conversationId, true) }
    if (typingTimer.current) clearTimeout(typingTimer.current)
    typingTimer.current = setTimeout(() => chat.sendTypingSignal(conversationId, false), 2500)
  }, [chat, conversationId])

  const uploadFiles = async (files: File[]) => {
    if (!token || !files.length) return
    setUploading(true)
    try {
      for (const file of files) {
        const uploaded = await uploadFile(token, file, 'PRIVATE', 'Chat attachment')
        setAttachments((items) => [...items, { fileId: uploaded.id, name: uploaded.originalName, mimeType: uploaded.mimeType }])
      }
    } catch (error) { console.error(error); alert('Không thể tải tệp lên.') }
    finally { setUploading(false); if (inputRef.current) inputRef.current.value = '' }
  }

  const send = async (override?: { messageType: ChatMessageType; content?: string; metadataJson?: string }) => {
    const content = override?.content ?? text.trim()
    const fileIds = attachments.map((item) => item.fileId)
    if (!override && !content && !fileIds.length || uploading || sending) return
    const allImages = attachments.length > 0 && attachments.every((item) => item.mimeType.startsWith('image/'))
    setSending(true)
    try {
      const pendingSend = chat.sendMessage(conversationId, {
        content: content || undefined, fileIds: fileIds.length ? fileIds : undefined,
        replyToMessageId: replyTo?.id, messageType: override?.messageType ?? (allImages ? 'IMAGE' : undefined),
        metadataJson: override?.metadataJson,
      })
      // Clear the composer as soon as the optimistic message is queued. Any
      // transport failure remains attached to that message with a retry action.
      setText(''); localStorage.removeItem(draftKey); setAttachments([]); setReplyTo(null); setShowExtras(false)
      await pendingSend
    } finally { setSending(false) }
  }

  const createPoll = () => {
    const question = prompt('Câu hỏi bình chọn?')?.trim()
    if (!question) return
    const raw = prompt('Các lựa chọn, ngăn cách bằng dấu phẩy')
    const values = raw?.split(',').map((value) => value.trim()).filter(Boolean) ?? []
    if (values.length < 2) return alert('Cần ít nhất 2 lựa chọn.')
    const metadataJson = JSON.stringify({ question, multiple: false, options: values.slice(0, 10).map((value, index) => ({ id: `${Date.now()}-${index}`, text: value, voterIds: [] })) })
    void send({ messageType: 'POLL', metadataJson })
  }

  const sendGif = () => {
    const url = prompt('Dán URL ảnh GIF (https://…)')?.trim()
    if (url && /^https:\/\//i.test(url)) void send({ messageType: 'GIF', content: url })
  }

  const startRecording = async () => {
    if (!token || !navigator.mediaDevices || !window.MediaRecorder) return alert('Trình duyệt không hỗ trợ ghi âm.')
    let stream: MediaStream | null = null
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const mediaRecorder = new MediaRecorder(stream)
      chunks.current = []
      mediaRecorder.ondataavailable = (event) => { if (event.data.size) chunks.current.push(event.data) }
      mediaRecorder.onstop = async () => {
        stream?.getTracks().forEach((track) => track.stop())
        const mimeType = mediaRecorder.mimeType.split(';')[0].toLowerCase()
        const extension = ({ 'audio/webm': 'webm', 'audio/ogg': 'ogg', 'audio/mp4': 'm4a' } as Record<string, string>)[mimeType]
        if (!extension || !chunks.current.length) {
          alert('Định dạng ghi âm của trình duyệt chưa được hỗ trợ.')
          return
        }
        const blob = new Blob(chunks.current, { type: mimeType })
        const file = new File([blob], `voice-${Date.now()}.${extension}`, { type: mimeType })
        setUploading(true)
        try {
          const uploaded = await uploadFile(token, file, 'PRIVATE', 'Voice message')
          await chat.sendMessage(conversationId, { fileIds: [uploaded.id], messageType: 'VOICE' })
        } catch (error) { console.error(error); alert('Không thể gửi tin nhắn thoại.') }
        finally { setUploading(false) }
      }
      recorder.current = mediaRecorder; mediaRecorder.start(); setRecording(true)
    } catch (error) {
      stream?.getTracks().forEach((track) => track.stop())
      console.error(error)
      alert('Không thể truy cập micro hoặc khởi tạo ghi âm.')
    }
  }

  const stopRecording = () => { recorder.current?.stop(); recorder.current = null; setRecording(false) }
  const handlePaste = (event: ClipboardEvent<HTMLTextAreaElement>) => { const files = [...event.clipboardData.files]; if (files.length) { event.preventDefault(); void uploadFiles(files) } }
  const handleDrop = (event: DragEvent<HTMLDivElement>) => { event.preventDefault(); void uploadFiles([...event.dataTransfer.files]) }

  return <div className="chat-composer" data-conversation-id={conversationId} onDragOver={(event) => event.preventDefault()} onDrop={handleDrop}>
    {replyTo && <div className="chat-composer-reply"><Reply size={15} /><span><strong>{replyTo.sender?.name}</strong>{replyTo.content || 'Tin nhắn đa phương tiện'}</span><button type="button" aria-label="Hủy trả lời" onClick={() => setReplyTo(null)}><X size={15} /></button></div>}
    {!!attachments.length && <div className="chat-composer-attachments-preview">{attachments.map((item) => <span key={item.fileId} className="chat-composer-chip">{item.mimeType.startsWith('image/') && <Image size={13} />} {item.name}<button type="button" aria-label={`Gỡ tệp ${item.name}`} onClick={() => setAttachments((all) => all.filter((value) => value.fileId !== item.fileId))}><X size={13} /></button></span>)}</div>}
    {showEmoji && <div className="chat-emoji-panel"><div>{EMOJIS.map((emoji) => <button key={emoji} type="button" onClick={() => { setText((value) => value + emoji); setShowEmoji(false) }}>{emoji}</button>)}</div><small>Sticker</small><div>{STICKERS.map((sticker) => <button className="is-sticker" key={sticker} type="button" onClick={() => { void send({ messageType: 'STICKER', content: sticker }); setShowEmoji(false) }}>{sticker}</button>)}</div></div>}
    {showExtras && <div className="chat-composer-extras"><button type="button" onClick={createPoll}><BarChart3 size={16} /> Bình chọn</button><button type="button" onClick={sendGif}><Image size={16} /> GIF từ URL</button></div>}
    {!!mentionCandidates.length && <div className="chat-mention-suggestions">{mentionCandidates.map((member) => <button type="button" key={member.userId} onClick={() => setText((value) => value.replace(/@[^\s@]*$/, `@${member.name.replace(/\s+/g, '_')} `))}>@{member.name}</button>)}</div>}
    <div className="chat-composer-row">
      <input ref={inputRef} type="file" multiple hidden onChange={(event) => void uploadFiles([...(event.target.files ?? [])])} />
      <button type="button" className="chat-composer-action-btn" title="Đính kèm" aria-label="Đính kèm tệp" disabled={uploading} onClick={() => inputRef.current?.click()}>{uploading ? <Loader2 size={16} className="animate-spin" /> : <Paperclip size={16} />}</button>
      <button type="button" className="chat-composer-action-btn" title="Emoji và sticker" aria-label="Chọn emoji hoặc sticker" onClick={() => setShowEmoji((value) => !value)}><Smile size={16} /></button>
      <button type="button" className="chat-composer-action-btn" title="Bình chọn và GIF" aria-label="Mở bình chọn hoặc GIF" onClick={() => setShowExtras((value) => !value)}><BarChart3 size={16} /></button>
      <textarea className="chat-composer-input" aria-label="Soạn tin nhắn" placeholder={placeholder} rows={1} value={text} onPaste={handlePaste}
        onChange={(event) => { setText(event.target.value); signalTyping() }}
        onKeyDown={(event: KeyboardEvent<HTMLTextAreaElement>) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); void send() } }} />
      <button type="button" className={`chat-composer-action-btn ${recording ? 'is-recording' : ''}`} title="Tin nhắn thoại" aria-label={recording ? 'Dừng và gửi tin nhắn thoại' : 'Ghi tin nhắn thoại'} onClick={() => recording ? stopRecording() : void startRecording()}>{recording ? <Square size={14} /> : <Mic size={16} />}</button>
      <button type="button" className="chat-composer-action-btn chat-composer-send-btn" aria-label="Gửi tin nhắn" disabled={(!text.trim() && !attachments.length) || uploading || sending} onClick={() => void send()}><Send size={15} /></button>
    </div>
    {recording && <div className="chat-recording-status"><span /> Đang ghi âm… nhấn nút vuông để gửi</div>}
  </div>
}
