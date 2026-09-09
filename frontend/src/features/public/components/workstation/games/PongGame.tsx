import { useEffect, useRef, useState } from 'react'
import { soundSynth } from '../audio'
import { recordHighScore, unlockAchievement } from '../storage'

interface PongGameProps {
  onExit: () => void
}

const W = 420
const H = 200
const PADDLE_H = 42
const PADDLE_W = 8
const BALL_SIZE = 7
const WIN_SCORE = 5

export function PongGame({ onExit }: PongGameProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [playerScore, setPlayerScore] = useState(0)
  const [aiScore, setAiScore] = useState(0)
  const [winner, setWinner] = useState<'PLAYER' | 'AI' | null>(null)
  const [isPaused, setIsPaused] = useState(false)

  const stateRef = useRef({
    pY: H / 2 - PADDLE_H / 2,
    pVy: 0,
    aiY: H / 2 - PADDLE_H / 2,
    bX: W / 2,
    bY: H / 2,
    bVx: 2.6,
    bVy: 1.4,
    trail: [] as { x: number; y: number }[],
    pScore: 0,
    aiScore: 0,
    winner: null as 'PLAYER' | 'AI' | null,
    isPaused: false,
  })

  const resetBall = (towardsAi = true) => {
    const s = stateRef.current
    s.bX = W / 2
    s.bY = H / 2
    s.bVx = towardsAi ? 2.6 : -2.6
    s.bVy = (Math.random() - 0.5) * 2.4
    s.trail = []
  }

  const restartGame = () => {
    stateRef.current = {
      pY: H / 2 - PADDLE_H / 2,
      pVy: 0,
      aiY: H / 2 - PADDLE_H / 2,
      bX: W / 2,
      bY: H / 2,
      bVx: 2.6,
      bVy: 1.4,
      trail: [],
      pScore: 0,
      aiScore: 0,
      winner: null,
      isPaused: false,
    }
    setPlayerScore(0)
    setAiScore(0)
    setWinner(null)
    setIsPaused(false)
    soundSynth.playSelect()
  }

  const movePlayer = (delta: number) => {
    stateRef.current.pY = Math.max(0, Math.min(H - PADDLE_H, stateRef.current.pY + delta))
  }

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        soundSynth.playKey('esc')
        onExit()
        return
      }
      if (e.key === 'KeyP') {
        e.preventDefault()
        stateRef.current.isPaused = !stateRef.current.isPaused
        setIsPaused(stateRef.current.isPaused)
        return
      }

      if (e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        stateRef.current.pVy = -4.2
      } else if (e.key === 'ArrowDown' || e.key === 'KeyS') {
        e.preventDefault()
        stateRef.current.pVy = 4.2
      } else if (e.key === 'Enter' && stateRef.current.winner) {
        e.preventDefault()
        restartGame()
      }
    }

    const handleKeyUp = (e: KeyboardEvent) => {
      if (
        e.key === 'ArrowUp' ||
        e.key === 'KeyW' ||
        e.key === 'ArrowDown' ||
        e.key === 'KeyS'
      ) {
        stateRef.current.pVy = 0
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('keyup', handleKeyUp)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('keyup', handleKeyUp)
    }
  }, [onExit])

  // Game loop
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let animId: number

    const loop = () => {
      const s = stateRef.current

      if (!s.isPaused && !s.winner) {
        // Player paddle physics
        s.pY = Math.max(0, Math.min(H - PADDLE_H, s.pY + s.pVy))

        // AI paddle physics (gentle tracking speed)
        const targetAi = s.bY - PADDLE_H / 2
        const aiSpeed = 2.3
        if (s.aiY < targetAi - 4) {
          s.aiY += aiSpeed
        } else if (s.aiY > targetAi + 4) {
          s.aiY -= aiSpeed
        }
        s.aiY = Math.max(0, Math.min(H - PADDLE_H, s.aiY))

        // Ball movement
        s.bX += s.bVx
        s.bY += s.bVy

        // Store trail
        s.trail.push({ x: s.bX, y: s.bY })
        if (s.trail.length > 6) s.trail.shift()

        // Top & bottom wall bounce
        if (s.bY <= 0 || s.bY >= H - BALL_SIZE) {
          s.bVy = -s.bVy
          soundSynth.playBounce(320)
        }

        // Left paddle (Player) collision
        const playerX = 18
        if (
          s.bX <= playerX + PADDLE_W &&
          s.bX >= playerX - 4 &&
          s.bY + BALL_SIZE >= s.pY &&
          s.bY <= s.pY + PADDLE_H &&
          s.bVx < 0
        ) {
          s.bVx = Math.min(3.8, Math.abs(s.bVx) * 1.02)
          const hitOffset = (s.bY + BALL_SIZE / 2 - (s.pY + PADDLE_H / 2)) / (PADDLE_H / 2)
          s.bVy = hitOffset * 3.0
          soundSynth.playBounce(540)
        }

        // Right paddle (AI) collision
        const aiX = W - 18 - PADDLE_W
        if (
          s.bX + BALL_SIZE >= aiX &&
          s.bX <= aiX + PADDLE_W + 4 &&
          s.bY + BALL_SIZE >= s.aiY &&
          s.bY <= s.aiY + PADDLE_H &&
          s.bVx > 0
        ) {
          s.bVx = -Math.min(3.8, Math.abs(s.bVx) * 1.02)
          const hitOffset = (s.bY + BALL_SIZE / 2 - (s.aiY + PADDLE_H / 2)) / (PADDLE_H / 2)
          s.bVy = hitOffset * 3.0
          soundSynth.playBounce(480)
        }

        // Score check
        if (s.bX < 0) {
          // AI scored
          s.aiScore += 1
          setAiScore(s.aiScore)
          soundSynth.playHit()
          if (s.aiScore >= WIN_SCORE) {
            s.winner = 'AI'
            setWinner('AI')
            soundSynth.playGameOver()
          } else {
            resetBall(true)
          }
        } else if (s.bX > W) {
          // Player scored
          s.pScore += 1
          setPlayerScore(s.pScore)
          soundSynth.playCollect()
          if (s.pScore >= WIN_SCORE) {
            s.winner = 'PLAYER'
            setWinner('PLAYER')
            soundSynth.playSelect()
            unlockAchievement('PONG_DEFEAT_AI')
            recordHighScore('pong', s.pScore)
          } else {
            resetBall(false)
          }
        }
      }

      // ── RENDERING ──────────────────────────────────────────
      ctx.fillStyle = '#060B14'
      ctx.fillRect(0, 0, W, H)

      // Center court dashed net line
      ctx.strokeStyle = 'rgba(56, 189, 248, 0.2)'
      ctx.lineWidth = 2
      ctx.setLineDash([4, 4])
      ctx.beginPath()
      ctx.moveTo(W / 2, 0)
      ctx.lineTo(W / 2, H)
      ctx.stroke()
      ctx.setLineDash([])

      // Phosphor ball trail
      s.trail.forEach((pt, idx) => {
        const alpha = (idx + 1) / (s.trail.length * 2.5)
        ctx.fillStyle = `rgba(56, 189, 248, ${alpha})`
        ctx.fillRect(pt.x, pt.y, BALL_SIZE, BALL_SIZE)
      })

      // Main Ball
      ctx.fillStyle = '#38BDF8'
      ctx.fillRect(s.bX, s.bY, BALL_SIZE, BALL_SIZE)

      // Player Paddle (Left — Orange / Cream)
      ctx.fillStyle = '#F97316'
      ctx.fillRect(18, s.pY, PADDLE_W, PADDLE_H)

      // AI Paddle (Right — Cyan / Blue)
      ctx.fillStyle = '#38BDF8'
      ctx.fillRect(W - 18 - PADDLE_W, s.aiY, PADDLE_W, PADDLE_H)

      // Score Header
      ctx.font = '900 16px ui-monospace, monospace'
      ctx.fillStyle = '#F97316'
      ctx.fillText(`${s.pScore}`, W / 2 - 38, 26)
      ctx.fillStyle = '#38BDF8'
      ctx.fillText(`${s.aiScore}`, W / 2 + 26, 26)

      // Winner Banner
      if (s.winner) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.88)'
        ctx.fillRect(0, 0, W, H)

        ctx.textAlign = 'center'
        if (s.winner === 'PLAYER') {
          ctx.fillStyle = '#10B981'
          ctx.font = '900 16px ui-monospace, monospace'
          ctx.fillText('VICTORY // SMARTLAB AI DEFEATED!', W / 2, H / 2 - 16)
        } else {
          ctx.fillStyle = '#EF4444'
          ctx.font = '900 16px ui-monospace, monospace'
          ctx.fillText('DEFEAT // SMARTLAB AI WINS', W / 2, H / 2 - 16)
        }

        ctx.fillStyle = '#F8FAFC'
        ctx.font = '700 12px ui-monospace, monospace'
        ctx.fillText(`FINAL SCORE: ${s.pScore} - ${s.aiScore}`, W / 2, H / 2 + 8)

        ctx.fillStyle = '#38BDF8'
        ctx.font = '600 10px ui-monospace, monospace'
        ctx.fillText('PRESS [ENTER] TO REMATCH', W / 2, H / 2 + 30)
        ctx.fillText('PRESS [ESC] FOR ARCADE MENU', W / 2, H / 2 + 48)
        ctx.textAlign = 'start'
      }

      // Paused Banner
      if (s.isPaused && !s.winner) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.75)'
        ctx.fillRect(0, 0, W, H)
        ctx.fillStyle = '#FBBF24'
        ctx.font = '900 14px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('[PAUSED — PRESS P TO RESUME]', W / 2, H / 2)
        ctx.textAlign = 'start'
      }

      animId = requestAnimationFrame(loop)
    }

    animId = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(animId)
  }, [])

  return (
    <div className="sl-ws-game-container">
      <div className="sl-ws-term-header">
        <div className="sl-ws-term-badge">
          <span className="sl-ws-term-dot sl-dot-cyan" />
          <span>QUANTUM PONG // PLAYER {playerScore} : {aiScore} AI</span>
        </div>
        <div className="sl-ws-game-header-actions">
          <button
            type="button"
            className="sl-ws-header-btn"
            onClick={() => {
              stateRef.current.isPaused = !stateRef.current.isPaused
              setIsPaused(stateRef.current.isPaused)
            }}
          >
            {isPaused ? 'RESUME [P]' : 'PAUSE [P]'}
          </button>
          <button
            type="button"
            className="sl-ws-header-btn"
            onClick={onExit}
          >
            EXIT [ESC]
          </button>
        </div>
      </div>

      <div
        className="sl-game-canvas-wrap"
        onMouseMove={(e) => {
          const rect = e.currentTarget.getBoundingClientRect()
          const mouseY = e.clientY - rect.top
          const scaleY = H / rect.height
          stateRef.current.pY = Math.max(0, Math.min(H - PADDLE_H, mouseY * scaleY - PADDLE_H / 2))
        }}
      >
        <canvas
          ref={canvasRef}
          width={W}
          height={H}
          className="sl-game-canvas"
        />
      </div>

      {/* On-screen touch buttons */}
      <div className="sl-game-touch-bar">
        {winner ? (
          <button
            type="button"
            className="sl-touch-btn sl-touch-jump"
            onClick={restartGame}
          >
            REMATCH [ENTER]
          </button>
        ) : (
          <>
            <button
              type="button"
              className="sl-touch-btn"
              onClick={() => movePlayer(-24)}
            >
              ▲ UP [W]
            </button>
            <button
              type="button"
              className="sl-touch-btn"
              onClick={() => movePlayer(24)}
            >
              ▼ DOWN [S]
            </button>
          </>
        )}
        <button
          type="button"
          className="sl-touch-btn sl-touch-exit"
          onClick={onExit}
        >
          BACK [ESC]
        </button>
      </div>
    </div>
  )
}
