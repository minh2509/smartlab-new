import { ChevronDown, LogIn, LogOut, Menu, Newspaper, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { NotificationPopover } from '../../features/notifications/components/NotificationPopover'
import { Logo } from './Logo'

const publicLinks = [
  { to: '/du-an', label: 'Dự án' },
  { to: '/bai-viet', label: 'Bài viết' },
  { to: '/tai-lieu', label: 'Tài liệu' },
  { to: '/su-kien', label: 'Sự kiện' },
  { to: '/thanh-vien', label: 'Thành viên' },
]

const aboutLinks = [
  { to: '/gioi-thieu', title: 'Về phòng Lab', description: 'Tổng quan, mục tiêu, định hướng' },
  { to: '/linh-vuc', title: 'Lĩnh vực nghiên cứu', description: 'AI · Robotics · Kỹ thuật phần mềm' },
  { to: '/thu-vien-anh', title: 'Hình ảnh hoạt động', description: 'Thư viện ảnh của Lab' },
]

export function AppHeader() {
  const { isAuthenticated, logout, profile } = useAuth()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const role = resolveHeaderRole(profile?.roles)
  const canReadNotifications = profile?.permissions.includes('notifications.read_own') ?? false

  useEffect(() => setMobileOpen(false), [location.pathname])

  useEffect(() => {
    if (!mobileOpen) return
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') setMobileOpen(false) }
    document.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [mobileOpen])

  return (
    <header className="site-nav">
      <div className="nav-in">
        <NavLink to="/" className="logo" aria-label="Smart Lab">
          <Logo />
        </NavLink>

        <nav className="public-nav" aria-label="Điều hướng chính">
          <ul className="menu">
            <li>
              <NavLink to="/">Trang chủ</NavLink>
            </li>
            <li>
              <AboutDropdown />
            </li>
            {publicLinks.map((link) => (
              <li key={link.to}>
                <NavLink to={link.to}>{link.label}</NavLink>
              </li>
            ))}
            {isAuthenticated ? (
              <li>
                <NavLink to="/profile">Workspace</NavLink>
              </li>
            ) : null}
          </ul>
        </nav>

        <div className="nav-act">
          {isAuthenticated ? (
            <>
              {role ? <span className="nav-role-label nav-utility-role">{role}</span> : null}
              {role ? <span className="nav-utility-separator" aria-hidden="true" /> : null}
              <div className="nav-utility-actions">
                <NavLink
                  end
                  className={({ isActive }) => `nav-private-link${isActive ? ' is-active' : ''}`}
                  to="/posts"
                >
                  <Newspaper size={15} aria-hidden="true" />
                  Bảng tin
                </NavLink>
                {canReadNotifications ? <NotificationPopover /> : null}
                <button className="btn sm" type="button" onClick={() => void logout()}>
                  <LogOut size={15} />
                  Đăng xuất
                </button>
              </div>
            </>
          ) : (
            <>
              <NavLink
                end
                className={({ isActive }) => `nav-private-link nav-public-feed-link${isActive ? ' is-active' : ''}`}
                to="/posts"
              >
                <Newspaper size={15} aria-hidden="true" />
                Bảng tin
              </NavLink>
              <NavLink className="nav-login-btn" to="/login">
                <span className="nav-login-ico">
                  <LogIn size={15} />
                </span>
                <span>Đăng nhập</span>
              </NavLink>
            </>
          )}
          <button
            className="nav-mobile-toggle burger"
            type="button"
            aria-label={mobileOpen ? 'Đóng menu điều hướng' : 'Mở menu điều hướng'}
            aria-controls="mobile-navigation"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen((value) => !value)}
          >
            {mobileOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
          </button>
        </div>
      </div>
      {mobileOpen ? <MobileNavigation isAuthenticated={isAuthenticated} role={role} onClose={() => setMobileOpen(false)} onLogout={() => { setMobileOpen(false); void logout() }} /> : null}
    </header>
  )
}

function MobileNavigation({ isAuthenticated, role, onClose, onLogout }: { isAuthenticated: boolean; role: string | null; onClose: () => void; onLogout: () => void }) {
  return (
    <div className="mnav open" id="mobile-navigation" role="dialog" aria-modal="true" aria-label="Menu điều hướng">
      <div className="mnav-top">
        <Link to="/" className="logo" aria-label="Smart Lab" onClick={onClose}><Logo /></Link>
        <button className="nav-mobile-close" type="button" aria-label="Đóng menu điều hướng" onClick={onClose}><X aria-hidden="true" /></button>
      </div>
      <nav aria-label="Điều hướng mobile">
        <ul>
          <li><NavLink to="/" end onClick={onClose}>Trang chủ</NavLink></li>
          <li><NavLink to="/gioi-thieu" onClick={onClose}>Giới thiệu</NavLink></li>
          {publicLinks.map((link) => <li key={link.to}><NavLink to={link.to} onClick={onClose}>{link.label}</NavLink></li>)}
          {isAuthenticated ? <li><NavLink to="/profile" onClick={onClose}>Workspace{role ? ` · ${role}` : ''}</NavLink></li> : null}
          <li><NavLink to="/posts" onClick={onClose}>Bảng tin</NavLink></li>
        </ul>
      </nav>
      <div className="mnav-act">
        {isAuthenticated ? <button className="btn primary block" type="button" onClick={onLogout}><LogOut size={16} aria-hidden="true" /> Đăng xuất</button> : <Link className="btn primary block" to="/login" onClick={onClose}><LogIn size={16} aria-hidden="true" /> Đăng nhập</Link>}
      </div>
    </div>
  )
}

function resolveHeaderRole(roles: string[] | undefined) {
  if (roles?.includes('ADMIN')) return 'Admin'
  if (roles?.includes('LEADER')) return 'Leader'
  if (roles?.includes('MEMBER')) return 'Member'
  return null
}

function AboutDropdown() {
  const [isOpen, setOpen] = useState(false)
  const location = useLocation()
  const menuRef = useRef<HTMLDivElement>(null)
  const isActive = aboutLinks.some((link) => link.to === location.pathname)

  useEffect(() => {
    setOpen(false)
  }, [location.pathname])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [])

  return (
    <div className={isOpen ? 'nav-dropdown open' : 'nav-dropdown'} ref={menuRef}>
      <button
        className={isActive ? 'nav-dropdown-trigger active' : 'nav-dropdown-trigger'}
        type="button"
        aria-expanded={isOpen}
        aria-haspopup="true"
        onClick={() => setOpen((value) => !value)}
      >
        Giới thiệu
        <ChevronDown aria-hidden="true" />
      </button>
      <div className="nav-dropdown-menu">
        {aboutLinks.map((link) => (
          <NavLink className="nav-dropdown-item" to={link.to} key={link.to}>
            <span>{link.title}</span>
            <small>{link.description}</small>
          </NavLink>
        ))}
      </div>
    </div>
  )
}
