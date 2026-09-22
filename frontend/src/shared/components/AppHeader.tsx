import { ChevronDown, LogIn, LogOut, Newspaper, Search } from "lucide-react";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from "react";
import {
  matchPath,
  NavLink,
  useLocation,
  useNavigate,
  useResolvedPath,
} from "react-router-dom";
import { useAuth } from "../../features/auth/authContext";
import { NotificationPopover } from "../../features/notifications/components/NotificationPopover";
import { Logo } from "./Logo";

type NavLeaf = {
  to: string;
  label: string;
  description?: string;
  end?: boolean;
};
type NavGroup = { label: string; children: readonly NavLeaf[] };
type NavItem = NavLeaf | NavGroup;

const mainNav: readonly NavItem[] = [
  { to: "/", label: "Trang chủ", end: true },
  { to: "/gioi-thieu", label: "Về Phòng Lab" },
  {
    label: "Nghiên cứu",
    children: [
      {
        to: "/du-an",
        label: "Dự án",
        description: "Từ ý tưởng tới hiện thực.",
      },
      {
        to: "/tai-lieu",
        label: "Tài liệu",
        description: "Tri thức cho hành trình nghiên cứu",
      },
    ], 
  },
  {
    label: "Truyền thông",
    children: [
      {
        to: "/tin-tuc",
        label: "Tin tức",
        description: "Tin tức và truyền thông về Phòng Lab.",
      },
      {
        to: "/bai-viet",
        label: "Bài viết",
        description: "Những góc nhìn và chia sẻ khác nhau.",
      },
      {
        to: "/su-kien",
        label: "Sự kiện",
        description: "Kết nối, học hỏi và trải nghiệm.",
      },
    ],
  },
];

const ROLE_LABELS = [
  { key: "ADMIN", label: "Admin" },
  { key: "LEADER", label: "Leader" },
  { key: "MEMBER", label: "Member" },
] as const;

const PERMISSION_READ_OWN_NOTIFICATIONS = "notifications.read_own";
const DROPDOWN_VIEWPORT_MARGIN = 12;

function isGroup(item: NavItem): item is NavGroup {
  return "children" in item;
}

function isCoarsePointer() {
  return window.matchMedia("(pointer: coarse)").matches;
}

function resolveHeaderRole(roles: readonly string[] | null | undefined) {
  if (!roles?.length) return null;
  return ROLE_LABELS.find(({ key }) => roles.includes(key))?.label ?? null;
}

function hasPermission(
  permissions: readonly string[] | null | undefined,
  permission: string,
) {
  return permissions?.includes(permission) ?? false;
}

function joinClassNames(...names: Array<string | false | undefined>) {
  return names.filter(Boolean).join(" ");
}

function updateDropdownDrift(
  root: HTMLElement | null,
  menu: HTMLElement | null,
) {
  if (!root || !menu) return;
  const rootRect = root.getBoundingClientRect();
  const viewportWidth = document.documentElement.clientWidth;
  const centered = rootRect.left + rootRect.width / 2 - menu.offsetWidth / 2;
  const maxLeft = viewportWidth - menu.offsetWidth - DROPDOWN_VIEWPORT_MARGIN;
  const clamped = Math.min(
    Math.max(centered, DROPDOWN_VIEWPORT_MARGIN),
    Math.max(DROPDOWN_VIEWPORT_MARGIN, maxLeft),
  );
  menu.style.setProperty(
    "--dropdown-drift",
    `${Math.round(clamped - centered)}px`,
  );
}

const SamePageClickContext = createContext<(() => void) | undefined>(undefined);

function ReloadNavLink({
  to,
  onClick,
  ...rest
}: ComponentPropsWithoutRef<typeof NavLink>) {
  const { pathname, search } = useLocation();
  const resolved = useResolvedPath(to);
  const navigate = useNavigate();
  const onSamePageClick = useContext(SamePageClickContext);

  function handleClick(event: ReactMouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented) return;
    if (
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return;
    }
    if (resolved.pathname !== pathname || !onSamePageClick) return;

    event.preventDefault();
    if (resolved.search !== search) {
      navigate(
        { pathname: resolved.pathname, search: resolved.search },
        { replace: true },
      );
    }
    onSamePageClick();
  }

  return <NavLink {...rest} to={to} onClick={handleClick} />;
}

type NavDropdownProps = {
  group: NavGroup;
  pinned: boolean;
  onTogglePinned: () => void;
  onClosePinned: () => void;
};

function NavDropdown({
  group,
  pinned,
  onTogglePinned,
  onClosePinned,
}: NavDropdownProps) {
  const { pathname } = useLocation();
  const menuId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);

  const coarse = isCoarsePointer();
  const open = coarse ? pinned : hovered || focused;

  const isActive = group.children.some((child) =>
    matchPath({ path: child.to, end: child.end ?? false }, pathname),
  );

  useEffect(() => {
    if (!open) return;
    const sync = () => updateDropdownDrift(rootRef.current, menuRef.current);
    sync();
    window.addEventListener("resize", sync);
    return () => window.removeEventListener("resize", sync);
  }, [open]);

  function handleKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Escape") return;
    onClosePinned();
    setHovered(false);
    setFocused(false);
    (document.activeElement as HTMLElement | null)?.blur();
  }

  function handleTriggerClick() {
    if (!coarse) return;
    if (pinned) buttonRef.current?.blur();
    onTogglePinned();
  }

  function handleItemClick(event: ReactMouseEvent<HTMLAnchorElement>) {
    event.currentTarget.blur();
    onClosePinned();
  }

  return (
    <li>
      <div
        ref={rootRef}
        className={joinClassNames("nav-dropdown", open && "open")}
        onKeyDown={handleKeyDown}
        onPointerEnter={(event) => {
          if (event.pointerType === "mouse") setHovered(true);
        }}
        onPointerLeave={(event) => {
          if (event.pointerType === "mouse") setHovered(false);
        }}
        onFocus={(event) => setFocused(event.target.matches(":focus-visible"))}
        onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) {
            setFocused(false);
          }
        }}
      >
        <button
          ref={buttonRef}
          type="button"
          className={joinClassNames(
            "nav-dropdown-trigger",
            isActive && "active",
          )}
          aria-expanded={open}
          aria-controls={menuId}
          onClick={handleTriggerClick}
        >
          {group.label}
          <ChevronDown size={14} aria-hidden="true" />
        </button>
        <div ref={menuRef} id={menuId} className="nav-dropdown-menu">
          {group.children.map((child) => (
            <ReloadNavLink
              key={child.to}
              to={child.to}
              end={child.end}
              className="nav-dropdown-item"
              onClick={handleItemClick}
            >
              <span>{child.label}</span>
              {child.description ? <small>{child.description}</small> : null}
            </ReloadNavLink>
          ))}
        </div>
      </div>
    </li>
  );
}

function FeedLink({ extraClassName }: { extraClassName?: string }) {
  return (
    <ReloadNavLink
      end
      to="/posts"
      className={({ isActive }) =>
        joinClassNames(
          "nav-private-link",
          extraClassName,
          isActive && "is-active",
        )
      }
    >
      <Newspaper size={15} aria-hidden="true" />
      Bảng tin
    </ReloadNavLink>
  );
}

function SearchButton({ onClick }: { onClick?: () => void }) {
  return (
    <button
      className="icon-btn"
      type="button"
      aria-label="Tìm kiếm"
      onClick={onClick}
    >
      <Search size={18} aria-hidden="true" />
    </button>
  );
}

type AppHeaderProps = {
  onSearchOpen?: () => void;
  onSamePageClick?: () => void;
};

export function AppHeader({ onSearchOpen, onSamePageClick }: AppHeaderProps) {
  const { isAuthenticated, logout, profile } = useAuth();
  const { pathname } = useLocation();
  const navRef = useRef<HTMLElement>(null);
  const [pinnedGroup, setPinnedGroup] = useState<string | null>(null);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const role = resolveHeaderRole(profile?.roles);
  const canReadNotifications = hasPermission(
    profile?.permissions,
    PERMISSION_READ_OWN_NOTIFICATIONS,
  );

  useEffect(() => {
    setPinnedGroup(null);
  }, [pathname]);

  useEffect(() => {
    if (pinnedGroup === null) return;

    function handlePointerDown(event: PointerEvent) {
      if (!navRef.current?.contains(event.target as Node)) {
        setPinnedGroup(null);
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [pinnedGroup]);

  useEffect(() => {
    if (!onSearchOpen) return;

    function handleKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        onSearchOpen?.();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onSearchOpen]);

  const handleLogout = useCallback(async () => {
    if (isLoggingOut) return;
    setIsLoggingOut(true);
    try {
      await logout();
    } catch (error) {
      console.error("Đăng xuất thất bại", error);
    } finally {
      setIsLoggingOut(false);
    }
  }, [isLoggingOut, logout]);

  return (
    <SamePageClickContext.Provider value={onSamePageClick}>
      <header className="site-nav">
        <div className="nav-in">
          <ReloadNavLink to="/" className="logo" aria-label="Smart Lab">
            <Logo />
          </ReloadNavLink>

          <nav
            ref={navRef}
            className="public-nav"
            aria-label="Điều hướng chính"
          >
            <ul className="menu">
              {mainNav.map((item) =>
                isGroup(item) ? (
                  <NavDropdown
                    key={item.label}
                    group={item}
                    pinned={pinnedGroup === item.label}
                    onTogglePinned={() =>
                      setPinnedGroup((current) =>
                        current === item.label ? null : item.label,
                      )
                    }
                    onClosePinned={() =>
                      setPinnedGroup((current) =>
                        current === item.label ? null : current,
                      )
                    }
                  />
                ) : (
                  <li key={item.to}>
                    <ReloadNavLink end={item.end} to={item.to}>
                      {item.label}
                    </ReloadNavLink>
                  </li>
                ),
              )}
              {isAuthenticated ? (
                <li>
                  <ReloadNavLink to="/profile">Workspace</ReloadNavLink>
                </li>
              ) : null}
            </ul>
          </nav>

          <div className="nav-act">
            {isAuthenticated ? (
              <>
                {role ? (
                  <>
                    <span className="nav-role-label nav-utility-role">
                      {role}
                    </span>
                    <span
                      className="nav-utility-separator"
                      aria-hidden="true"
                    />
                  </>
                ) : null}
                <div className="nav-utility-actions">
                  <FeedLink />
                  <SearchButton onClick={onSearchOpen} />
                  {canReadNotifications ? <NotificationPopover /> : null}
                  <button
                    className="btn sm"
                    type="button"
                    disabled={isLoggingOut}
                    aria-busy={isLoggingOut}
                    onClick={handleLogout}
                  >
                    <LogOut size={15} aria-hidden="true" />
                    Đăng xuất
                  </button>
                </div>
              </>
            ) : (
              <>
                <FeedLink extraClassName="nav-public-feed-link" />
                <SearchButton onClick={onSearchOpen} />
                <ReloadNavLink className="nav-login-btn" to="/login">
                  <span className="nav-login-ico">
                    <LogIn size={15} aria-hidden="true" />
                  </span>
                  <span>Đăng nhập</span>
                </ReloadNavLink>
              </>
            )}
          </div>
        </div>
      </header>
    </SamePageClickContext.Provider>
  );
}
