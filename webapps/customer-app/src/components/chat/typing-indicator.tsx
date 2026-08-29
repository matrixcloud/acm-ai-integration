import { SupportAvatar } from "@/components/chat/support-avatar"

const DOTS = [0, 1, 2] as const

export function TypingIndicator() {
  return (
    <div className="flex items-end gap-2.5" role="status" aria-label="客服正在输入">
      <SupportAvatar className="size-8" />
      <div className="flex h-11 items-center gap-1 rounded-2xl rounded-bl-md border border-border/75 bg-white px-4 shadow-sm">
        {DOTS.map((dot) => (
          <span
            key={dot}
            className="size-1.5 animate-bounce rounded-full bg-muted-foreground/55"
            style={{ animationDelay: `${dot * 120}ms` }}
          />
        ))}
      </div>
    </div>
  )
}
