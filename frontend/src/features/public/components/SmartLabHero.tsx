import { ArrowRight } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { soundSynth } from './workstation/audio'
import type { HardwareActionTrigger } from './workstation/types'
import { WorkstationOS } from './workstation/WorkstationOS'
import './workstation/workstation.css'

export function SmartLabHero({ onExploreFields }: { onExploreFields: () => void }) {
  const [reducedMotion, setReducedMotion] = useState(false)
  const [isPowered, setIsPowered] = useState(true)
  const [floppyBlinking, setFloppyBlinking] = useState(false)
  const [hardwareTrigger, setHardwareTrigger] = useState<HardwareActionTrigger | null>(null)
  const [pressedKeyName, setPressedKeyName] = useState<string | null>(null)

  // Listen to prefers-reduced-motion
  useEffect(() => {
    const mq = window.matchMedia('(prefers-reduced-motion: reduce)')
    setReducedMotion(mq.matches)

    const handler = (e: MediaQueryListEvent) => setReducedMotion(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const triggerAction = useCallback((action: HardwareActionTrigger['action'], keyId?: string) => {
    if (keyId) {
      setPressedKeyName(keyId)
      setTimeout(() => setPressedKeyName(null), 120)
    }
    setHardwareTrigger({ action })
  }, [])

  const handleGenericKeyClick = (keyName: string) => {
    soundSynth.playKey('normal')
    setPressedKeyName(keyName)
    setTimeout(() => setPressedKeyName(null), 120)
  }

  const togglePower = () => {
    soundSynth.playPowerClick()
    setIsPowered((prev) => !prev)
  }

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
            <button className="btn primary lg landing-hero-btn-primary" type="button" onClick={onExploreFields}>
              Khám phá lĩnh vực <ArrowRight size={17} />
            </button>
            <Link className="btn outline-light lg landing-hero-btn-secondary" to="/du-an?status=RECRUITING">
              Xem dự án đang tuyển
            </Link>
          </div>
        </div>

        {/* Right Column — SmartLab Retro-modern Research Workstation */}
        <div className="landing-hero-visual">
          {/* Ground ambient contact shadow */}
          <div className="sl-ws-ground-shadow" aria-hidden="true" />

          {/* Workstation Unit */}
          <div className="sl-ws-unit">
            {/* Main Computer Chassis */}
            <div className="sl-ws-chassis">
              {/* Top Chamfer Edge with highlights */}
              <div className="sl-ws-chassis-top" aria-hidden="true" />

              {/* Front Face */}
              <div className="sl-ws-chassis-front">
                {/* Upper Section: CRT Monitor Assembly */}
                <div className="sl-ws-crt-housing">
                  <div className="sl-ws-crt-bezel">
                    {/* Screen Glass & Workstation OS */}
                    <WorkstationOS
                      reducedMotion={reducedMotion}
                      isPowered={isPowered}
                      onPowerToggle={togglePower}
                      hardwareTrigger={hardwareTrigger}
                      onTriggerHandled={() => setHardwareTrigger(null)}
                      onFloppyActivity={(active) => setFloppyBlinking(active)}
                    />
                  </div>
                </div>

                {/* Lower Section: Hardware Panel */}
                <div className="sl-ws-panel">
                  {/* Left: Floppy / Data Drive */}
                  <div className="sl-ws-drive-group">
                    <button
                      type="button"
                      className={`sl-ws-drive-slot ${floppyBlinking ? 'is-active-drive' : ''}`}
                      onClick={() => triggerAction('floppy')}
                      title="5.25&quot; Floppy Drive — Click to load/eject Arcade Disk"
                      aria-label="5.25 inch Floppy Drive — Click to load Arcade Disk"
                    >
                      <div className="sl-ws-drive-slit" />
                      <div className="sl-ws-drive-latch" />
                      <div className="sl-ws-drive-eject" />
                    </button>
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
                    <div className="sl-ws-vents" aria-hidden="true">
                      <span /><span /><span /><span />
                      <span /><span /><span /><span />
                    </div>
                    <div className="sl-ws-power-wrap">
                      <div
                        className={`sl-ws-power-led ${!isPowered ? 'is-off' : ''}`}
                        title={isPowered ? 'Workstation Power LED: Active' : 'Workstation Power LED: Off'}
                      />
                      <button
                        type="button"
                        className={`sl-ws-power-switch ${!isPowered ? 'is-off' : ''}`}
                        onClick={togglePower}
                        role="switch"
                        aria-checked={isPowered}
                        aria-label="Workstation Main Power Switch"
                        title={isPowered ? 'Click to Power Down Workstation' : 'Click to Power Up Workstation'}
                      >
                        <div className="sl-ws-switch-notch" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Right Side 3D Perspective Facet */}
              <div className="sl-ws-chassis-side" aria-hidden="true">
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
            <div className="sl-ws-keyboard" aria-label="Interactive Retro Keyboard">
              <div className="sl-kb-chassis">
                <div className="sl-kb-top-rim" aria-hidden="true" />
                <div className="sl-kb-well">
                  {/* Row 1: Function / Numbers */}
                  <div className="sl-kb-row">
                    <button
                      type="button"
                      className={`sl-key esc ${pressedKeyName === 'esc' ? 'is-pressed' : ''}`}
                      onClick={() => triggerAction('esc', 'esc')}
                      title="ESC: Open System Menu / Back"
                      aria-label="Escape Key"
                    >
                      ESC
                    </button>
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k1')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k2')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k3')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k4')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k5')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k6')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k7')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k8')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k9')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k10')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k11')} />
                    <button
                      type="button"
                      className="sl-key bsp"
                      onClick={() => handleGenericKeyClick('del')}
                      aria-label="Delete Key"
                    >
                      DEL
                    </button>
                  </div>
                  {/* Row 2: QWERTY */}
                  <div className="sl-kb-row">
                    <button
                      type="button"
                      className="sl-key tab"
                      onClick={() => handleGenericKeyClick('tab')}
                      aria-label="Tab Key"
                    >
                      TAB
                    </button>
                    <span className="sl-key" onClick={() => handleGenericKeyClick('q')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('w')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('e')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('r')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('t')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('y')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('u')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('i')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('o')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('p')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('bracket')} />
                    <button
                      type="button"
                      className="sl-key slash"
                      onClick={() => handleGenericKeyClick('slash')}
                      aria-label="Backslash Key"
                    >
                      \
                    </button>
                  </div>
                  {/* Row 3: Home row */}
                  <div className="sl-kb-row">
                    <button
                      type="button"
                      className="sl-key caps"
                      onClick={() => handleGenericKeyClick('ctrl')}
                      aria-label="Control Key"
                    >
                      CTRL
                    </button>
                    <span className="sl-key" onClick={() => handleGenericKeyClick('a')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('s')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('d')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('f')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('g')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('h')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('j')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('k')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('l')} />
                    <span className="sl-key" onClick={() => handleGenericKeyClick('semi')} />
                    <button
                      type="button"
                      className={`sl-key accent enter ${pressedKeyName === 'enter' ? 'is-pressed' : ''}`}
                      onClick={() => triggerAction('enter', 'enter')}
                      title="RETURN: Select / Start"
                      aria-label="Return Key"
                    >
                      RETURN
                    </button>
                  </div>
                  {/* Row 4: Spacebar & Modifiers */}
                  <div className="sl-kb-row">
                    <button
                      type="button"
                      className="sl-key shift"
                      onClick={() => handleGenericKeyClick('shift')}
                      aria-label="Shift Key"
                    >
                      SHIFT
                    </button>
                    <button
                      type="button"
                      className="sl-key opt"
                      onClick={() => handleGenericKeyClick('alt')}
                      aria-label="Option Key"
                    >
                      ALT
                    </button>
                    <button
                      type="button"
                      className="sl-key cmd"
                      onClick={() => handleGenericKeyClick('cmd')}
                      aria-label="Command Key"
                    >
                      CMD
                    </button>
                    <button
                      type="button"
                      className={`sl-key space ${pressedKeyName === 'space' ? 'is-pressed' : ''}`}
                      onClick={() => triggerAction('space', 'space')}
                      title="SPACE: Jump / Action"
                      aria-label="Spacebar Key"
                    />
                    <button
                      type="button"
                      className="sl-key cmd"
                      onClick={() => handleGenericKeyClick('cmd2')}
                      aria-label="Command Key"
                    >
                      CMD
                    </button>
                    <button
                      type="button"
                      className="sl-key opt"
                      onClick={() => handleGenericKeyClick('alt2')}
                      aria-label="Option Key"
                    >
                      ALT
                    </button>
                    <button
                      type="button"
                      className={`sl-key arrow left ${pressedKeyName === 'left' ? 'is-pressed' : ''}`}
                      onClick={() => triggerAction('left', 'left')}
                      title="Arrow Left"
                      aria-label="Left Arrow Key"
                    >
                      ◀
                    </button>
                    <button
                      type="button"
                      className={`sl-key arrow right ${pressedKeyName === 'right' ? 'is-pressed' : ''}`}
                      onClick={() => triggerAction('right', 'right')}
                      title="Arrow Right"
                      aria-label="Right Arrow Key"
                    >
                      ▶
                    </button>
                  </div>
                </div>
                <div className="sl-kb-front-lip" aria-hidden="true" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
