import { useEffect, useRef, useState } from 'react'
import { mergeAttributes, Node } from '@tiptap/core'
import { EditorContent, NodeViewWrapper, ReactNodeViewRenderer, useEditor, type JSONContent, type NodeViewProps } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import {
  Bold,
  Braces,
  Eye,
  Heading1,
  Heading2,
  ImagePlus,
  Italic,
  Link2,
  List,
  ListOrdered,
  Quote,
  Redo2,
  RemoveFormatting,
  Smile,
  Strikethrough,
  Underline,
  Undo2,
  Unlink,
  X,
} from 'lucide-react'
import { downloadFile } from '../../files/api'
import type { PostContentDocument } from '../types'
import { createPostDocumentFromText } from '../postContent'

type Props = {
  token: string
  value: PostContentDocument
  disabled?: boolean
  onChange: (value: PostContentDocument) => void
  onImageUpload: (file: File) => Promise<{ fileId: number; alt?: string }>
  onPreviewRequest: () => void
}

const EMOJIS = ['😀', '😄', '😂', '🥰', '😍', '🤔', '👏', '🙌', '🎉', '🔥', '💡', '✅', '🚀', '🔬', '🧪', '📚', '❤️', '👍']

export function RichTextEditor({ token, value, disabled = false, onChange, onImageUpload, onPreviewRequest }: Props) {
  const [emojiOpen, setEmojiOpen] = useState(false)
  const [insertingImage, setInsertingImage] = useState(false)
  const imageInputRef = useRef<HTMLInputElement>(null)
  const [, setToolbarVersion] = useState(0)
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        link: {
          openOnClick: false,
          autolink: true,
          defaultProtocol: 'https',
          HTMLAttributes: { rel: 'nofollow noopener noreferrer', target: '_blank' },
        },
      }),
      Placeholder.configure({ placeholder: 'Viết nội dung bài viết…' }),
      InlineImage.configure({ token }),
    ],
    content: editorContent(value),
    editable: !disabled,
    immediatelyRender: false,
    editorProps: {
      attributes: {
        id: 'post-content',
        class: 'post-rich-editor-content',
        'aria-label': 'Nội dung bài viết',
      },
    },
    onUpdate: ({ editor: current }) => {
      const json = current.getJSON()
      onChange({ type: 'doc', content: (json.content ?? []) as PostContentDocument['content'] })
    },
    onSelectionUpdate: () => setToolbarVersion((version) => version + 1),
  })

  useEffect(() => {
    editor?.setEditable(!disabled)
  }, [disabled, editor])

  function editLink() {
    if (!editor) return
    const previous = editor.getAttributes('link').href as string | undefined
    const supplied = window.prompt('Địa chỉ liên kết', previous ?? 'https://')
    if (supplied === null) return
    const href = normalizeLink(supplied)
    if (!href) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run()
      return
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href }).run()
  }

  function insertEmoji(emoji: string) {
    editor?.chain().focus().insertContent(emoji).run()
    setEmojiOpen(false)
  }

  async function insertImage(file?: File) {
    if (!file || !editor || disabled || insertingImage) return
    setInsertingImage(true)
    try {
      const image = await onImageUpload(file)
      editor.chain().focus().insertContent({
        type: 'image',
        attrs: { fileId: image.fileId, alt: image.alt ?? file.name },
      }).run()
    } catch {
      // The parent surfaces upload errors next to the form.
    } finally {
      setInsertingImage(false)
      if (imageInputRef.current) imageInputRef.current.value = ''
    }
  }

  return (
    <div className={`post-rich-editor${disabled ? ' is-disabled' : ''}`}>
      <div className="post-rich-toolbar" role="toolbar" aria-label="Công cụ định dạng nội dung">
        <ToolbarButton label="In đậm" active={editor?.isActive('bold')} disabled={disabled} onClick={() => editor?.chain().focus().toggleBold().run()}><Bold /></ToolbarButton>
        <ToolbarButton label="In nghiêng" active={editor?.isActive('italic')} disabled={disabled} onClick={() => editor?.chain().focus().toggleItalic().run()}><Italic /></ToolbarButton>
        <ToolbarButton label="Gạch chân" active={editor?.isActive('underline')} disabled={disabled} onClick={() => editor?.chain().focus().toggleUnderline().run()}><Underline /></ToolbarButton>
        <ToolbarButton label="Gạch ngang" active={editor?.isActive('strike')} disabled={disabled} onClick={() => editor?.chain().focus().toggleStrike().run()}><Strikethrough /></ToolbarButton>
        <span className="post-rich-toolbar-divider" aria-hidden="true" />
        <ToolbarButton label="Tiêu đề lớn" active={editor?.isActive('heading', { level: 1 })} disabled={disabled} onClick={() => editor?.chain().focus().toggleHeading({ level: 1 }).run()}><Heading1 /></ToolbarButton>
        <ToolbarButton label="Tiêu đề nhỏ" active={editor?.isActive('heading', { level: 2 })} disabled={disabled} onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}><Heading2 /></ToolbarButton>
        <ToolbarButton label="Danh sách dấu chấm" active={editor?.isActive('bulletList')} disabled={disabled} onClick={() => editor?.chain().focus().toggleBulletList().run()}><List /></ToolbarButton>
        <ToolbarButton label="Danh sách đánh số" active={editor?.isActive('orderedList')} disabled={disabled} onClick={() => editor?.chain().focus().toggleOrderedList().run()}><ListOrdered /></ToolbarButton>
        <ToolbarButton label="Trích dẫn" active={editor?.isActive('blockquote')} disabled={disabled} onClick={() => editor?.chain().focus().toggleBlockquote().run()}><Quote /></ToolbarButton>
        <ToolbarButton label="Khối mã" active={editor?.isActive('codeBlock')} disabled={disabled} onClick={() => editor?.chain().focus().toggleCodeBlock().run()}><Braces /></ToolbarButton>
        <span className="post-rich-toolbar-divider" aria-hidden="true" />
        <ToolbarButton label="Thêm liên kết" active={editor?.isActive('link')} disabled={disabled} onClick={editLink}><Link2 /></ToolbarButton>
        <ToolbarButton label="Gỡ liên kết" disabled={disabled || !editor?.isActive('link')} onClick={() => editor?.chain().focus().unsetLink().run()}><Unlink /></ToolbarButton>
        <ToolbarButton label="Xóa định dạng" disabled={disabled} onClick={() => editor?.chain().focus().unsetAllMarks().clearNodes().run()}><RemoveFormatting /></ToolbarButton>
        <div className="post-rich-popover-wrap">
          <ToolbarButton label="Chèn emoji" active={emojiOpen} disabled={disabled} onClick={() => setEmojiOpen((open) => !open)}><Smile /></ToolbarButton>
          {emojiOpen ? <div className="post-rich-emoji-picker" role="dialog" aria-label="Chọn emoji">
            {EMOJIS.map((emoji) => <button type="button" key={emoji} onClick={() => insertEmoji(emoji)}>{emoji}</button>)}
          </div> : null}
        </div>
        <input
          ref={imageInputRef}
          className="post-editor-file-input"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          disabled={disabled || insertingImage}
          onChange={(event) => void insertImage(event.target.files?.[0])}
        />
        <ToolbarButton label={insertingImage ? 'Đang tải ảnh' : 'Chèn ảnh vào nội dung'} disabled={disabled || insertingImage} onClick={() => imageInputRef.current?.click()}><ImagePlus /></ToolbarButton>
        <span className="post-rich-toolbar-spacer" />
        <ToolbarButton label="Hoàn tác" disabled={disabled || !editor?.can().chain().focus().undo().run()} onClick={() => editor?.chain().focus().undo().run()}><Undo2 /></ToolbarButton>
        <ToolbarButton label="Làm lại" disabled={disabled || !editor?.can().chain().focus().redo().run()} onClick={() => editor?.chain().focus().redo().run()}><Redo2 /></ToolbarButton>
        <ToolbarButton label="Xem trước bài viết" disabled={disabled} onClick={onPreviewRequest}><Eye /></ToolbarButton>
      </div>
      <EditorContent editor={editor} />
      <div className="post-rich-editor-footer">
        <span>Hỗ trợ tiêu đề, danh sách, trích dẫn, liên kết, emoji và ảnh trong nội dung.</span>
        <span>Ctrl/Cmd + Z để hoàn tác</span>
      </div>
    </div>
  )
}

type InlineImageOptions = { token: string }

const InlineImage = Node.create<InlineImageOptions>({
  name: 'image',
  group: 'block',
  atom: true,
  draggable: true,
  isolating: true,
  addOptions: () => ({ token: '' }),
  addAttributes() {
    return {
      fileId: {
        default: null,
        parseHTML: (element) => Number(element.getAttribute('data-file-id')) || null,
        renderHTML: (attributes) => attributes.fileId ? { 'data-file-id': String(attributes.fileId) } : {},
      },
      alt: {
        default: '',
        parseHTML: (element) => element.querySelector('img')?.getAttribute('alt') ?? '',
      },
    }
  },
  parseHTML: () => [{ tag: 'figure[data-post-inline-image]' }],
  renderHTML({ HTMLAttributes }) {
    const alt = typeof HTMLAttributes.alt === 'string' ? HTMLAttributes.alt : ''
    const figureAttributes = { ...HTMLAttributes }
    delete figureAttributes.alt
    return ['figure', mergeAttributes(figureAttributes, { 'data-post-inline-image': '' }), ['img', { alt }]]
  },
  addNodeView() {
    return ReactNodeViewRenderer(InlineImageNodeView)
  },
})

function InlineImageNodeView({ node, extension, selected, deleteNode }: NodeViewProps) {
  const fileId = typeof node.attrs.fileId === 'number' ? node.attrs.fileId : Number(node.attrs.fileId)
  const alt = typeof node.attrs.alt === 'string' ? node.attrs.alt : ''
  const token = (extension.options as InlineImageOptions).token
  const [state, setState] = useState<{ url: string | null; failed: boolean }>({ url: null, failed: false })

  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    if (!Number.isSafeInteger(fileId) || fileId <= 0) {
      setState({ url: null, failed: true })
      return
    }
    setState({ url: null, failed: false })
    void downloadFile(token, fileId)
      .then((blob) => {
        if (!active) return
        objectUrl = URL.createObjectURL(blob)
        setState({ url: objectUrl, failed: false })
      })
      .catch(() => {
        if (active) setState({ url: null, failed: true })
      })
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [fileId, token])

  return <NodeViewWrapper
    as="figure"
    className={`post-rich-inline-image post-rich-inline-image-editor${selected ? ' is-selected' : ''}`}
    data-file-id={fileId}
    data-post-inline-image=""
  >
    {state.url ? <img src={state.url} alt={alt} draggable={false} /> : <div className="post-rich-inline-image-state">{state.failed ? 'Không thể tải ảnh.' : 'Đang tải ảnh…'}</div>}
    {alt ? <figcaption>{alt}</figcaption> : null}
    <button type="button" className="post-rich-inline-image-remove" aria-label="Xóa ảnh khỏi nội dung" title="Xóa ảnh khỏi nội dung" onMouseDown={(event) => event.preventDefault()} onClick={deleteNode}><X aria-hidden="true" /></button>
  </NodeViewWrapper>
}

function ToolbarButton({ label, active = false, disabled = false, onClick, children }: {
  label: string
  active?: boolean
  disabled?: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return <button
    type="button"
    className={active ? 'is-active' : undefined}
    aria-label={label}
    aria-pressed={active}
    title={label}
    disabled={disabled}
    onClick={onClick}
  >{children}</button>
}

function editorContent(value: PostContentDocument): JSONContent {
  if (value.content) return { type: 'doc', content: value.content as JSONContent[] }
  const migrated = createPostDocumentFromText(value.body ?? '')
  return { type: 'doc', content: migrated.content as JSONContent[] }
}

function normalizeLink(value: string): string | null {
  const href = value.trim()
  if (!href) return null
  if (/^(https?:|mailto:)/i.test(href)) return href
  if (/^[\w.-]+\.[a-z]{2,}(?:[/?#].*)?$/i.test(href)) return `https://${href}`
  window.alert('Liên kết phải bắt đầu bằng http://, https:// hoặc mailto:.')
  return null
}
