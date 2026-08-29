import { Archive, Database, RotateCcw } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { KnowledgeBase } from "@/types/kb"

type KbListProps = {
  knowledgeBases: KnowledgeBase[]
  selectedKbId: string | null
  onSelect: (kbId: string) => void
  onToggleStatus: (kbId: string) => void
}

export function KbList({
  knowledgeBases,
  selectedKbId,
  onSelect,
  onToggleStatus,
}: KbListProps) {
  return (
    <ul className="flex flex-col gap-2" aria-label="知识库列表">
      {knowledgeBases.map((kb) => {
        const isActive = kb.status === "active"
        const isSelected = kb.id === selectedKbId

        return (
          <li key={kb.id}>
            <button
              type="button"
              onClick={() => onSelect(kb.id)}
              aria-pressed={isSelected}
              className={cn(
                "flex w-full items-center gap-3 rounded-2xl border border-border bg-card px-4 py-3 text-left transition-all hover:border-primary/30",
                isSelected && "border-primary/45 bg-secondary",
              )}
            >
              <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Database className="size-4" />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate text-sm font-semibold text-foreground">
                  {kb.name}
                </span>
                <span className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span>文档 {kb.docCount}</span>
                  <Badge variant={isActive ? "success" : "secondary"}>
                    {isActive ? "启用" : "停用"}
                  </Badge>
                </span>
              </span>
              <span
                role="button"
                tabIndex={0}
                aria-label={isActive ? "停用知识库" : "启用知识库"}
                onClick={(event) => {
                  event.stopPropagation()
                  onToggleStatus(kb.id)
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.stopPropagation()
                    event.preventDefault()
                    onToggleStatus(kb.id)
                  }
                }}
                className="flex size-8 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
              >
                {isActive ? <Archive className="size-4" /> : <RotateCcw className="size-4" />}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}
