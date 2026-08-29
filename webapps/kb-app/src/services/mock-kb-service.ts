import {
  DEFAULT_MOCK_SEARCH_RESULTS,
  MOCK_DOCUMENTS,
  MOCK_EVAL_RUN,
  MOCK_EVAL_SUITES,
  MOCK_KNOWLEDGE_BASES,
  MOCK_LATENCY_MS,
  MOCK_SEARCH_RULES,
} from "@/data/mock-kb"
import type {
  EvalRunReport,
  EvalSuite,
  KbDocument,
  KnowledgeBase,
  SearchResult,
} from "@/types/kb"

export type MockKbService = {
  listKnowledgeBases(): Promise<KnowledgeBase[]>
  getKnowledgeBase(kbId: string): Promise<KnowledgeBase | null>
  listDocuments(kbId: string): Promise<KbDocument[]>
  uploadDocument(kbId: string, file: File): Promise<KbDocument>
  deleteDocument(kbId: string, docId: string): Promise<void>
  search(kbId: string, query: string, topK: number): Promise<SearchResult[]>
  archiveKnowledgeBase(kbId: string): Promise<KnowledgeBase>
  activateKnowledgeBase(kbId: string): Promise<KnowledgeBase>
  listEvalSuites(): Promise<EvalSuite[]>
  startEvalRun(kbNo: string, suiteNo: string, topK: number): Promise<EvalRunReport>
  getEvalRun(runNo: string): Promise<EvalRunReport | null>
}

function delay(ms: number = MOCK_LATENCY_MS): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function matchSearchResults(query: string): SearchResult[] {
  const normalized = query.trim()

  if (!normalized) {
    return []
  }

  const rule = MOCK_SEARCH_RULES.find((item) =>
    item.keywords.some((keyword) => normalized.includes(keyword)),
  )

  return rule?.results ?? DEFAULT_MOCK_SEARCH_RESULTS
}

export const mockKbService: MockKbService = {
  async listKnowledgeBases() {
    await delay()
    return MOCK_KNOWLEDGE_BASES.map((kb) => ({ ...kb }))
  },

  async getKnowledgeBase(kbId) {
    await delay()
    const found = MOCK_KNOWLEDGE_BASES.find((kb) => kb.id === kbId)
    return found ? { ...found } : null
  },

  async listDocuments(kbId) {
    await delay()
    const docs = MOCK_DOCUMENTS[kbId] ?? []
    return docs.map((doc) => ({ ...doc }))
  },

  async uploadDocument(kbId, file) {
    await delay(MOCK_LATENCY_MS + 200)

    const doc: KbDocument = {
      id: `doc-${Date.now()}`,
      name: file.name,
      status: "ready",
      chunkCount: Math.max(1, Math.ceil(file.size / 512)),
      createdAt: new Date().toISOString(),
    }

    const docs = MOCK_DOCUMENTS[kbId] ?? (MOCK_DOCUMENTS[kbId] = [])
    docs.push(doc)
    const kb = MOCK_KNOWLEDGE_BASES.find((item) => item.id === kbId)
    if (kb) {
      kb.docCount = docs.length
    }

    return { ...doc }
  },

  async deleteDocument(kbId, docId) {
    await delay()
    const docs = MOCK_DOCUMENTS[kbId]

    if (docs) {
      const index = docs.findIndex((doc) => doc.id === docId)
      if (index >= 0) {
        docs.splice(index, 1)
      }
    }

    const kb = MOCK_KNOWLEDGE_BASES.find((item) => item.id === kbId)
    if (kb && docs) {
      kb.docCount = docs.length
    }
  },

  async search(kbId, query, topK) {
    await delay()
    const results = matchSearchResults(query)
    return results.slice(0, topK).map((result) => ({ ...result }))
  },

  async archiveKnowledgeBase(kbId) {
    await delay()
    const kb = MOCK_KNOWLEDGE_BASES.find((item) => item.id === kbId)

    if (!kb) {
      throw new Error(`知识库 ${kbId} 不存在`)
    }

    kb.status = "archived"
    return { ...kb }
  },

  async activateKnowledgeBase(kbId) {
    await delay()
    const kb = MOCK_KNOWLEDGE_BASES.find((item) => item.id === kbId)

    if (!kb) {
      throw new Error(`知识库 ${kbId} 不存在`)
    }

    kb.status = "active"
    return { ...kb }
  },

  async listEvalSuites() {
    await delay()
    return MOCK_EVAL_SUITES.map((suite) => ({ ...suite }))
  },

  async startEvalRun(kbNo, suiteNo, topK) {
    await delay(MOCK_LATENCY_MS + 400)
    return {
      ...MOCK_EVAL_RUN,
      runNo: `run-${Date.now()}`,
      kbNo,
      details: MOCK_EVAL_RUN.details.map((detail) => ({
        ...detail,
        contextRelevancyScore: Math.round(Math.min(1, detail.contextRelevancyScore + (topK > 3 ? 0.02 : 0)) * 100) / 100,
      })),
    }
  },

  async getEvalRun(runNo) {
    await delay()
    if (runNo === MOCK_EVAL_RUN.runNo) {
      return { ...MOCK_EVAL_RUN }
    }
    return null
  },
}
