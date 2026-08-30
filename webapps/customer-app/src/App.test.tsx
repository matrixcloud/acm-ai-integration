import { act, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { StrictMode } from "react"
import { afterEach, describe, expect, it, vi } from "vitest"

import App from "@/App"
import * as customerSvc from "@/services/customer-agent"
import type { BackendConversation, BackendMessage, MessageThread } from "@/services/customer-agent"

vi.mock("@/services/customer-agent", async (importOriginal) => {
  const actual = await importOriginal<typeof customerSvc>()
  return {
    ...actual,
    createConversation: vi.fn(),
    fetchQuickQuestions: vi.fn(),
    streamAssistantReply: vi.fn(),
    endConversation: vi.fn(),
    submitFeedback: vi.fn(),
  }
})

const mockedCustomerSvc = vi.mocked(customerSvc)

function backendMessage(overrides: Partial<BackendMessage>): BackendMessage {
  return {
    id: 1,
    seqNo: 1,
    role: "CUSTOMER",
    content: "你好",
    createdAt: "2026-08-29T12:00:00",
    ...overrides,
  }
}

function activeConversation(): BackendConversation {
  return {
    id: 1,
    conversationNo: "CON-TEST-1",
    customerId: "customer-001",
    status: "ACTIVE",
    startedAt: "2026-08-29T12:00:00",
    endedAt: null,
    createdAt: "2026-08-29T12:00:00",
    messages: [],
    feedback: null,
  }
}

function thread(messages: BackendMessage[]): MessageThread {
  return { conversationNo: "CON-TEST-1", messages }
}

describe("App", () => {
  afterEach(() => {
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it("creates a conversation, lists quick questions and completes a real message round-trip", async () => {
    mockedCustomerSvc.createConversation.mockResolvedValue(activeConversation())
    mockedCustomerSvc.fetchQuickQuestions.mockResolvedValue([
      { id: 1, sortOrder: 1, questionText: "怎么查询订单进度？" },
      { id: 2, sortOrder: 2, questionText: "如何申请退款？" },
    ])
    mockedCustomerSvc.streamAssistantReply.mockResolvedValue(
      thread([
        backendMessage({ content: "怎么查询订单进度？" }),
        backendMessage({ id: 2, seqNo: 2, role: "AGENT", content: "你可以在「我的订单」中查看最新进度。" }),
      ]),
    )

    render(
      <StrictMode>
        <App />
      </StrictMode>,
    )

    await waitFor(() => {
      expect(mockedCustomerSvc.createConversation).toHaveBeenCalledWith("cust-001")
    })

    const quickQuestion = await screen.findByRole("button", { name: "怎么查询订单进度？" })
    fireEvent.click(quickQuestion)

    expect(mockedCustomerSvc.streamAssistantReply).toHaveBeenCalledWith(
      "CON-TEST-1",
      "怎么查询订单进度？",
      expect.any(Function),
    )

    await waitFor(() => {
      expect(screen.getByText("你可以在「我的订单」中查看最新进度。")).toBeInTheDocument()
    })
    expect(screen.queryByRole("status", { name: "客服正在输入" })).not.toBeInTheDocument()
  })

  it("renders streamed tokens before replacing them with the persisted reply", async () => {
    mockedCustomerSvc.createConversation.mockResolvedValue(activeConversation())
    mockedCustomerSvc.fetchQuickQuestions.mockResolvedValue([])
    mockedCustomerSvc.streamAssistantReply.mockImplementation(
      (_conversationNo, _content, onChunk) => {
        onChunk("您好，正在为您")
        return Promise.resolve(
          thread([
            backendMessage({ content: "你好" }),
            backendMessage({ id: 2, seqNo: 2, role: "AGENT", content: "您好，正在为您查询订单。" }),
          ]),
        )
      },
    )

    render(<App />)

    await waitFor(() => {
      expect(mockedCustomerSvc.createConversation).toHaveBeenCalled()
    })

    fireEvent.change(screen.getByPlaceholderText("请输入你想咨询的问题…"), {
      target: { value: "你好" },
    })
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }))

    await waitFor(() => {
      expect(screen.getByText("您好，正在为您")).toBeInTheDocument()
    })

    await waitFor(() => {
      expect(screen.getByText("您好，正在为您查询订单。")).toBeInTheDocument()
    })
    expect(screen.queryByText("您好，正在为您")).not.toBeInTheDocument()
    expect(screen.queryByRole("status", { name: "客服正在输入" })).not.toBeInTheDocument()
  })

  it("shows the typing indicator while waiting for the agent reply", async () => {
    mockedCustomerSvc.createConversation.mockResolvedValue(activeConversation())
    mockedCustomerSvc.fetchQuickQuestions.mockResolvedValue([])
    let resolveReply: (value: MessageThread) => void = () => {}
    mockedCustomerSvc.streamAssistantReply.mockImplementation(
      () =>
        new Promise<MessageThread>((resolve) => {
          resolveReply = resolve
        }),
    )

    render(<App />)

    await waitFor(() => {
      expect(mockedCustomerSvc.createConversation).toHaveBeenCalled()
    })

    fireEvent.change(screen.getByPlaceholderText("请输入你想咨询的问题…"), {
      target: { value: "你好" },
    })
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }))

    await waitFor(() => {
      expect(screen.getByRole("status", { name: "客服正在输入" })).toBeInTheDocument()
    })

    await act(async () => {
      resolveReply(
        thread([
          backendMessage({ content: "你好" }),
          backendMessage({ id: 2, seqNo: 2, role: "AGENT", content: "您好，有什么可以帮您？" }),
        ]),
      )
    })

    expect(screen.queryByRole("status", { name: "客服正在输入" })).not.toBeInTheDocument()
    expect(screen.getByText("您好，有什么可以帮您？")).toBeInTheDocument()
  })

  it("rolls back the optimistic message when the send fails", async () => {
    mockedCustomerSvc.createConversation.mockResolvedValue(activeConversation())
    mockedCustomerSvc.fetchQuickQuestions.mockResolvedValue([])
    mockedCustomerSvc.streamAssistantReply.mockRejectedValue(
      new customerSvc.CustomerServiceError("Mock AI Agent configured to fail", "EXTERNAL_DEPENDENCY_FAILED", 502),
    )

    render(<App />)

    await waitFor(() => {
      expect(mockedCustomerSvc.createConversation).toHaveBeenCalled()
    })

    fireEvent.change(screen.getByPlaceholderText("请输入你想咨询的问题…"), {
      target: { value: "我的订单到哪了？" },
    })
    fireEvent.click(screen.getByRole("button", { name: "发送消息" }))

    await waitFor(() => {
      expect(screen.getByText("Mock AI Agent configured to fail")).toBeInTheDocument()
    })
    expect(screen.queryByText("我的订单到哪了？")).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText("请输入你想咨询的问题…")).not.toBeDisabled()
  })

  it("ends the conversation and submits feedback through the API", async () => {
    mockedCustomerSvc.createConversation.mockResolvedValue(activeConversation())
    mockedCustomerSvc.fetchQuickQuestions.mockResolvedValue([])
    mockedCustomerSvc.endConversation.mockResolvedValue({
      ...activeConversation(),
      status: "AWAITING_FEEDBACK",
    })
    mockedCustomerSvc.submitFeedback.mockResolvedValue({
      ...activeConversation(),
      status: "ENDED",
      feedback: { rating: "SATISFIED", comment: null, submittedAt: "2026-08-29T12:10:00" },
    })

    render(<App />)

    await waitFor(() => {
      expect(mockedCustomerSvc.createConversation).toHaveBeenCalled()
    })

    fireEvent.click(screen.getByRole("button", { name: "结束咨询" }))
    await waitFor(() => {
      expect(mockedCustomerSvc.endConversation).toHaveBeenCalledWith("CON-TEST-1")
    })

    fireEvent.click(screen.getByRole("button", { name: "已解决" }))
    await waitFor(() => {
      expect(mockedCustomerSvc.submitFeedback).toHaveBeenCalledWith("CON-TEST-1", "satisfied")
    })
    expect(await screen.findByText("感谢你的评价，本次咨询已结束")).toBeInTheDocument()
    expect(screen.getByPlaceholderText("本次咨询已结束")).toBeDisabled()
  })
})
