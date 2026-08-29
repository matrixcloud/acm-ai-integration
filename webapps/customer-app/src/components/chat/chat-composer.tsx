import { ArrowUp, CornerDownLeft } from "lucide-react"
import { useState, type KeyboardEvent } from "react"

import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"

const MAX_MESSAGE_LENGTH = 500

export type ComposerMode = "active" | "responding" | "ended"

type ChatComposerProps = {
  mode: ComposerMode
  onSend: (message: string) => void
}

const COMPOSER_PLACEHOLDERS: Record<ComposerMode, string> = {
  active: "请输入你想咨询的问题…",
  responding: "正在等待客服回复…",
  ended: "本次咨询已结束",
}

export function ChatComposer({ mode, onSend }: ChatComposerProps) {
  const [message, setMessage] = useState("")
  const normalizedMessage = message.trim()
  const isDisabled = mode !== "active"
  const canSend = normalizedMessage.length > 0 && !isDisabled

  const submitMessage = () => {
    if (!canSend) return
    onSend(normalizedMessage)
    setMessage("")
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault()
      submitMessage()
    }
  }

  return (
    <div className="border-t border-border/80 bg-white/90 px-4 py-3 backdrop-blur md:px-6 md:py-4">
      <div className="relative rounded-[22px] border border-border bg-background shadow-[0_8px_30px_rgba(31,55,80,0.07)] transition focus-within:border-primary/30 focus-within:ring-4 focus-within:ring-ring/10">
        <Textarea
          aria-label="输入咨询内容"
          className="min-h-[76px] border-0 bg-transparent pb-9 pr-16 shadow-none focus-visible:border-0 focus-visible:ring-0"
          disabled={isDisabled}
          maxLength={MAX_MESSAGE_LENGTH}
          onChange={(event) => setMessage(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={COMPOSER_PLACEHOLDERS[mode]}
          rows={2}
          value={message}
        />
        <div className="absolute inset-x-3 bottom-2.5 flex items-center justify-between">
          <span className="hidden items-center gap-1 text-[11px] text-muted-foreground sm:flex">
            <CornerDownLeft aria-hidden="true" className="size-3" />
            Enter 发送 · Shift + Enter 换行
          </span>
          <span className="text-[11px] text-muted-foreground sm:hidden">
            {message.length}/{MAX_MESSAGE_LENGTH}
          </span>
          <Button
            aria-label="发送消息"
            className="size-9"
            disabled={!canSend}
            onClick={submitMessage}
            size="icon"
            type="button"
          >
            <ArrowUp aria-hidden="true" className="size-4" strokeWidth={2.5} />
          </Button>
        </div>
      </div>
      <p className="mt-2 text-center text-[11px] text-muted-foreground">
        AI 回复仅供参考，重要业务信息请以实际结果为准
      </p>
    </div>
  )
}
