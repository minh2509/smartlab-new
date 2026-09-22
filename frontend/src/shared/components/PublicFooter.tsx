import { Clock3, Mail, MapPin, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { Logo } from './Logo'

const quickLinks = [
  { to: '/gioi-thieu', label: 'Về Smart Lab' },
  { to: '/linh-vuc', label: 'Lĩnh vực nghiên cứu' },
  { to: '/thanh-vien', label: 'Thành viên' },
  { to: '/lien-he', label: 'Liên hệ' },
]

const exploreLinks = [
  { to: '/du-an', label: 'Dự án' },
  { to: '/bai-viet', label: 'Bài viết' },
  { to: '/su-kien', label: 'Sự kiện' },
  { to: '/thu-vien-anh', label: 'Thư viện ảnh' },
]

export function PublicFooter() {
  const { isAuthenticated } = useAuth()

  return (
    <footer className="site-foot" role="contentinfo">
      <div className="wrap foot-grid">
        <section className="foot-about" aria-labelledby="footer-brand-title">
          <div className="footer-logo-lockup">
            <Logo />
          </div>
          <h2 id="footer-brand-title" className="sr-only">Smart Lab</h2>
          <p>Phòng Lab sinh viên tập trung vào nghiên cứu, phát triển sản phẩm và chia sẻ tri thức trong cộng đồng.</p>
          <div className="foot-contact" aria-label="Thông tin liên hệ Smart Lab">
            <div><MapPin aria-hidden="true" /><span>Phòng A3-502, Hoà Lạc</span></div>
            <div><Mail aria-hidden="true" /><a href="mailto:smartlab@example.edu.vn">smartlab@example.edu.vn</a></div>
            <div><Clock3 aria-hidden="true" /><span>Thứ 2 – Thứ 6, 09:00 – 17:00</span></div>
          </div>
        </section>

        <nav aria-label="Thông tin Smart Lab">
          <h3>Smart Lab</h3>
          <ul>{quickLinks.map((link) => <li key={link.to}><Link to={link.to}>{link.label}</Link></li>)}</ul>
        </nav>

        <nav aria-label="Khám phá nội dung">
          <h3>Khám phá</h3>
          <ul>{exploreLinks.map((link) => <li key={link.to}><Link to={link.to}>{link.label}</Link></li>)}</ul>
        </nav>

        <section aria-labelledby="footer-workspace-title">
          <h3 id="footer-workspace-title">Không gian làm việc</h3>
          <p className="footer-workspace-copy">{isAuthenticated ? 'Tiếp tục công việc và cập nhật hoạt động của bạn.' : 'Thành viên Smart Lab có thể đăng nhập để mở workspace riêng.'}</p>
          <Link className="footer-workspace-link" to={isAuthenticated ? '/profile' : '/login'}>
            {isAuthenticated ? 'Mở workspace' : 'Đăng nhập'} <ArrowUpRight size={15} aria-hidden="true" />
          </Link>
        </section>
      </div>
      <div className="wrap foot-btm">
        <span>© {new Date().getFullYear()} Smart Lab. Nội dung thuộc Smart Lab.</span>
        <Link to="/lien-he">Cần hỗ trợ? Liên hệ Smart Lab</Link>
      </div>
    </footer>
  )
}
