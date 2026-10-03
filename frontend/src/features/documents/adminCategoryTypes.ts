export type AdminDocumentCategory = {
  id: number
  code: string
  name: string
  description: string | null
  displayOrder: number
  isActive: boolean
  documentCount: number
  createdAt: string
  updatedAt: string
}

export type CreateDocumentCategoryPayload = {
  code: string
  name: string
  description?: string | null
  displayOrder?: number
  isActive?: boolean
}

export type UpdateDocumentCategoryPayload = {
  name: string
  description?: string | null
  displayOrder?: number
  isActive?: boolean
}

export type ReorderDocumentCategoriesPayload = {
  items: Array<{ id: number; displayOrder: number }>
}

export type AssignDocumentCategoryPayload = {
  documentIds: number[]
  categoryId: number | null
}

export type AdminDocumentItem = {
  id: number
  title: string
  projectId: number
  projectCode: string
  projectName: string
  categoryId: number | null
  categoryName: string | null
  fileName: string
  accessScope: string
  updatedAt: string
}
