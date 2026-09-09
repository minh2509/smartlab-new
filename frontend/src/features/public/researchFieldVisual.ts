import aiResearchImage from '../../assets/fields/ai-research.webp'
import roboticsResearchImage from '../../assets/fields/robotics-research.webp'
import softwareEngineeringImage from '../../assets/fields/software-engineering.webp'
import type { ResearchField } from '../../shared/types/api'

const FIELD_VISUALS = {
  ai: {
    color: 'var(--s1)',
    image: aiResearchImage,
    imageAlt: 'Minh họa nghiên cứu trí tuệ nhân tạo trong phòng lab',
    tags: ['Computer Vision', 'NLP', 'Deep Learning'],
  },
  robotics: {
    color: 'var(--s2)',
    image: roboticsResearchImage,
    imageAlt: 'Minh họa cánh tay robot và cảm biến trong phòng lab',
    tags: ['Embedded', 'Control', 'ROS'],
  },
  software: {
    color: 'var(--s3)',
    image: softwareEngineeringImage,
    imageAlt: 'Minh họa kiến trúc phần mềm và quy trình kiểm thử',
    tags: ['Kiến trúc', 'DevOps', 'Kiểm thử'],
  },
} as const

export function fieldVisual(field: ResearchField) {
  const key = `${field.code} ${field.name}`.toLocaleLowerCase('vi')
  if (key.includes('robot')) return FIELD_VISUALS.robotics
  if (key.includes('software') || key.includes('phần mềm') || /(^|\s)se(\s|$)/.test(key)) {
    return FIELD_VISUALS.software
  }
  return FIELD_VISUALS.ai
}

export function publicFileUrl(fileId: number) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1.0'
  return `${baseUrl}/files/${fileId}`
}
