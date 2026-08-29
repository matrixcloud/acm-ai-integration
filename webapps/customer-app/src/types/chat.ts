export type MessageRole = "customer" | "support"

export type ChatMessage = {
  id: string
  role: MessageRole
  content: string
  createdAt: Date
}

export type ConversationStatus = "active" | "responding" | "awaiting-feedback" | "ended"

export type FeedbackRating = "satisfied" | "unsatisfied"
