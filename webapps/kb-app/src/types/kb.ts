export type KnowledgeBaseStatus = "active" | "archived"
export type DocumentStatus = "processing" | "ready" | "failed"
export type EvalRunStatus = "running" | "completed" | "failed"

export interface KnowledgeBase {
  id: string
  name: string
  status: KnowledgeBaseStatus
  docCount: number
  createdAt: string
}

export interface KbDocument {
  id: string
  name: string
  status: DocumentStatus
  chunkCount: number
  createdAt: string
}

export interface SearchResult {
  content: string
  score: number
  documentName: string
}

export interface EvalSuite {
  id: string
  name: string
  caseCount: number
}

export interface EvalMetric {
  avgScore: number
  passRate: number
}

export interface EvalRunReport {
  runNo: string
  kbNo: string
  status: EvalRunStatus
  metrics: {
    contextRelevancy: EvalMetric
    faithfulness: EvalMetric
    answerRelevancy: EvalMetric
  }
  details: EvalRunDetail[]
}

export interface EvalRunDetail {
  query: string
  generatedAnswer: string
  contextRelevancyScore: number
  faithfulnessScore: number
  answerRelevancyScore: number
}
