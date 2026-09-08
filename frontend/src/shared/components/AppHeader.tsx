import { ChevronDown, LogIn, LogOut, Newspaper } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/authContext'
import { NotificationPopover } from '../../features/notifications/components/NotificationPopover'
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
  { to: '/thu-vien-anh', title: 'Hình ảnh hoạt động', description: 'Thư viện ảnh của Lab' },
]

export function AppHeader() {
  const { isAuthenticated, logout, profile } = useAuth()
  const role = resolveHeaderRole(profile?.roles)
  const canReadNotifications = profile?.permissions.includes('notifications.read_own') ?? false

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
        </div>
      </div>
    </header>
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
  const triggerRef = useRef<HTMLButtonElement>(null)
  const rafRef = useRef<number | null>(null)
  const isActive = aboutLinks.some((link) => link.to === location.pathname)

  const resetDrift = useCallback(() => {
    if (rafRef.current) {
      cancelAnimationFrame(rafRef.current)
      rafRef.current = null
    }
    if (menuRef.current) {
      menuRef.current.style.setProperty('--dropdown-drift', '0px')
    }
  }, [])

  useEffect(() => {
    setOpen(false)
    resetDrift()
  }, [location.pathname, resetDrift])

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpen(false)
        resetDrift()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current)
      }
    }
  }, [resetDrift])

  const handlePointerMove = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (event.pointerType === 'touch') return
    if (typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    const clientX = event.clientX
    if (rafRef.current) cancelAnimationFrame(rafRef.current)
    rafRef.current = requestAnimationFrame(() => {
      if (!triggerRef.current || !menuRef.current) return
      const rect = triggerRef.current.getBoundingClientRect()
      if (rect.width <= 0) return

      // Normalized X relative to trigger: left edge = -1, center = 0, right edge = +1
      const rawNormalized = ((clientX - rect.left) / rect.width) * 2 - 1
      const normalized = Math.max(-1, Math.min(1, rawNormalized))

      // Restrained, subtle travel distance (10px max)
      // Sine curve softens extremes and gives physical, spring-like boundary
      const MAX_DRIFT = 10
      const drift = Math.sin(normalized * (Math.PI / 2)) * MAX_DRIFT

      // Viewport collision clamping
      const triggerCenterX = rect.left + rect.width / 2
      const halfMenuWidth = 146 // 292px width
      const VIEWPORT_PADDING = 12
      let clampedDrift = drift
      const projectedLeft = triggerCenterX - halfMenuWidth + drift
      const projectedRight = triggerCenterX + halfMenuWidth + drift

      if (projectedLeft < VIEWPORT_PADDING) {
        clampedDrift += (VIEWPORT_PADDING - projectedLeft)
      } else if (projectedRight > window.innerWidth - VIEWPORT_PADDING) {
        clampedDrift -= (projectedRight - (window.innerWidth - VIEWPORT_PADDING))
      }

      menuRef.current.style.setProperty('--dropdown-drift', `${clampedDrift.toFixed(2)}px`)
    })
  }

  return (
    <div
      className={isOpen ? 'nav-dropdown open' : 'nav-dropdown'}
      ref={menuRef}
      onPointerLeave={resetDrift}
    >
      <button
        ref={triggerRef}
        className={isActive ? 'nav-dropdown-trigger active' : 'nav-dropdown-trigger'}
        type="button"
        aria-expanded={isOpen}
        aria-haspopup="true"
        onClick={() => setOpen((value) => !value)}
        onPointerMove={handlePointerMove}
        onPointerLeave={resetDrift}
        onFocus={resetDrift}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setOpen(false)
            resetDrift()
          }
        }}
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
