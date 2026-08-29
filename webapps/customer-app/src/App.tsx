import {
  BadgeCheck,
  Clock3,
  LockKeyhole,
  MessageCircleMore,
  Sparkles,
  Stars,
} from "lucide-react"
import { useEffect, useRef, useState } from "react"

import { ChatComposer, type ComposerMode } from "@/components/chat/chat-composer"
import { ChatMessage } from "@/components/chat/chat-message"
import { FeedbackCard } from "@/components/chat/feedback-card"
import { SupportAvatar } from "@/components/chat/support-avatar"
import { TypingIndicator } from "@/components/chat/typing-indicator"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { INITIAL_MESSAGES, QUICK_QUESTIONS } from "@/data/mock-chat"
import { getMockReply } from "@/services/mock-support-service"
import type { ChatMessage as ChatMessageType, ConversationStatus, FeedbackRating } from "@/types/chat"

const SERVICE_FEATURES = [
  {
    icon: Clock3,
    title: "随时响应",
    description: "常见问题即时解答",
  },
  {
    icon: LockKeyhole,
    title: "安心沟通",
    description: "用心守护每次咨询",
  },
] as const

const COMPOSER_MODE_BY_STATUS: Record<ConversationStatus, ComposerMode> = {
  active: "active",
  responding: "responding",
  "awaiting-feedback": "ended",
  ended: "ended",
}

function createMessage(role: ChatMessageType["role"], content: string): ChatMessageType {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    createdAt: new Date(),
  }
}

function App() {
  const [messages, setMessages] = useState<ChatMessageType[]>(INITIAL_MESSAGES)
  const [status, setStatus] = useState<ConversationStatus>("active")
  const [rating, setRating] = useState<FeedbackRating | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true

    return () => {
      isMountedRef.current = false
    }
  }, [])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" })
  }, [messages, status])

  const handleSend = async (content: string) => {
    if (status !== "active") {
      throw new Error(`会话状态为 ${status}，无法发送消息`)
    }

    setMessages((currentMessages) => [...currentMessages, createMessage("customer", content)])
    setStatus("responding")

    const reply = await getMockReply(content)
    if (!isMountedRef.current) return

    setMessages((currentMessages) => [...currentMessages, createMessage("support", reply)])
    setStatus("active")
  }

  const handleEndConversation = () => {
    if (status !== "active") {
      throw new Error(`会话状态为 ${status}，无法结束咨询`)
    }
    setStatus("awaiting-feedback")
  }

  const handleRate = (nextRating: FeedbackRating) => {
    if (status !== "awaiting-feedback") {
      throw new Error(`会话状态为 ${status}，无法提交评价`)
    }
    setRating(nextRating)
    setStatus("ended")
  }

  const isConversationOpen = status === "active" || status === "responding"
  const hasCustomerMessage = messages.some((message) => message.role === "customer")
  const composerMode = COMPOSER_MODE_BY_STATUS[status]

  return (
    <main className="relative flex min-h-svh items-center justify-center overflow-hidden bg-page px-0 py-0 md:px-6 md:py-8">
      <div className="page-glow page-glow-left" aria-hidden="true" />
      <div className="page-glow page-glow-right" aria-hidden="true" />

      <section className="relative grid h-svh w-full overflow-hidden bg-white shadow-2xl shadow-primary/10 md:h-[min(780px,calc(100svh-4rem))] md:max-w-[1120px] md:grid-cols-[380px_1fr] md:rounded-[32px] md:border md:border-white/80">
        <aside className="relative hidden overflow-hidden bg-primary p-10 text-primary-foreground md:flex md:flex-col">
          <div className="aside-orbit" aria-hidden="true" />
          <div className="relative z-10 flex items-center gap-3">
            <div className="flex size-11 items-center justify-center rounded-2xl bg-white/10 ring-1 ring-white/15">
              <MessageCircleMore aria-hidden="true" className="size-6 text-coral-light" />
            </div>
            <div>
              <div className="font-display text-lg font-bold tracking-tight">ACM Support</div>
              <div className="text-xs text-white/55">每一次回应，都更靠近你</div>
            </div>
          </div>

          <div className="relative z-10 my-auto py-12">
            <Badge className="mb-5 bg-white/10 text-coral-light ring-1 ring-white/10">
              <Sparkles aria-hidden="true" className="size-3" />
              智能服务体验
            </Badge>
            <h1 className="font-display text-[42px] leading-[1.14] font-bold tracking-[-0.035em]">
              有问题，
              <br />
              <span className="text-coral-light">我们一起解决。</span>
            </h1>
            <p className="mt-5 max-w-[280px] text-sm leading-6 text-white/65">
              清晰说明你的问题，智能客服会为你提供及时、友好的帮助。
            </p>

            <div className="mt-9 grid gap-3">
              {SERVICE_FEATURES.map(({ icon: Icon, title, description }) => (
                <div
                  className="flex items-center gap-3 rounded-2xl border border-white/10 bg-white/[0.055] p-3.5 backdrop-blur"
                  key={title}
                >
                  <div className="flex size-9 items-center justify-center rounded-xl bg-white/10 text-coral-light">
                    <Icon aria-hidden="true" className="size-4" />
                  </div>
                  <div>
                    <p className="text-sm font-semibold">{title}</p>
                    <p className="mt-0.5 text-xs text-white/50">{description}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="relative z-10 flex items-center gap-2 text-xs text-white/45">
            <BadgeCheck aria-hidden="true" className="size-4 text-emerald-300" />
            当前为前端 Mock 演示环境
          </div>
        </aside>

        <div className="flex min-h-0 flex-col bg-chat">
          <header className="flex h-[76px] shrink-0 items-center justify-between border-b border-border/75 bg-white/90 px-4 backdrop-blur md:h-[84px] md:px-6">
            <div className="flex min-w-0 items-center gap-3">
              <div className="relative">
                <SupportAvatar className="size-11 md:size-12" />
                <span className="absolute right-0 bottom-0 size-3 rounded-full border-2 border-white bg-emerald-500" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h2 className="truncate font-display text-base font-bold text-foreground md:text-lg">
                    ACM 智能客服
                  </h2>
                  <Badge variant="success" className="hidden sm:inline-flex">
                    在线
                  </Badge>
                </div>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">
                  通常几秒内回复 · AI 助手小安
                </p>
              </div>
            </div>
            {isConversationOpen && (
              <Button
                disabled={status === "responding"}
                onClick={handleEndConversation}
                size="sm"
                variant="ghost"
              >
                结束咨询
              </Button>
            )}
          </header>

          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-5 md:px-7 md:py-6">
            <div className="mx-auto flex min-h-full max-w-2xl flex-col">
              <div className="mb-6 flex items-center gap-3 text-[11px] text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                今天
                <span className="h-px flex-1 bg-border" />
              </div>

              <div className="space-y-5">
                {messages.map((message) => (
                  <ChatMessage key={message.id} message={message} />
                ))}
                {status === "responding" && <TypingIndicator />}
              </div>

              {!hasCustomerMessage && status === "active" && (
                <div className="mt-7 animate-in fade-in slide-in-from-bottom-2 duration-500">
                  <div className="mb-3 flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                    <Stars aria-hidden="true" className="size-3.5 text-coral" />
                    你可能想问
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {QUICK_QUESTIONS.map((question) => (
                      <Button
                        className="h-auto justify-start rounded-xl py-2.5 text-left font-normal"
                        key={question}
                        onClick={() => void handleSend(question)}
                        variant="outline"
                      >
                        {question}
                      </Button>
                    ))}
                  </div>
                </div>
              )}

              {(status === "awaiting-feedback" || status === "ended") && (
                <div className="mt-7 animate-in fade-in slide-in-from-bottom-2">
                  <FeedbackCard onRate={handleRate} rating={rating} />
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
          </div>

          <ChatComposer mode={composerMode} onSend={(message) => void handleSend(message)} />
        </div>
      </section>
    </main>
  )
}

export default App
