import { Bot } from "lucide-react"

import { SupportAvatar } from "@/components/chat/support-avatar"
import { cn } from "@/lib/utils"
import type { ChatMessage as ChatMessageType } from "@/types/chat"

const TIME_FORMATTER = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
})

type ChatMessageProps = {
  message: ChatMessageType
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isCustomer = message.role === "customer"

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
            "break-words whitespace-pre-wrap rounded-2xl px-4 py-3 text-[15px] leading-6 shadow-[0_1px_2px_rgba(21,39,64,0.06)]",
            isCustomer
              ? "rounded-br-md bg-primary text-primary-foreground"
              : "rounded-bl-md border border-border/75 bg-white text-foreground",
          )}
        >
          {message.content}
        </div>
        <time className="mt-1.5 px-1 text-[11px] text-muted-foreground/80">
          {TIME_FORMATTER.format(message.createdAt)}
        </time>
      </div>
    </article>
  )
}
