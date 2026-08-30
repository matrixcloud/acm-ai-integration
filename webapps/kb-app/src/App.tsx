import { Activity, BookOpen, Plus } from "lucide-react"
import { useCallback, useEffect, useState } from "react"

import { DocumentList } from "@/components/kb/document-list"
import { EvalReport } from "@/components/kb/eval-report"
import { KbList } from "@/components/kb/kb-list"
import { SearchPanel } from "@/components/kb/search-panel"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { KbServiceError, kbService } from "@/services/kb-service"
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
  const [error, setError] = useState<string | null>(null)
  const [isCreatingKb, setIsCreatingKb] = useState(false)
  const [newKbName, setNewKbName] = useState("")

  const selectedKb = knowledgeBases.find((kb) => kb.id === selectedKbId) ?? null

  const refreshDocuments = useCallback(async (kbId: string) => {
    const docs = await kbService.listDocuments(kbId)
    setDocuments(docs)
  }, [])

  const refreshKnowledgeBases = useCallback(async () => {
    const kbs = await kbService.listKnowledgeBases()
    setKnowledgeBases(kbs)
    return kbs
  }, [])

  useEffect(() => {
    const loadInitial = async () => {
      setIsLoadingKbs(true)
      setError(null)
      try {
        const kbs = await refreshKnowledgeBases()
        const firstActive = kbs.find((kb) => kb.status === "active") ?? kbs[0]

        if (firstActive) {
          setSelectedKbId(firstActive.id)
          await refreshDocuments(firstActive.id)
        }
      } catch (cause) {
        setError(
          cause instanceof KbServiceError ? cause.message : "无法加载知识库，请稍后重试",
        )
      } finally {
        setIsLoadingKbs(false)
      }
    }

    void loadInitial()
  }, [refreshKnowledgeBases, refreshDocuments])

  const handleSelectKb = useCallback(
    (kbId: string) => {
      setSelectedKbId(kbId)
      setSearchResults([])
      void refreshDocuments(kbId).catch((cause: unknown) => {
        setError(
          cause instanceof KbServiceError ? cause.message : "加载文档失败，请稍后重试",
        )
      })
    },
    [refreshDocuments],
  )

  const handleToggleStatus = useCallback(
    async (kbId: string) => {
      const kb = knowledgeBases.find((item) => item.id === kbId)
      if (!kb) {
        return
      }

      setError(null)
      try {
        const updated =
          kb.status === "active"
            ? await kbService.archiveKnowledgeBase(kbId)
            : await kbService.activateKnowledgeBase(kbId)

        setKnowledgeBases((prev) =>
          prev.map((item) => (item.id === updated.id ? { ...updated } : item)),
        )
      } catch (cause) {
        setError(
          cause instanceof KbServiceError ? cause.message : "更新知识库状态失败，请稍后重试",
        )
      }
    },
    [knowledgeBases],
  )

  const handleCreateKb = useCallback(async () => {
    const name = newKbName.trim()
    if (!name) {
      return
    }

    setIsCreatingKb(true)
    setError(null)
    try {
      const kb = await kbService.createKnowledgeBase(name)
      setNewKbName("")
      const kbs = await refreshKnowledgeBases()
      setKnowledgeBases(kbs)
      setSelectedKbId(kb.id)
      setDocuments([])
    } catch (cause) {
      setError(
        cause instanceof KbServiceError ? cause.message : "创建知识库失败，请稍后重试",
      )
    } finally {
      setIsCreatingKb(false)
    }
  }, [newKbName, refreshKnowledgeBases])

  const handleUpload = useCallback(
    async (file: File) => {
      if (!selectedKbId) {
        return
      }

      setIsUploading(true)
      setError(null)
      try {
        const doc = await kbService.uploadDocument(selectedKbId, file)
        setDocuments((prev) => [...prev, doc])
        const kbs = await refreshKnowledgeBases()
        setKnowledgeBases(kbs)
      } catch (cause) {
        setError(
          cause instanceof KbServiceError ? cause.message : "上传文档失败，请稍后重试",
        )
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

      setError(null)
      try {
        await kbService.deleteDocument(selectedKbId, docId)
        setDocuments((prev) => prev.filter((doc) => doc.id !== docId))

        const kbs = await refreshKnowledgeBases()
        setKnowledgeBases(kbs)
      } catch (cause) {
        setError(
          cause instanceof KbServiceError ? cause.message : "删除文档失败，请稍后重试",
        )
      }
    },
    [selectedKbId, refreshKnowledgeBases],
  )

  const handleSearch = useCallback(
    async (query: string) => {
      if (!selectedKbId) {
        return
      }

      setIsSearching(true)
      setError(null)
      try {
        setSearchResults(await kbService.search(selectedKbId, query, 3))
      } catch (cause) {
        setError(cause instanceof KbServiceError ? cause.message : "检索失败，请稍后重试")
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
    setError(null)
    try {
      const suites = await kbService.listEvalSuites()
      const suite = suites[0]
      if (!suite) {
        setError("暂无评估测试套件，请先在知识库服务中创建")
        return
      }

      const report = await kbService.startEvalRun(selectedKbId, suite.id, 3)
      setEvalRun(report)
    } catch (cause) {
      setError(cause instanceof KbServiceError ? cause.message : "发起评估失败，请稍后重试")
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
      {error && (
        <div className="mx-auto mb-4 max-w-6xl rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="mx-auto grid max-w-6xl gap-4 md:grid-cols-[300px_1fr]">
        <Card>
          <CardContent className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">知识库</h2>
              {isLoadingKbs && (
                <span className="text-xs text-muted-foreground">加载中…</span>
              )}
            </div>
            <form
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                void handleCreateKb()
              }}
            >
              <input
                type="text"
                value={newKbName}
                onChange={(event) => setNewKbName(event.target.value)}
                placeholder="新知识库名称"
                maxLength={100}
                aria-label="知识库名称"
                className="h-9 min-w-0 flex-1 rounded-full border border-border bg-background px-3 text-sm text-foreground outline-none placeholder:text-muted-foreground focus-visible:ring-4 focus-visible:ring-ring/25"
              />
              <Button
                type="submit"
                size="sm"
                variant="secondary"
                disabled={isCreatingKb || !newKbName.trim()}
              >
                <Plus className="size-4" />
                创建
              </Button>
            </form>

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
