import type { ChatMessage } from "@/types/chat"

export const INITIAL_MESSAGES: ChatMessage[] = [
  {
    id: "welcome-message",
    role: "support",
    content: "你好，我是 ACM 智能客服小安。很高兴为你服务，请问有什么可以帮你？",
    createdAt: new Date(),
  },
]