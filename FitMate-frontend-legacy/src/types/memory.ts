export interface MemoryItem {
  id: number
  memoryType: 'FACT' | 'EPISODIC' | 'SNAPSHOT' | 'INSIGHT'
  content: string
  metadataJson: string | null
  source: string | null
  status: string
  createdAt: string
}

export interface MemoryListResponse {
  items: MemoryItem[]
  total: number
  page: number
  size: number
}

export interface ProfileTag {
  label: string
  weight: number
  category: 'identity' | 'goal' | 'condition' | 'preference' | 'status'
}

export interface ProfileResponse {
  profileText: string | null
  profileTagsJson: string | null
  memoryVersion: number | null
  generatedAt: string | null
}
