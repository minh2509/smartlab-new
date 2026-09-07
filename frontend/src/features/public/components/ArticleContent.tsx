export interface ArticleContentDocument {
  body: string
}

function parseArticleContent(json: unknown): ArticleContentDocument | null {
  if (typeof json !== 'object' || json === null) return null
  const body = (json as Record<string, unknown>).body
  if (typeof body !== 'string') return null
  // The approved public Article contract is body-only; compatibility fields are intentionally ignored.
  return { body }
}

export function ArticleContent({ contentJson, className, bodyClassName, fallbackClassName }: {
  contentJson: Record<string, unknown>
  className?: string
  bodyClassName?: string
  fallbackClassName?: string
}) {
  const content = parseArticleContent(contentJson)

  if (!content) {
    return (
      <div className={fallbackClassName ?? 'article-page-content-state'}>
        <p>Nội dung này hiện chưa hỗ trợ hiển thị đầy đủ.</p>
      </div>
    )
  }

  return (
    <div className={className}>
      <p className={bodyClassName} style={{ whiteSpace: 'pre-wrap' }}>{content.body}</p>
    </div>
  )
}
