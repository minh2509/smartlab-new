import { ArrowRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

interface TerminalLine {
  text: string
  type: 'cmd' | 'info' | 'item' | 'success' | 'ready'
}

const TERMINAL_SCENES: TerminalLine[][] = [
  // Scene 1: Research Fields API
  [
    { text: '> import { getResearchFields } from "@/profile/api"', type: 'cmd' },
    { text: '> await getResearchFields()', type: 'cmd' },
    { text: 'GET /research-fields', type: 'info' },
    { text: 'type: Promise<ResearchField[]>', type: 'item' },
    { text: 'api contract verified', type: 'success' },
    { text: 'ready_', type: 'ready' },
  ],
  // Scene 2: Public Recruiting Projects API
  [
    { text: '> import { listPublicRecruitingProjects } from "@/projects/api"', type: 'cmd' },
    { text: '> await listPublicRecruitingProjects(0, 6)', type: 'cmd' },
    { text: 'GET /projects/public/recruiting?page=0&size=6', type: 'info' },
    { text: 'type: Promise<RecruitingPage>', type: 'item' },
    { text: 'api contract verified', type: 'success' },
    { text: 'ready_', type: 'ready' },
  ],
  // Scene 3: Public Events API
  [
    { text: '> import { listPublicEvents } from "@/events/api"', type: 'cmd' },
    { text: '> await listPublicEvents()', type: 'cmd' },
    { text: 'GET /events/public', type: 'info' },
    { text: 'type: Promise<LabEvent[]>', type: 'item' },
    { text: 'api contract verified', type: 'success' },
    { text: 'ready_', type: 'ready' },
  ],
]

const STATIC_LINES: TerminalLine[] = [
  { text: '> await getResearchFields()', type: 'cmd' },
  { text: 'GET /research-fields', type: 'info' },
  { text: 'type: Promise<ResearchField[]>', type: 'item' },
  { text: 'api contract verified', type: 'success' },
  { text: 'ready_', type: 'ready' },
]

export function SmartLabHero() {
  const [reducedMotion, setReducedMotion] = useState(false)
  const [sceneIndex, setSceneIndex] = useState(0)
  const [visibleLinesCount, setVisibleLinesCount] = useState(0)

  // Listen to prefers-reduced-motion
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    setReducedMotion(mq.matches)

    const handler = (e: MediaQueryListEvent) => setReducedMotion(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  // Terminal line-by-line reveal loop
  useEffect(() => {
    if (reducedMotion) return

    const currentScene = TERMINAL_SCENES[sceneIndex]
    if (visibleLinesCount < currentScene.length) {
      const lineDelay = visibleLinesCount === 0 ? 380 : 280
      const timer = setTimeout(() => {
        setVisibleLinesCount((prev) => prev + 1)
      }, lineDelay)
      return () => clearTimeout(timer)
    }

    // Finished scene, pause then cycle to next scene
    const pauseTimer = setTimeout(() => {
      setVisibleLinesCount(0)
      setSceneIndex((prev) => (prev + 1) % TERMINAL_SCENES.length)
    }, 3600)

    return () => clearTimeout(pauseTimer)
  }, [sceneIndex, visibleLinesCount, reducedMotion])

  const activeLines = reducedMotion
    ? STATIC_LINES
    : TERMINAL_SCENES[sceneIndex].slice(0, visibleLinesCount)

  return (
    <section className="landing-hero" aria-label="Giới thiệu Smart Lab">
      {/* Decorative subtle graph paper grid */}
      <div className="landing-hero-grid" aria-hidden="true" />

      <div className="landing-hero-in">
        {/* Left Column — Editorial Copy & CTAs */}
        <div className="landing-hero-copy">
          <h1 className="landing-hero-title">
            <span className="landing-hero-brand">SMART LAB</span>
            <span className="landing-hero-headline">
              <span className="landing-hero-line">Nghiên cứu thật.</span>
              <span className="landing-hero-line">Dự án thật.</span>
              <span className="landing-hero-line">Sản phẩm thật.</span>
            </span>
          </h1>

          <p className="landing-hero-lead">
            Phòng nghiên cứu về AI, Robotics và Software Engineering,
            nơi sinh viên cùng mentor xây dựng các dự án và sản phẩm thực tế.
          </p>

          <div className="landing-hero-cta">
            <Link className="btn primary lg landing-hero-btn-primary" to="/linh-vuc">
              Khám phá lĩnh vực <ArrowRight size={17} />
            </Link>
            <Link className="btn outline-light lg landing-hero-btn-secondary" to="/du-an?status=RECRUITING">
              Xem dự án đang tuyển
            </Link>
          </div>
        </div>

        {/* Right Column — SmartLab Retro-modern Research Workstation */}
        <div className="landing-hero-visual" aria-hidden="true">
          {/* Ground ambient contact shadow */}
          <div className="sl-ws-ground-shadow" />

          {/* Workstation Unit */}
          <div className="sl-ws-unit">
            {/* Main Computer Chassis */}
            <div className="sl-ws-chassis">
              {/* Top Chamfer Edge with highlights */}
              <div className="sl-ws-chassis-top" />

              {/* Front Face */}
              <div className="sl-ws-chassis-front">
                {/* Upper Section: CRT Monitor Assembly */}
                <div className="sl-ws-crt-housing">
                  <div className="sl-ws-crt-bezel">
                    {/* Screen Glass */}
                    <div className="sl-ws-crt-screen">
                      <div className="sl-ws-crt-scanlines" />
                      <div className="sl-ws-crt-glare" />

                      {/* Screen Content / Terminal */}
                      <div className="sl-ws-terminal">
                        <div className="sl-ws-term-header">
                          <div className="sl-ws-term-badge">
                            <span className="sl-ws-term-dot" />
                            <span>SMARTLAB TERMINAL</span>
                          </div>
                          <div className="sl-ws-term-channel">API WORKFLOW</div>
                        </div>

                        <div className="sl-ws-term-body">
                          {activeLines.map((line, idx) => (
                            <div
                              key={`${sceneIndex}-${idx}`}
                              className={`sl-term-line sl-term-${line.type}`}
                            >
                              {line.text}
                            </div>
                          ))}
                          {!reducedMotion && (
                            <span className="sl-term-cursor" />
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Lower Section: Hardware Panel */}
                <div className="sl-ws-panel">
                  {/* Left: Floppy / Data Drive */}
                  <div className="sl-ws-drive-group">
                    <div className="sl-ws-drive-slot">
                      <div className="sl-ws-drive-slit" />
                      <div className="sl-ws-drive-latch" />
                      <div className="sl-ws-drive-eject" />
                    </div>
                    <div className="sl-ws-drive-label">5.25&quot; HIGH-DENSITY DRIVE</div>
                  </div>

                  {/* Center: Recessed Brushed Metallic Badge */}
                  <div className="sl-ws-badge-group">
                    <div className="sl-ws-badge-plate">
                      <div className="sl-ws-badge-brand">SMART LAB</div>
                      <div className="sl-ws-badge-sub">RESEARCH WORKSTATION // SL-WS01</div>
                    </div>
                  </div>

                  {/* Right: Power & Ventilation */}
                  <div className="sl-ws-controls-group">
                    <div className="sl-ws-vents">
                      <span /><span /><span /><span />
                      <span /><span /><span /><span />
                    </div>
                    <div className="sl-ws-power-wrap">
                      <div className="sl-ws-power-led" />
                      <div className="sl-ws-power-switch">
                        <div className="sl-ws-switch-notch" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Side 3D Perspective Facet */}
              <div className="sl-ws-chassis-side">
                <div className="sl-ws-side-vents">
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                  <div className="sl-side-slit" />
                </div>
              </div>
            </div>

            {/* Retro Mechanical Keyboard with 3D perspective staging */}
            <div className="sl-ws-keyboard">
              <div className="sl-kb-chassis">
                <div className="sl-kb-top-rim" />
                <div className="sl-kb-well">
                  {/* Row 1: Function / Numbers */}
                  <div className="sl-kb-row">
                    <span className="sl-key esc">ESC</span>
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key bsp">DEL</span>
                  </div>
                  {/* Row 2: QWERTY */}
                  <div className="sl-kb-row">
                    <span className="sl-key tab">TAB</span>
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key slash">\</span>
                  </div>
                  {/* Row 3: Home row */}
                  <div className="sl-kb-row">
                    <span className="sl-key caps">CTRL</span>
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key" />
                    <span className="sl-key accent enter">RETURN</span>
                  </div>
                  {/* Row 4: Spacebar & Modifiers */}
                  <div className="sl-kb-row">
                    <span className="sl-key shift">SHIFT</span>
                    <span className="sl-key opt">ALT</span>
                    <span className="sl-key cmd">CMD</span>
                    <span className="sl-key space" />
                    <span className="sl-key cmd">CMD</span>
                    <span className="sl-key opt">ALT</span>
                    <span className="sl-key arrow left">◀</span>
                    <span className="sl-key arrow right">▶</span>
                  </div>
                </div>
                <div className="sl-kb-front-lip" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
