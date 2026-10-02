import { Link } from 'react-router-dom'

type PublicPageHeadProps = {
  title: string
  description: string
}

function renderFormattedTitle(title: string) {
  if (!title) return title
  const regex = /(\([^)]+\))/g
  const parts = title.split(regex)
  if (parts.length === 1) return title

  return parts.map((part, index) => {
    if (part.startsWith('(') && part.endsWith(')')) {
      return (
        <span key={index} className="title-cluster">
          {part}
        </span>
      )
    }
    return part
  })
}

export function PublicPageHead({ title, description }: PublicPageHeadProps) {
  return (
    <section className="pagehead">
      <div className="wrap">
        <nav className="crumb" aria-label="Đường dẫn">
          <Link to="/">Trang chủ</Link>
          <span className="sep">/</span>
          <b>{title}</b>
        </nav>
        <h1>{renderFormattedTitle(title)}</h1>
        <p>{description}</p>
      </div>
    </section>
  )
}
