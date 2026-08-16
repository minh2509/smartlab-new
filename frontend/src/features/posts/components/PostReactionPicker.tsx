import { useState } from 'react'
import type { ReactionType } from '../types'
import { REACTIONS, reactionPresentation } from '../reactions'

type Props = {
  selected: ReactionType | null
  disabled?: boolean
  onSelect: (reaction: ReactionType) => void
  onRemove: () => void
}

export function PostReactionPicker({ selected, disabled, onSelect, onRemove }: Props) {
  const [open, setOpen] = useState(false)
  const current = reactionPresentation(selected)

  return (
    <div className="reaction-control" onKeyDown={(event) => event.key === 'Escape' && setOpen(false)}>
      {open ? (
        <div className="reaction-picker" role="menu" aria-label="Chọn cảm xúc">
          {REACTIONS.map((reaction) => (
            <button
              type="button"
              role="menuitem"
              title={reaction.label}
              aria-label={reaction.label}
              className={selected === reaction.type ? 'is-selected' : ''}
              key={reaction.type}
              onClick={() => { onSelect(reaction.type); setOpen(false) }}
            >
              <span aria-hidden="true">{reaction.emoji}</span>
            </button>
          ))}
        </div>
      ) : null}
      <button
        type="button"
        className={selected ? 'social-action is-selected' : 'social-action'}
        disabled={disabled}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => selected ? onRemove() : onSelect('LIKE')}
        onContextMenu={(event) => { event.preventDefault(); setOpen((value) => !value) }}
      >
        <span aria-hidden="true">{selected ? current.emoji : '👍'}</span>
        {selected ? current.label : 'Thích'}
      </button>
      <button
        type="button"
        className="reaction-picker-trigger"
        disabled={disabled}
        aria-label="Mở danh sách cảm xúc"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        ▾
      </button>
    </div>
  )
}
