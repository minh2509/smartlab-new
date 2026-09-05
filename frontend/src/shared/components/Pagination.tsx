import { ChevronLeft, ChevronRight } from 'lucide-react'

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  const first = Math.max(1, Math.min(page - 2, totalPages - 4))
  const last = Math.min(totalPages, Math.max(5, page + 2))
  return (
    <nav className="public-archive-pagination" aria-label="Phân trang">
      <button type="button" onClick={() => onChange(page - 1)} disabled={page <= 1} aria-label="Trang trước">
        <ChevronLeft size={17} aria-hidden="true" />
      </button>
      {Array.from({ length: last - first + 1 }, (_, index) => first + index).map((value) => (
        <button
          type="button"
          className={value === page ? 'is-current' : ''}
          key={value}
          onClick={() => onChange(value)}
          aria-current={value === page ? 'page' : undefined}
        >
          {value}
        </button>
      ))}
      <button type="button" onClick={() => onChange(page + 1)} disabled={page >= totalPages} aria-label="Trang sau">
        <ChevronRight size={17} aria-hidden="true" />
      </button>
    </nav>
  )
}
