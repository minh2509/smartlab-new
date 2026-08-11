import { Bell, ChevronDown, LogIn, LogOut, Newspaper } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { Logo } from './Logo'

const publicLinks = [
  { to: '/du-an', label: 'Dự án' },
  { to: '/bai-viet', label: 'Bài viết' },
  { to: '/tai-lieu', label: 'Tài liệu' },
  { to: '/su-kien', label: 'Sự kiện' },
]

const aboutLinks = [
  { to: '/gioi-thieu', title: 'Về phòng Lab', description: 'Tổng quan, mục tiêu, định hướng' },
  { to: '/linh-vuc', title: 'Lĩnh vực nghiên cứu', description: 'AI · Robotics · Kỹ thuật phần mềm' },
  { to: '/thanh-vien', title: 'Thành viên', description: 'Đội ngũ và nhóm nghiên cứu' },
  { to: '/thu-vien-anh', title: 'Hình ảnh hoạt động', description: 'Thư viện ảnh của Lab' },
]

export function AppHeader() {
  const { isAuthenticated, logout } = useAuth()

  return (
    <header className="site-nav">
      <div className="nav-in">
        <NavLink to="/" className="logo" aria-label="Smart Lab">
          <Logo />
        </NavLink>

        <ul className="menu public-nav">
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
            <>
              <li>
                <NavLink to="/profile">Hồ sơ</NavLink>
              </li>
              <li>
                <NavLink to="/admin/accounts">Admin</NavLink>
              </li>
            </>
          ) : null}
        </ul>

        <div className="nav-act">
          {isAuthenticated ? (
            <>
              <NavLink
                className={({ isActive }) => `nav-private-link${isActive ? ' is-active' : ''}`}
                to="/posts"
              >
                <Newspaper size={15} aria-hidden="true" />
                Bảng tin
              </NavLink>
              <button className="icon-btn" type="button" aria-label="Thông báo">
                <Bell />
              </button>
              <button className="btn sm" type="button" onClick={() => void logout()}>
                <LogOut size={15} />
                Đăng xuất
              </button>
            </>
          ) : (
            <NavLink className="nav-login-btn" to="/login">
              <span className="nav-login-ico">
                <LogIn size={15} />
              </span>
              <span>Đăng nhập</span>
            </NavLink>
          )}
        </div>
      </div>
    </header>
  )
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
