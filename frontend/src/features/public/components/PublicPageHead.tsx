import { Link } from 'react-router-dom'

type PublicPageHeadProps = {
  title: string
  description: string
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
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
    </section>
  )
}
