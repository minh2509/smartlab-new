import type { PostContentAttachmentReference, PostContentDocument, PostContentMark, PostContentNode } from './types'

export function parsePostContent(contentJson: Record<string, unknown> | undefined): PostContentDocument | null {
  if (contentJson?.type !== 'doc') return null
  const body = typeof contentJson.body === 'string' ? contentJson.body : undefined
  const content = Array.isArray(contentJson.content) ? contentJson.content.flatMap(parseNode) : undefined
  if (body === undefined && content === undefined) return null
  const files = Array.isArray(contentJson.files)
    ? contentJson.files.flatMap(parseReference)
    : []
  return {
    type: 'doc',
    ...(body !== undefined ? { body } : {}),
    ...(content !== undefined ? { content } : {}),
    ...(files.length ? { files } : {}),
  }
}

export function serializePostContent(
  document: Pick<PostContentDocument, 'type' | 'body' | 'content'>,
  files: PostContentAttachmentReference[],
): PostContentDocument {
  return {
    type: 'doc',
    ...(document.content ? { content: document.content } : {}),
    ...(document.body !== undefined && !document.content ? { body: document.body } : {}),
    ...(files.length ? { files } : {}),
  }
}

export function createPostDocumentFromText(body: string): PostContentDocument {
  const lines = body.replace(/\r\n?/g, '\n').split('\n')
  return {
    type: 'doc',
    content: lines.map((line) => ({
      type: 'paragraph',
      ...(line ? { content: [{ type: 'text', text: line }] } : {}),
    })),
  }
}

export function extractPostPlainText(document: PostContentDocument | null): string {
  if (!document) return ''
  if (document.body !== undefined) return document.body
  return (document.content ?? []).map(nodeText).join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

export function inlineImageFileIds(document: PostContentDocument | null): Set<number> {
  const ids = new Set<number>()
  function visit(node: PostContentNode) {
    if (node.type === 'image') {
      const value = node.attrs?.fileId
      const fileId = typeof value === 'number' ? value : Number(value)
      if (Number.isSafeInteger(fileId) && fileId > 0) ids.add(fileId)
    }
    node.content?.forEach(visit)
  }
  document?.content?.forEach(visit)
  return ids
}

function nodeText(node: PostContentNode): string {
  if (node.type === 'text') return node.text ?? ''
  if (node.type === 'hardBreak') return '\n'
  const value = (node.content ?? []).map(nodeText).join('')
  return ['paragraph', 'heading', 'blockquote', 'listItem', 'codeBlock'].includes(node.type) ? `${value}\n` : value
}

function parseNode(value: unknown): PostContentNode[] {
  if (!value || typeof value !== 'object') return []
  const node = value as Record<string, unknown>
  if (typeof node.type !== 'string') return []
  const parsed: PostContentNode = { type: node.type }
  if (node.attrs && typeof node.attrs === 'object' && !Array.isArray(node.attrs)) {
    parsed.attrs = { ...(node.attrs as Record<string, unknown>) }
  }
  if (Array.isArray(node.content)) parsed.content = node.content.flatMap(parseNode)
  if (Array.isArray(node.marks)) {
    parsed.marks = node.marks.flatMap((mark): PostContentMark[] => {
      if (!mark || typeof mark !== 'object' || typeof (mark as Record<string, unknown>).type !== 'string') return []
      const value = mark as Record<string, unknown>
      return [{
        type: value.type as string,
        ...(value.attrs && typeof value.attrs === 'object' && !Array.isArray(value.attrs)
          ? { attrs: { ...(value.attrs as Record<string, unknown>) } }
          : {}),
      }]
    })
  }
  if (typeof node.text === 'string') parsed.text = node.text
  return [parsed]
}

function parseReference(value: unknown): PostContentAttachmentReference[] {
  if (!value || typeof value !== 'object') return []
  const reference = value as Record<string, unknown>
  if (!Number.isSafeInteger(reference.fileId) || (reference.fileId as number) <= 0) return []
  if (reference.type === 'image') {
    return [{ type: 'image', fileId: reference.fileId as number, ...(typeof reference.alt === 'string' ? { alt: reference.alt } : {}) }]
  }
  if (reference.type === 'file') {
    return [{ type: 'file', fileId: reference.fileId as number, ...(typeof reference.label === 'string' ? { label: reference.label } : {}) }]
  }
  return []
}
