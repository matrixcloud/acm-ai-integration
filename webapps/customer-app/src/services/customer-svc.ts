import type {
  ChatMessage,
  ConversationStatus,
  FeedbackRating,
} from "@/types/chat"

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ""
const API_VERSION = "1"

// Backend DTOs (customer-svc §9.1, §9.4)
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
    let problem: ProblemDetail = {}
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // non-JSON error body; keep defaults
    }
    const code = problem.code ?? "HTTP_ERROR"
    const detail = problem.detail ?? problem.title ?? `请求失败（HTTP ${response.status}）`
    throw new CustomerServiceError(detail, code, response.status)
  }

  return (await response.json()) as T
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

export async function sendMessage(
  conversationNo: string,
  content: string,
): Promise<MessageThread> {
  return request<MessageThread>(`/conversations/${encodeURIComponent(conversationNo)}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ content }),
  })
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