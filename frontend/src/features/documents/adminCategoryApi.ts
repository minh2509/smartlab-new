import { apiClient } from '../../lib/apiClient'
import type {
  AdminDocumentCategory,
  AdminDocumentItem,
  AssignDocumentCategoryPayload,
  CreateDocumentCategoryPayload,
  ReorderDocumentCategoriesPayload,
  UpdateDocumentCategoryPayload,
} from './adminCategoryTypes'

export function listAdminDocumentCategories(token: string) {
  return apiClient<AdminDocumentCategory[]>('/admin/document-categories', { token })
}

export function createAdminDocumentCategory(token: string, payload: CreateDocumentCategoryPayload) {
  return apiClient<AdminDocumentCategory>('/admin/document-categories', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function updateAdminDocumentCategory(
  token: string,
  id: number,
  payload: UpdateDocumentCategoryPayload,
) {
  return apiClient<AdminDocumentCategory>(`/admin/document-categories/${id}`, {
    method: 'PUT',
    token,
    body: JSON.stringify(payload),
  })
}

export function toggleAdminDocumentCategoryActive(token: string, id: number) {
  return apiClient<AdminDocumentCategory>(`/admin/document-categories/${id}/status`, {
    method: 'PATCH',
    token,
  })
}

export function reorderAdminDocumentCategories(
  token: string,
  payload: ReorderDocumentCategoriesPayload,
) {
  return apiClient<void>('/admin/document-categories/reorder', {
    method: 'PUT',
    token,
    body: JSON.stringify(payload),
  })
}

export function assignDocumentsCategory(
  token: string,
  payload: AssignDocumentCategoryPayload,
) {
  return apiClient<void>('/admin/document-categories/assign', {
    method: 'POST',
    token,
    body: JSON.stringify(payload),
  })
}

export function listDocumentsForAssignment(token: string) {
  return apiClient<AdminDocumentItem[]>('/admin/document-categories/documents', { token })
}
