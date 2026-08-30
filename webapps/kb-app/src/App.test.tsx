import { fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { StrictMode } from "react"
import { afterEach, describe, expect, it, vi } from "vitest"

import App from "@/App"
import { kbService } from "@/services/kb-service"
import type { KnowledgeBase } from "@/types/kb"

vi.mock("@/services/kb-service", () => ({
  kbService: {
    createKnowledgeBase: vi.fn(),
    listKnowledgeBases: vi.fn(),
    listDocuments: vi.fn(),
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
    search: vi.fn(),
    archiveKnowledgeBase: vi.fn(),
    activateKnowledgeBase: vi.fn(),
    listEvalSuites: vi.fn(),
    startEvalRun: vi.fn(),
  },
}))

const mockedService = vi.mocked(kbService)

function knowledgeBase(id: string, name: string): KnowledgeBase {
  return {
    id,
    name,
    status: "active",
    docCount: 0,
    createdAt: "2026-08-30T00:00:00",
  }
}

describe("App", () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it("renders the knowledge base management title and lists knowledge bases", async () => {
    mockedService.listKnowledgeBases.mockResolvedValue([
      knowledgeBase("KB-1", "售后服务知识库"),
      knowledgeBase("KB-2", "订单流程知识库"),
    ])
    mockedService.listDocuments.mockResolvedValue([])

    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )

    expect(screen.getByRole("heading", { name: "知识库管理" })).toBeInTheDocument()

    await waitFor(() => {
      const list = screen.getByRole("list", { name: "知识库列表" })
      expect(within(list).getByText("售后服务知识库")).toBeInTheDocument()
      expect(within(list).getByText("订单流程知识库")).toBeInTheDocument()
    })
    expect(mockedService.listKnowledgeBases).toHaveBeenCalled()
  })

  it("creates a knowledge base from the input", async () => {
    mockedService.listKnowledgeBases.mockResolvedValue([])
    mockedService.listDocuments.mockResolvedValue([])
    mockedService.createKnowledgeBase.mockResolvedValue(
      knowledgeBase("KB-3", "新品知识库"),
    )

    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )

    fireEvent.change(await screen.findByLabelText("知识库名称"), {
      target: { value: "  新品知识库  " },
    })
    fireEvent.click(screen.getByRole("button", { name: "创建" }))

    await waitFor(() => {
      expect(mockedService.createKnowledgeBase).toHaveBeenCalledWith("新品知识库")
    })
  })
})