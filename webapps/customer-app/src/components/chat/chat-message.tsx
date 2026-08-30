import { Bot } from "lucide-react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"

import { SupportAvatar } from "@/components/chat/support-avatar"
import { cn } from "@/lib/utils"
import type { ChatMessage as ChatMessageType } from "@/types/chat"

const TIME_FORMATTER = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
})

const REMARK_PLUGINS = [remarkGfm]

type ChatMessageProps = {
  message: ChatMessageType
  renderMarkdown?: boolean
}

export function ChatMessage({ message, renderMarkdown = true }: ChatMessageProps) {
  const isCustomer = message.role === "customer"
  const shouldRenderMarkdown = !isCustomer && renderMarkdown

  return (
    <article
      className={cn("flex items-end gap-2.5", isCustomer && "justify-end")}
      aria-label={isCustomer ? "你的消息" : "客服消息"}
    >
      {!isCustomer && <SupportAvatar className="size-8" />}
      <div className={cn("max-w-[82%]", isCustomer && "flex flex-col items-end")}>
        {!isCustomer && (
          <div className="mb-1.5 flex items-center gap-1.5 px-1 text-xs font-medium text-muted-foreground">
            <Bot aria-hidden="true" className="size-3.5" />
            智能客服小安
          </div>
        )}
        <div
          className={cn(
            "break-words rounded-2xl px-4 py-3 text-[15px] leading-6 shadow-[0_1px_2px_rgba(21,39,64,0.06)]",
            isCustomer
              ? "whitespace-pre-wrap rounded-br-md bg-primary text-primary-foreground"
              : "rounded-bl-md border border-border/75 bg-white text-foreground",
            !shouldRenderMarkdown && "whitespace-pre-wrap",
          )}
        >
          {shouldRenderMarkdown ? (
            <div className="markdown-body">
              <ReactMarkdown remarkPlugins={REMARK_PLUGINS}>{message.content}</ReactMarkdown>
            </div>
          ) : (
            message.content
          )}
        </div>
        <time className="mt-1.5 px-1 text-[11px] text-muted-foreground/80">
          {TIME_FORMATTER.format(message.createdAt)}
        </time>
      </div>
    </article>
  )
}