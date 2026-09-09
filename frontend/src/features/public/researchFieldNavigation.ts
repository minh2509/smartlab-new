export const RESEARCH_FIELDS_HASH = '#research-fields'
export const RESEARCH_FIELDS_PATH = `/${RESEARCH_FIELDS_HASH}`

export function scrollToResearchFields() {
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  document.getElementById('research-fields')?.scrollIntoView({
    behavior: reducedMotion ? 'auto' : 'smooth',
    block: 'start',
  })
}
