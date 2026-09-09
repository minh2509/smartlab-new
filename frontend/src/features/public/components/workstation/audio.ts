// Zero-dependency retro Web Audio synthesizer for SMARTLAB WORKSTATION OS
// Strictly muted by default! No autoplay sound.

class SoundSynth {
  private ctx: AudioContext | null = null
  private enabled = false

  public isEnabled(): boolean {
    return this.enabled
  }

  public setEnabled(enabled: boolean): void {
    this.enabled = enabled
    if (enabled && !this.ctx) {
      this.initContext()
    }
  }

  private initContext(): void {
    try {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
      if (AudioCtx) {
        this.ctx = new AudioCtx()
      }
    } catch {
      this.ctx = null
    }
  }

  private getActiveCtx(): AudioContext | null {
    if (!this.enabled) return null
    if (!this.ctx) this.initContext()
    if (this.ctx && this.ctx.state === 'suspended') {
      void this.ctx.resume()
    }
    return this.ctx
  }

  // Keypress blip
  public playKey(kind: 'normal' | 'enter' | 'esc' = 'normal'): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    const now = ctx.currentTime

    const freq = kind === 'enter' ? 880 : kind === 'esc' ? 440 : 660
    osc.type = 'square'
    osc.frequency.setValueAtTime(freq, now)
    osc.frequency.exponentialRampToValueAtTime(kind === 'enter' ? 1200 : 200, now + 0.04)

    gain.gain.setValueAtTime(0.04, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.04)
  }

  // Menu Select chime
  public playSelect(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'triangle'
    osc.frequency.setValueAtTime(523.25, now) // C5
    osc.frequency.setValueAtTime(659.25, now + 0.04) // E5
    osc.frequency.setValueAtTime(783.99, now + 0.08) // G5

    gain.gain.setValueAtTime(0.05, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.14)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.14)
  }

  // BIOS POST Boot Beep
  public playBootBeep(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'square'
    osc.frequency.setValueAtTime(880, now)

    gain.gain.setValueAtTime(0.08, now)
    gain.gain.setValueAtTime(0.08, now + 0.12)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.15)
  }

  // Floppy disk drive seek / read chatter
  public playFloppySeek(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    for (let i = 0; i < 4; i++) {
      const stepTime = now + i * 0.05
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()

      osc.type = 'sawtooth'
      osc.frequency.setValueAtTime(140 + i * 40, stepTime)

      gain.gain.setValueAtTime(0.03, stepTime)
      gain.gain.exponentialRampToValueAtTime(0.001, stepTime + 0.025)

      osc.connect(gain)
      gain.connect(ctx.destination)

      osc.start(stepTime)
      osc.stop(stepTime + 0.025)
    }
  }

  // Jump sound for runner
  public playJump(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'square'
    osc.frequency.setValueAtTime(180, now)
    osc.frequency.exponentialRampToValueAtTime(700, now + 0.12)

    gain.gain.setValueAtTime(0.06, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.12)
  }

  // Collect item sound
  public playCollect(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sine'
    osc.frequency.setValueAtTime(987.77, now) // B5
    osc.frequency.setValueAtTime(1318.51, now + 0.06) // E6

    gain.gain.setValueAtTime(0.06, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.15)
  }

  // Pong / Breakout bounce
  public playBounce(pitch = 440): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'square'
    osc.frequency.setValueAtTime(pitch, now)
    osc.frequency.exponentialRampToValueAtTime(pitch * 0.75, now + 0.05)

    gain.gain.setValueAtTime(0.05, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.05)
  }

  // Hit / Obstacle collision
  public playHit(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sawtooth'
    osc.frequency.setValueAtTime(160, now)
    osc.frequency.exponentialRampToValueAtTime(40, now + 0.16)

    gain.gain.setValueAtTime(0.08, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.16)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.16)
  }

  // Game over buzzer
  public playGameOver(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'sawtooth'
    osc.frequency.setValueAtTime(320, now)
    osc.frequency.setValueAtTime(260, now + 0.1)
    osc.frequency.setValueAtTime(200, now + 0.2)
    osc.frequency.setValueAtTime(140, now + 0.3)

    gain.gain.setValueAtTime(0.07, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.45)
  }

  // Power switch toggle click
  public playPowerClick(): void {
    const ctx = this.getActiveCtx()
    if (!ctx) return

    const now = ctx.currentTime
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'triangle'
    osc.frequency.setValueAtTime(120, now)
    osc.frequency.exponentialRampToValueAtTime(30, now + 0.03)

    gain.gain.setValueAtTime(0.05, now)
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.03)

    osc.connect(gain)
    gain.connect(ctx.destination)

    osc.start(now)
    osc.stop(now + 0.03)
  }
}

export const soundSynth = new SoundSynth()
