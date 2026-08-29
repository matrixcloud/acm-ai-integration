import { Activity, BookOpen } from "lucide-react"
import { useCallback, useEffect, useState } from "react"

import { DocumentList } from "@/components/kb/document-list"
import { EvalReport } from "@/components/kb/eval-report"
import { KbList } from "@/components/kb/kb-list"
import { SearchPanel } from "@/components/kb/search-panel"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { mockKbService } from "@/services/mock-kb-service"
import type {
  EvalRunReport,
  KbDocument,
  KnowledgeBase,
  SearchResult,
} from "@/types/kb"

function App() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])
  const [selectedKbId, setSelectedKbId] = useState<string | null>(null)
  const [documents, setDocuments] = useState<KbDocument[]>([])
  const [searchResults, setSearchResults] = useState<SearchResult[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const [evalRun, setEvalRun] = useState<EvalRunReport | null>(null)
  const [isRunningEval, setIsRunningEval] = useState(false)
  const [isLoadingKbs, setIsLoadingKbs] = useState(true)

  const selectedKb = knowledgeBases.find((kb) => kb.id === selectedKbId) ?? null

  const refreshDocuments = useCallback(async (kbId: string) => {
    const docs = await mockKbService.listDocuments(kbId)
    setDocuments(docs)
  }, [])

  const refreshKnowledgeBases = useCallback(async () => {
    const kbs = await mockKbService.listKnowledgeBases()
    setKnowledgeBases(kbs)
    return kbs
  }, [])

  useEffect(() => {
    const loadInitial = async () => {
      setIsLoadingKbs(true)
      const kbs = await refreshKnowledgeBases()
      const firstActive = kbs.find((kb) => kb.status === "active") ?? kbs[0]

      if (firstActive) {
        setSelectedKbId(firstActive.id)
        await refreshDocuments(firstActive.id)
      }
      setIsLoadingKbs(false)
    }

    void loadInitial()
  }, [refreshKnowledgeBases, refreshDocuments])

  const handleSelectKb = useCallback(
    (kbId: string) => {
      setSelectedKbId(kbId)
      setSearchResults([])
      void refreshDocuments(kbId)
    },
    [refreshDocuments],
  )

  const handleToggleStatus = useCallback(
    async (kbId: string) => {
      const kb = knowledgeBases.find((item) => item.id === kbId)
      if (!kb) {
        return
      }

      const updated =
        kb.status === "active"
          ? await mockKbService.archiveKnowledgeBase(kbId)
          : await mockKbService.activateKnowledgeBase(kbId)

      setKnowledgeBases((prev) =>
        prev.map((item) => (item.id === updated.id ? { ...updated } : item)),
      )
    },
    [knowledgeBases],
  )

  const handleUpload = useCallback(
    async (file: File) => {
      if (!selectedKbId) {
        return
      }

      setIsUploading(true)
      try {
        const doc = await mockKbService.uploadDocument(selectedKbId, file)
        setDocuments((prev) => [...prev, doc])
        const kbs = await refreshKnowledgeBases()
        setKnowledgeBases(kbs)
      } finally {
        setIsUploading(false)
      }
    },
    [selectedKbId, refreshKnowledgeBases],
  )

  const handleDelete = useCallback(
    async (docId: string) => {
      if (!selectedKbId) {
        return
      }

      await mockKbService.deleteDocument(selectedKbId, docId)
      setDocuments((prev) => prev.filter((doc) => doc.id !== docId))

      const kbs = await refreshKnowledgeBases()
      setKnowledgeBases(kbs)
    },
    [selectedKbId, refreshKnowledgeBases],
  )

  const handleSearch = useCallback(
    async (query: string) => {
      if (!selectedKbId) {
        return
      }

      setIsSearching(true)
      try {
        setSearchResults(await mockKbService.search(selectedKbId, query, 3))
      } finally {
        setIsSearching(false)
      }
    },
    [selectedKbId],
  )

  const handleStartEval = useCallback(async () => {
    if (!selectedKbId) {
      return
    }

    setIsRunningEval(true)
    try {
      const suites = await mockKbService.listEvalSuites()
      const report = await mockKbService.startEvalRun(
        selectedKbId,
        suites[0]?.id ?? "suite-001",
        3,
      )
      setEvalRun(report)
    } finally {
      setIsRunningEval(false)
    }
  }, [selectedKbId])

  return (
    <main className="relative min-h-svh bg-page px-4 py-6 md:px-8 md:py-10">
      <header className="mx-auto mb-6 flex max-w-6xl items-center gap-3">
        <span className="flex size-10 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <BookOpen className="size-5" />
        </span>
        <div className="flex flex-col">
          <h1 className="text-lg font-semibold text-foreground">知识库管理</h1>
          <p className="text-xs text-muted-foreground">
            管理知识库、上传文档并测试检索效果
          </p>
        </div>
      </header>

      <div className="mx-auto grid max-w-6xl gap-4 md:grid-cols-[300px_1fr]">
        <Card>
          <CardContent className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">知识库</h2>
              {isLoadingKbs && (
                <span className="text-xs text-muted-foreground">加载中…</span>
              )}
            </div>
            <KbList
              knowledgeBases={knowledgeBases}
              selectedKbId={selectedKbId}
              onSelect={handleSelectKb}
              onToggleStatus={handleToggleStatus}
            />
          </CardContent>
        </Card>

        <div className="flex flex-col gap-4">
          <Card>
            <CardContent>
              <DocumentList
                knowledgeBase={selectedKb}
                documents={documents}
                isUploading={isUploading}
                onUpload={handleUpload}
                onDelete={handleDelete}
              />
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <SearchPanel
                isSearching={isSearching}
                results={searchResults}
                onSearch={handleSearch}
              />
            </CardContent>
          </Card>

          <Card>
            <CardContent className="flex flex-col gap-3">
              <div className="flex items-center justify-between gap-2">
                <h2 className="text-sm font-semibold text-foreground">RAG 评估</h2>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={!selectedKbId || isRunningEval}
                  onClick={handleStartEval}
                >
                  <Activity className="size-4" />
                  发起评估
                </Button>
              </div>
              <EvalReport report={evalRun} isRunning={isRunningEval} />
            </CardContent>
          </Card>
        </div>
      </div>
    </main>
  )
}

export default App
