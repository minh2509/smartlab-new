import { Fragment, type ReactNode } from 'react'
import type { PostContentDocument, PostContentMark, PostContentNode } from '../types'

type Props = {
  document: PostContentDocument
  className?: string
  renderImage?: (fileId: number, alt: string, key: string) => ReactNode
}

export function PostRichText({ document, className, renderImage }: Props) {
  if (document.content) {
    return <div className={className}>{document.content.map((node, index) => renderNode(node, `root-${index}`, renderImage))}</div>
  }
  return <p className={className}>{document.body ?? ''}</p>
}

function renderNode(node: PostContentNode, key: string, renderImage?: Props['renderImage']): ReactNode {
  if (node.type === 'text') return <Fragment key={key}>{applyMarks(node.text ?? '', node.marks ?? [], key)}</Fragment>
  if (node.type === 'hardBreak') return <br key={key} />
  if (node.type === 'horizontalRule') return <hr key={key} />
  if (node.type === 'image') {
    const rawFileId = node.attrs?.fileId
    const fileId = typeof rawFileId === 'number' ? rawFileId : Number(rawFileId)
    const alt = typeof node.attrs?.alt === 'string' ? node.attrs.alt : ''
    if (!Number.isSafeInteger(fileId) || fileId <= 0) return null
    return renderImage?.(fileId, alt, key) ?? <figure className="post-rich-inline-image" key={key}><div className="post-rich-inline-image-state">Ảnh #{fileId}</div></figure>
  }

  const children = (node.content ?? []).map((child, index) => renderNode(child, `${key}-${index}`, renderImage))
  switch (node.type) {
    case 'paragraph': return <p className={alignmentClass(node)} key={key}>{children.length ? children : <br />}</p>
    case 'heading': {
      const level = numberAttr(node, 'level', 2)
      if (level === 1) return <h1 className={alignmentClass(node)} key={key}>{children}</h1>
      if (level === 3) return <h3 className={alignmentClass(node)} key={key}>{children}</h3>
      return <h2 className={alignmentClass(node)} key={key}>{children}</h2>
    }
    case 'bulletList': return <ul key={key}>{children}</ul>
    case 'orderedList': return <ol key={key} start={numberAttr(node, 'start', 1)}>{children}</ol>
    case 'listItem': return <li key={key}>{children}</li>
    case 'blockquote': return <blockquote key={key}>{children}</blockquote>
    case 'codeBlock': return <pre key={key}><code>{plainNodeText(node)}</code></pre>
    default: return <Fragment key={key}>{children}</Fragment>
  }
}

function applyMarks(text: string, marks: PostContentMark[], key: string): ReactNode {
  return marks.reduce<ReactNode>((value, mark, index) => {
    const markKey = `${key}-mark-${index}`
    switch (mark.type) {
      case 'bold': return <strong key={markKey}>{value}</strong>
      case 'italic': return <em key={markKey}>{value}</em>
      case 'underline': return <u key={markKey}>{value}</u>
      case 'strike': return <s key={markKey}>{value}</s>
      case 'code': return <code key={markKey}>{value}</code>
      case 'link': {
        const href = safeHref(mark.attrs?.href)
        return href ? <a href={href} key={markKey} rel="nofollow noopener noreferrer" target="_blank">{value}</a> : value
      }
      default: return value
    }
  }, text)
}

function safeHref(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const href = value.trim()
  if (/^(https?:|mailto:)/i.test(href)) return href
  return null
}

function numberAttr(node: PostContentNode, name: string, fallback: number): number {
  const value = node.attrs?.[name]
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function alignmentClass(node: PostContentNode): string | undefined {
  const align = node.attrs?.textAlign
  return typeof align === 'string' && ['left', 'center', 'right', 'justify'].includes(align)
    ? `post-rich-align-${align}`
    : undefined
}

function plainNodeText(node: PostContentNode): string {
  if (node.type === 'text') return node.text ?? ''
  if (node.type === 'hardBreak') return '\n'
  return (node.content ?? []).map(plainNodeText).join('')
}
