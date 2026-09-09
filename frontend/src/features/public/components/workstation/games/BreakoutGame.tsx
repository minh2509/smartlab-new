import { useEffect, useRef, useState } from 'react'
import { soundSynth } from '../audio'
import { recordHighScore, unlockAchievement } from '../storage'

interface BreakoutGameProps {
  onExit: () => void
}

const W = 420
const H = 200
const PADDLE_W = 68
const PADDLE_H = 8
const BALL_R = 4
const BRICK_ROWS = 4
const BRICK_COLS = 10
const BRICK_W = 36
const BRICK_H = 11
const BRICK_GAP = 4
const BRICK_OFFSET_X = 14
const BRICK_OFFSET_Y = 24

interface Brick {
  x: number
  y: number
  alive: boolean
  color: string
  points: number
}

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  alpha: number
  color: string
}

export function BreakoutGame({ onExit }: BreakoutGameProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const [score, setScore] = useState(0)
  const [isGameOver, setIsGameOver] = useState(false)
  const [isVictory, setIsVictory] = useState(false)
  const [isPaused, setIsPaused] = useState(false)

  const stateRef = useRef({
    pX: W / 2 - PADDLE_W / 2,
    pVx: 0,
    bX: W / 2,
    bY: H - 30,
    bVx: 2.0,
    bVy: -2.2,
    ballAttached: true,
    bricks: [] as Brick[],
    particles: [] as Particle[],
    score: 0,
    brokenCount: 0,
    isGameOver: false,
    isVictory: false,
    isPaused: false,
  })

  const initBricks = (): Brick[] => {
    const list: Brick[] = []
    const colors = ['#EF4444', '#F59E0B', '#10B981', '#38BDF8']
    const pts = [40, 30, 20, 10]

    for (let r = 0; r < BRICK_ROWS; r++) {
      for (let c = 0; c < BRICK_COLS; c++) {
        list.push({
          x: BRICK_OFFSET_X + c * (BRICK_W + BRICK_GAP),
          y: BRICK_OFFSET_Y + r * (BRICK_H + BRICK_GAP),
          alive: true,
          color: colors[r],
          points: pts[r],
        })
      }
    }
    return list
  }

  const restartGame = () => {
    stateRef.current = {
      pX: W / 2 - PADDLE_W / 2,
      pVx: 0,
      bX: W / 2,
      bY: H - 28,
      bVx: 2.0,
      bVy: -2.2,
      ballAttached: true,
      bricks: initBricks(),
      particles: [],
      score: 0,
      brokenCount: 0,
      isGameOver: false,
      isVictory: false,
      isPaused: false,
    }
    setScore(0)
    setIsGameOver(false)
    setIsVictory(false)
    setIsPaused(false)
    soundSynth.playSelect()
  }

  const launchBall = () => {
    const s = stateRef.current
    if (s.ballAttached) {
      s.ballAttached = false
      s.bVx = (Math.random() - 0.5) * 2.2 || 1.6
      s.bVy = -2.4
      soundSynth.playBounce(600)
    }
  }

  const movePaddle = (dx: number) => {
    const s = stateRef.current
    s.pX = Math.max(0, Math.min(W - PADDLE_W, s.pX + dx))
    if (s.ballAttached) {
      s.bX = s.pX + PADDLE_W / 2
    }
  }

  // Keyboard navigation
  useEffect(() => {
    restartGame()

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

      if (e.key === 'ArrowLeft' || e.key === 'KeyA') {
        e.preventDefault()
        stateRef.current.pVx = -4.8
      } else if (e.key === 'ArrowRight' || e.key === 'KeyD') {
        e.preventDefault()
        stateRef.current.pVx = 4.8
      } else if (e.key === ' ' || e.key === 'ArrowUp' || e.key === 'KeyW') {
        e.preventDefault()
        launchBall()
      } else if (e.key === 'Enter' && (stateRef.current.isGameOver || stateRef.current.isVictory)) {
        e.preventDefault()
        restartGame()
      }
    }

    const handleKeyUp = (e: KeyboardEvent) => {
      if (
        e.key === 'ArrowLeft' ||
        e.key === 'KeyA' ||
        e.key === 'ArrowRight' ||
        e.key === 'KeyD'
      ) {
        stateRef.current.pVx = 0
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

      if (!s.isPaused && !s.isGameOver && !s.isVictory) {
        // Paddle movement
        s.pX = Math.max(0, Math.min(W - PADDLE_W, s.pX + s.pVx))

        if (s.ballAttached) {
          s.bX = s.pX + PADDLE_W / 2
          s.bY = H - 22
        } else {
          // Ball movement
          s.bX += s.bVx
          s.bY += s.bVy

          // Wall bounce (Left & Right)
          if (s.bX - BALL_R <= 0) {
            s.bX = BALL_R
            s.bVx = -s.bVx
            soundSynth.playBounce(380)
          } else if (s.bX + BALL_R >= W) {
            s.bX = W - BALL_R
            s.bVx = -s.bVx
            soundSynth.playBounce(380)
          }

          // Ceiling bounce
          if (s.bY - BALL_R <= 0) {
            s.bY = BALL_R
            s.bVy = -s.bVy
            soundSynth.playBounce(420)
          }

          // Bottom loss
          if (s.bY - BALL_R > H) {
            s.isGameOver = true
            setIsGameOver(true)
            soundSynth.playHit()
            soundSynth.playGameOver()
            recordHighScore('breakout', s.score)
          }

          // Paddle collision
          const paddleY = H - 18
          if (
            s.bY + BALL_R >= paddleY &&
            s.bY - BALL_R <= paddleY + PADDLE_H &&
            s.bX >= s.pX - 2 &&
            s.bX <= s.pX + PADDLE_W + 2 &&
            s.bVy > 0
          ) {
            s.bVy = -Math.abs(s.bVy)
            const hitRatio = (s.bX - (s.pX + PADDLE_W / 2)) / (PADDLE_W / 2)
            s.bVx = hitRatio * 2.8
            soundSynth.playBounce(520)
          }

          // Brick collisions
          let activeLeft = 0
          for (const brick of s.bricks) {
            if (!brick.alive) continue
            activeLeft++

            if (
              s.bX + BALL_R >= brick.x &&
              s.bX - BALL_R <= brick.x + BRICK_W &&
              s.bY + BALL_R >= brick.y &&
              s.bY - BALL_R <= brick.y + BRICK_H
            ) {
              brick.alive = false
              s.bVy = -s.bVy
              s.score += brick.points
              s.brokenCount += 1
              setScore(s.score)
              soundSynth.playCollect()

              if (s.brokenCount >= 10) {
                unlockAchievement('BREAKOUT_LAYER')
              }

              // Spawn particles
              for (let i = 0; i < 6; i++) {
                s.particles.push({
                  x: brick.x + BRICK_W / 2,
                  y: brick.y + BRICK_H / 2,
                  vx: (Math.random() - 0.5) * 4,
                  vy: (Math.random() - 0.5) * 4,
                  alpha: 1,
                  color: brick.color,
                })
              }
              break
            }
          }

          if (activeLeft === 0) {
            s.isVictory = true
            setIsVictory(true)
            soundSynth.playSelect()
            recordHighScore('breakout', s.score)
          }
        }

        // Update particles
        for (let i = s.particles.length - 1; i >= 0; i--) {
          const p = s.particles[i]
          p.x += p.vx
          p.y += p.vy
          p.alpha -= 0.05
          if (p.alpha <= 0) {
            s.particles.splice(i, 1)
          }
        }
      }

      // ── RENDERING ──────────────────────────────────────────
      ctx.fillStyle = '#090E17'
      ctx.fillRect(0, 0, W, H)

      // Firewall grid pattern
      ctx.strokeStyle = 'rgba(239, 68, 68, 0.05)'
      ctx.lineWidth = 1
      for (let y = 0; y < H; y += 16) {
        ctx.beginPath()
        ctx.moveTo(0, y)
        ctx.lineTo(W, y)
        ctx.stroke()
      }

      // Bricks
      for (const brick of s.bricks) {
        if (!brick.alive) continue
        ctx.fillStyle = brick.color
        ctx.fillRect(brick.x, brick.y, BRICK_W, BRICK_H)
        // Highlight top border
        ctx.fillStyle = 'rgba(255, 255, 255, 0.4)'
        ctx.fillRect(brick.x, brick.y, BRICK_W, 2)
      }

      // Particles
      for (const p of s.particles) {
        ctx.fillStyle = p.color
        ctx.globalAlpha = Math.max(0, p.alpha)
        ctx.fillRect(p.x, p.y, 3, 3)
      }
      ctx.globalAlpha = 1

      // Paddle
      const paddleY = H - 18
      ctx.fillStyle = '#F97316'
      ctx.fillRect(s.pX, paddleY, PADDLE_W, PADDLE_H)
      ctx.fillStyle = '#FED7AA'
      ctx.fillRect(s.pX + 2, paddleY + 1, PADDLE_W - 4, 2)

      // Ball
      ctx.fillStyle = '#38BDF8'
      ctx.beginPath()
      ctx.arc(s.bX, s.bY, BALL_R, 0, Math.PI * 2)
      ctx.fill()

      // Game Over Screen
      if (s.isGameOver) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.88)'
        ctx.fillRect(0, 0, W, H)

        ctx.fillStyle = '#EF4444'
        ctx.font = '900 16px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('FIREWALL BREACH FAILED // PROBE LOST', W / 2, H / 2 - 16)

        ctx.fillStyle = '#F8FAFC'
        ctx.font = '700 12px ui-monospace, monospace'
        ctx.fillText(`FINAL SCORE: ${s.score} PTS`, W / 2, H / 2 + 6)

        ctx.fillStyle = '#38BDF8'
        ctx.font = '600 10px ui-monospace, monospace'
        ctx.fillText('PRESS [ENTER] TO RETRY', W / 2, H / 2 + 28)
        ctx.fillText('PRESS [ESC] FOR ARCADE MENU', W / 2, H / 2 + 46)
        ctx.textAlign = 'start'
      }

      // Victory Screen
      if (s.isVictory) {
        ctx.fillStyle = 'rgba(11, 14, 20, 0.88)'
        ctx.fillRect(0, 0, W, H)

        ctx.fillStyle = '#10B981'
        ctx.font = '900 16px ui-monospace, monospace'
        ctx.textAlign = 'center'
        ctx.fillText('ALL FIREWALL LAYERS BREACHED!', W / 2, H / 2 - 16)

        ctx.fillStyle = '#F8FAFC'
        ctx.font = '700 12px ui-monospace, monospace'
        ctx.fillText(`VICTORY SCORE: ${s.score} PTS`, W / 2, H / 2 + 6)

        ctx.fillStyle = '#38BDF8'
        ctx.font = '600 10px ui-monospace, monospace'
        ctx.fillText('PRESS [ENTER] TO REPLAY', W / 2, H / 2 + 28)
        ctx.fillText('PRESS [ESC] FOR ARCADE MENU', W / 2, H / 2 + 46)
        ctx.textAlign = 'start'
      }

      // Paused Banner
      if (s.isPaused && !s.isGameOver && !s.isVictory) {
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
          <span className="sl-ws-term-dot sl-dot-orange" />
          <span>FIREWALL BREAKER // SCORE: {score}</span>
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
        onClick={launchBall}
        onMouseMove={(e) => {
          const rect = e.currentTarget.getBoundingClientRect()
          const mouseX = e.clientX - rect.left
          const scaleX = W / rect.width
          stateRef.current.pX = Math.max(0, Math.min(W - PADDLE_W, mouseX * scaleX - PADDLE_W / 2))
          if (stateRef.current.ballAttached) {
            stateRef.current.bX = stateRef.current.pX + PADDLE_W / 2
          }
        }}
      >
        <canvas
          ref={canvasRef}
          width={W}
          height={H}
          className="sl-game-canvas"
        />
      </div>

      {/* Touch buttons */}
      <div className="sl-game-touch-bar">
        {isGameOver || isVictory ? (
          <button
            type="button"
            className="sl-touch-btn sl-touch-jump"
            onClick={restartGame}
          >
            REPLAY [ENTER]
          </button>
        ) : (
          <>
            <button
              type="button"
              className="sl-touch-btn"
              onClick={() => movePaddle(-22)}
            >
              ◀ LEFT [A]
            </button>
            <button
              type="button"
              className="sl-touch-btn sl-touch-jump"
              onClick={launchBall}
            >
              LAUNCH [SPACE]
            </button>
            <button
              type="button"
              className="sl-touch-btn"
              onClick={() => movePaddle(22)}
            >
              RIGHT [D] ▶
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
