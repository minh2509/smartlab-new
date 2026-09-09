export type OSMode =
  | 'TERMINAL'
  | 'MENU'
  | 'DIAGNOSTICS'
  | 'ARCADE'
  | 'GAME_RUNNER'
  | 'GAME_SNAKE'
  | 'GAME_PONG'
  | 'GAME_BREAKOUT'
  | 'ACHIEVEMENTS'
  | 'BOOT'
  | 'OFF'

export type GameType = 'RUNNER' | 'SNAKE' | 'PONG' | 'BREAKOUT'

export interface HighScores {
  runner: number
  snake: number
  pong: number
  breakout: number
}

export interface Achievement {
  id: string
  title: string
  desc: string
  icon: string
  unlockedAt?: string
}

export interface WorkstationSettings {
  soundEnabled: boolean
  crtCurvature: boolean
}

export interface WorkstationStorageData {
  highScores: HighScores
  unlockedAchievementIds: string[]
  settings: WorkstationSettings
}

export interface HardwareActionTrigger {
  key?: string
  action?: 'esc' | 'enter' | 'space' | 'left' | 'right' | 'up' | 'down' | 'power' | 'floppy'
}
