import type {
  ChatMessage,
  ConversationStatus,
  FeedbackRating,
} from "@/types/chat"
import { parseSseStream } from "@/services/sse"

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ""
const API_VERSION = "1"

// Backend DTOs (customer-agent merge design §9, 2026-08-30)
export type BackendMessageRole = "CUSTOMER" | "AGENT"
export type BackendConversationStatus = "ACTIVE" | "AWAITING_FEEDBACK" | "ENDED"
export type BackendFeedbackRating = "SATISFIED" | "DISSATISFIED"

export type BackendMessage = {
  id: number
  seqNo: number
  role: BackendMessageRole
  content: string
  createdAt: string
}

export type BackendFeedback = {
  rating: BackendFeedbackRating
  comment: string | null
  submittedAt: string
}

export type BackendConversation = {
  id: number
  conversationNo: string
  customerId: string
  status: BackendConversationStatus
  startedAt: string
  endedAt: string | null
  createdAt: string
  messages: BackendMessage[]
  feedback: BackendFeedback | null
}

export type QuickQuestion = {
  id: number
  sortOrder: number
  questionText: string
}

export type MessageThread = {
  conversationNo: string
  messages: BackendMessage[]
}

type ProblemDetail = {
  code?: string
  detail?: string
  title?: string
  status?: number
}

export class CustomerServiceError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly httpStatus: number,
  ) {
    super(message)
    this.name = "CustomerServiceError"
  }
}

function idempotencyKey(): string {
  return crypto.randomUUID()
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set("API-Version", API_VERSION)
  if (init?.method && init.method !== "GET") {
    headers.set("Idempotency-Key", idempotencyKey())
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  })

  if (!response.ok) {
    throw await toServiceError(response)
  }

  return (await response.json()) as T
}

async function toServiceError(response: Response): Promise<CustomerServiceError> {
  let problem: ProblemDetail = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // non-JSON error body; keep defaults
  }
  const code = problem.code ?? "HTTP_ERROR"
  const detail = problem.detail ?? problem.title ?? `请求失败（HTTP ${response.status}）`
  return new CustomerServiceError(detail, code, response.status)
}

export async function createConversation(customerId: string): Promise<BackendConversation> {
  return request<BackendConversation>("/conversations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ customerId }),
  })
}

export async function fetchQuickQuestions(): Promise<QuickQuestion[]> {
  return request<QuickQuestion[]>("/quick-questions")
}

/**
 * Sends a customer message and streams the agent reply via SSE: `chunk` events are forwarded to
 * `onChunk` as they arrive, the final `done` event resolves with the persisted MessageThread, and
 * an in-band `error` event (or a stream that ends without `done`) rejects the promise.
 */
export async function streamAssistantReply(
  conversationNo: string,
  content: string,
  onChunk: (token: string) => void,
): Promise<MessageThread> {
  const response = await fetch(
    `${API_BASE_URL}/conversations/${encodeURIComponent(conversationNo)}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "API-Version": API_VERSION,
        "Idempotency-Key": idempotencyKey(),
        Accept: "text/event-stream",
      },
      body: JSON.stringify({ content }),
    },
  )

  if (!response.ok) {
    throw await toServiceError(response)
  }
  if (!response.body) {
    throw new CustomerServiceError("响应缺少可读的数据流", "EMPTY_STREAM", response.status)
  }

  let thread: MessageThread | null = null
  for await (const event of parseSseStream(response.body)) {
    if (event.event === "chunk") {
      onChunk(event.data)
    } else if (event.event === "done") {
      thread = parseJsonFrame(event.data, response.status) as MessageThread
    } else if (event.event === "error") {
      const problem = parseJsonFrame(event.data, response.status) as ProblemDetail
      throw new CustomerServiceError(
        problem.detail ?? problem.code ?? "回复生成失败",
        problem.code ?? "SSE_ERROR",
        response.status,
      )
    }
  }

  if (!thread) {
    throw new CustomerServiceError("回复流在完成前中断", "INCOMPLETE_STREAM", response.status)
  }
  return thread
}

function parseJsonFrame(data: string, httpStatus: number): unknown {
  try {
    return JSON.parse(data)
  } catch {
    throw new CustomerServiceError("回复流包含无法解析的数据帧", "SSE_ERROR", httpStatus)
  }
}

export async function endConversation(conversationNo: string): Promise<BackendConversation> {
  return request<BackendConversation>(
    `/conversations/${encodeURIComponent(conversationNo)}/end`,
    { method: "POST" },
  )
}

export async function submitFeedback(
  conversationNo: string,
  rating: FeedbackRating,
  comment?: string,
): Promise<BackendConversation> {
  return request<BackendConversation>(
    `/conversations/${encodeURIComponent(conversationNo)}/feedback`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        rating: toBackendRating(rating),
        ...(comment ? { comment } : {}),
      }),
    },
  )
}

export function toBackendRole(role: BackendMessageRole): ChatMessage["role"] {
  return role === "AGENT" ? "support" : "customer"
}

export function toChatMessage(message: BackendMessage): ChatMessage {
  return {
    id: String(message.id),
    role: toBackendRole(message.role),
    content: message.content,
    createdAt: new Date(message.createdAt),
  }
}

export function toConversationStatus(status: BackendConversationStatus): ConversationStatus {
  switch (status) {
    case "ACTIVE":
      return "active"
    case "AWAITING_FEEDBACK":
      return "awaiting-feedback"
    case "ENDED":
      return "ended"
  }
}

function toBackendRating(rating: FeedbackRating): BackendFeedbackRating {
  return rating === "satisfied" ? "SATISFIED" : "DISSATISFIED"
}

export function toFeedbackRating(rating: BackendFeedbackRating): FeedbackRating {
  return rating === "SATISFIED" ? "satisfied" : "unsatisfied"
}