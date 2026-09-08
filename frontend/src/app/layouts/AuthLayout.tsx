import { Link, Outlet } from 'react-router-dom'
import { Logo } from '../../shared/components/Logo'
import { Cpu, Terminal, ShieldCheck, ArrowUpRight, Activity } from 'lucide-react'

export function AuthLayout() {
  return (
    <div className="auth-split-layout">
      {/* Left Dossier Showcase */}
      <aside className="auth-dossier" aria-label="SmartLab Dossier">
        <div className="auth-dossier-grid-bg" aria-hidden="true" />
        <div className="auth-dossier-content">
          <div className="auth-dossier-head">
            <Link to="/" className="auth-dossier-logo" aria-label="Trang chủ Smart Lab">
              <Logo />
            </Link>
            <div className="auth-dossier-badge">
              <Activity size={13} className="auth-dossier-pulse" />
              <span>LAB CONSOLE · ONLINE</span>
            </div>
          </div>

          <div className="auth-dossier-body">
            <div className="auth-dossier-eyebrow">RESEARCH ENVIRONMENT</div>
            <h2 className="auth-dossier-title">Nghiên cứu thật.<br />Dự án thật.<br />Sản phẩm thật.</h2>
            <p className="auth-dossier-desc">
              Cổng xác thực tập trung cho thành viên, nghiên cứu sinh và giảng viên hướng dẫn tại Smart Lab.
            </p>

            <div className="auth-dossier-tracks">
              <div className="auth-dossier-track">
                <span className="auth-track-icon"><Cpu size={18} /></span>
                <div className="auth-track-copy">
                  <strong>Artificial Intelligence</strong>
                  <small>Thị giác máy tính, Học tăng cường & LLM</small>
                </div>
              </div>
              <div className="auth-dossier-track">
                <span className="auth-track-icon"><Terminal size={18} /></span>
                <div className="auth-track-copy">
                  <strong>Robotics & Automation</strong>
                  <small>Điều khiển nhúng, ROS2 & Bản đồ SLAM</small>
                </div>
              </div>
              <div className="auth-dossier-track">
                <span className="auth-track-icon"><ShieldCheck size={18} /></span>
                <div className="auth-track-copy">
                  <strong>Software Engineering</strong>
                  <small>Kiến trúc phân tán, CI/CD & Bảo mật hệ thống</small>
                </div>
              </div>
            </div>
          </div>

          <div className="auth-dossier-foot">
            <span className="auth-dossier-copyright">© 2026 Smart Lab. Khoa CNTT.</span>
            <Link to="/" className="auth-dossier-backlink">
              Quay lại trang chủ <ArrowUpRight size={14} />
            </Link>
          </div>
        </div>
      </aside>

      {/* Right Auth Console */}
      <main className="auth-console">
        <div className="auth-console-inner">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
