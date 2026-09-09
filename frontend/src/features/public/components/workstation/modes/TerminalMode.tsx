import { useEffect, useState } from 'react'

export interface TerminalLine {
  text: string
  type: 'cmd' | 'info' | 'item' | 'success' | 'ready' | 'hint'
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

interface TerminalModeProps {
  reducedMotion: boolean
  onOpenMenu: () => void
}

export function TerminalMode({ reducedMotion, onOpenMenu }: TerminalModeProps) {
  const [sceneIndex, setSceneIndex] = useState(0)
  const [visibleLinesCount, setVisibleLinesCount] = useState(0)

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
    <div className="sl-ws-terminal">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot" />
          <span>SMARTLAB TERMINAL</span>
        </div>
        <div className="sl-ws-term-right-tools">
          <button
            type="button"
            className="sl-ws-header-btn"
            onClick={onOpenMenu}
            title="Open Workstation System Menu"
          >
            MENU [ESC]
          </button>
          <div className="sl-ws-term-channel">API WORKFLOW</div>
        </div>
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

      <div className="sl-ws-term-footer">
        <span className="sl-term-footer-prompt">&gt;</span>
        <span className="sl-term-footer-text">
          TYPE <b>[ESC]</b> OR <b>[RETURN]</b> FOR SYSTEM OS / MINI-GAMES
        </span>
      </div>
    </div>
  )
}
