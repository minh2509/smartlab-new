import type { ReactionType } from './types'

export const REACTIONS: Array<{ type: ReactionType; label: string; emoji: string }> = [
  { type: 'LIKE', label: 'Thích', emoji: '👍' },
  { type: 'LOVE', label: 'Yêu thích', emoji: '❤️' },
  { type: 'HAHA', label: 'Haha', emoji: '😄' },
  { type: 'SAD', label: 'Buồn', emoji: '😢' },
  { type: 'ANGRY', label: 'Phẫn nộ', emoji: '😠' },
]

export function reactionPresentation(type: ReactionType | null) {
  return REACTIONS.find((item) => item.type === type) ?? REACTIONS[0]
}
