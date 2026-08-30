import type {
  EvalRunReport,
  EvalSuite,
  KbDocument,
  KnowledgeBase,
  SearchResult,
} from "@/types/kb"

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ""

// Backend DTOs (kb-service design §9, 2026-08-29)
type BackendKbStatus = "ACTIVE" | "ARCHIVED"
type BackendDocumentStatus = "PROCESSING" | "READY" | "FAILED"
type BackendEvalRunStatus = "RUNNING" | "COMPLETED" | "FAILED"

type BackendKnowledgeBase = {
  kbNo: string
  name: string
  status: BackendKbStatus
  docCount: number
  createdAt: string
}

type BackendDocument = {
  documentNo: string
  name: string
  status: BackendDocumentStatus
  chunkCount: number
  createdAt: string
}

type BackendSearchResponse = {
  chunks: {
    content: string
    score: number
    documentNo: string
    documentName: string
  }[]
}

type BackendEvalSuite = {
  suiteNo: string
  name: string
  caseCount: number
  createdAt: string
}

type BackendEvalRunReport = {
  runNo: string
  kbNo: string
  status: BackendEvalRunStatus
  topK: number
  startedAt: string
  finishedAt: string | null
  metrics: {
    contextRelevancy: { avgScore: number; passRate: number }
    faithfulness: { avgScore: number; passRate: number }
    answerRelevancy: { avgScore: number; passRate: number }
  }
  details: {
    query: string
    generatedAnswer: string
    contextRelevancyScore: number
    faithfulnessScore: number
    answerRelevancyScore: number
  }[]
}

type ProblemDetail = {
  code?: string
  detail?: string
  title?: string
  status?: number
}

export class KbServiceError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly httpStatus: number,
  ) {
    super(message)
    this.name = "KbServiceError"
  }
}

export type KbService = {
  listKnowledgeBases(): Promise<KnowledgeBase[]>
  listDocuments(kbNo: string): Promise<KbDocument[]>
  uploadDocument(kbNo: string, file: File): Promise<KbDocument>
  deleteDocument(kbNo: string, docNo: string): Promise<void>
  search(kbNo: string, query: string, topK: number): Promise<SearchResult[]>
  archiveKnowledgeBase(kbNo: string): Promise<KnowledgeBase>
  activateKnowledgeBase(kbNo: string): Promise<KnowledgeBase>
  listEvalSuites(): Promise<EvalSuite[]>
  startEvalRun(kbNo: string, suiteNo: string, topK: number): Promise<EvalRunReport>
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init)

  if (!response.ok) {
    throw await toServiceError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

async function toServiceError(response: Response): Promise<KbServiceError> {
  let problem: ProblemDetail = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // non-JSON error body; keep defaults
  }
  const code = problem.code ?? "HTTP_ERROR"
  const detail = problem.detail ?? problem.title ?? `请求失败（HTTP ${response.status}）`
  return new KbServiceError(detail, code, response.status)
}

export function toKnowledgeBase(kb: BackendKnowledgeBase): KnowledgeBase {
  return {
    id: kb.kbNo,
    name: kb.name,
    status: toKbStatus(kb.status),
    docCount: kb.docCount,
    createdAt: kb.createdAt,
  }
}

export function toDocument(document: BackendDocument): KbDocument {
  return {
    id: document.documentNo,
    name: document.name,
    status: toDocumentStatus(document.status),
    chunkCount: document.chunkCount,
    createdAt: document.createdAt,
  }
}

export function toSearchResults(response: BackendSearchResponse): SearchResult[] {
  return response.chunks.map((chunk) => ({
    content: chunk.content,
    score: chunk.score,
    documentName: chunk.documentName,
  }))
}

export function toEvalSuite(suite: BackendEvalSuite): EvalSuite {
  return {
    id: suite.suiteNo,
    name: suite.name,
    caseCount: suite.caseCount,
  }
}

export function toEvalRunReport(run: BackendEvalRunReport): EvalRunReport {
  return {
    runNo: run.runNo,
    kbNo: run.kbNo,
    status: toEvalRunStatus(run.status),
    metrics: run.metrics,
    details: run.details,
  }
}

function toKbStatus(status: BackendKbStatus): KnowledgeBase["status"] {
  switch (status) {
    case "ACTIVE":
      return "active"
    case "ARCHIVED":
      return "archived"
  }
}

function toDocumentStatus(status: BackendDocumentStatus): KbDocument["status"] {
  switch (status) {
    case "PROCESSING":
      return "processing"
    case "READY":
      return "ready"
    case "FAILED":
      return "failed"
  }
}

function toEvalRunStatus(status: BackendEvalRunStatus): EvalRunReport["status"] {
  switch (status) {
    case "RUNNING":
      return "running"
    case "COMPLETED":
      return "completed"
    case "FAILED":
      return "failed"
  }
}

export const kbService: KbService = {
  async listKnowledgeBases() {
    const kbs = await request<BackendKnowledgeBase[]>("/kbs")
    return kbs.map(toKnowledgeBase)
  },

  async listDocuments(kbNo) {
    const documents = await request<BackendDocument[]>(
      `/kbs/${encodeURIComponent(kbNo)}/documents`,
    )
    return documents.map(toDocument)
  },

  async uploadDocument(kbNo, file) {
    const formData = new FormData()
    formData.append("file", file)
    const document = await request<BackendDocument>(
      `/kbs/${encodeURIComponent(kbNo)}/documents`,
      { method: "POST", body: formData },
    )
    return toDocument(document)
  },

  async deleteDocument(kbNo, docNo) {
    await request<void>(
      `/kbs/${encodeURIComponent(kbNo)}/documents/${encodeURIComponent(docNo)}`,
      { method: "DELETE" },
    )
  },

  async search(kbNo, query, topK) {
    const response = await request<BackendSearchResponse>(
      `/kbs/${encodeURIComponent(kbNo)}/search`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, topK }),
      },
    )
    return toSearchResults(response)
  },

  async archiveKnowledgeBase(kbNo) {
    const kb = await request<BackendKnowledgeBase>(
      `/kbs/${encodeURIComponent(kbNo)}/archive`,
      { method: "POST" },
    )
    return toKnowledgeBase(kb)
  },

  async activateKnowledgeBase(kbNo) {
    const kb = await request<BackendKnowledgeBase>(
      `/kbs/${encodeURIComponent(kbNo)}/activate`,
      { method: "POST" },
    )
    return toKnowledgeBase(kb)
  },

  async listEvalSuites() {
    const suites = await request<BackendEvalSuite[]>("/eval/suites")
    return suites.map(toEvalSuite)
  },

  async startEvalRun(kbNo, suiteNo, topK) {
    const run = await request<BackendEvalRunReport>("/eval/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kbNo, suiteNo, topK }),
    })
    return toEvalRunReport(run)
  },
}