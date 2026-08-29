import { Loader2, Search } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { SearchResult } from "@/types/kb"

type SearchPanelProps = {
  isSearching: boolean
  results: SearchResult[]
  onSearch: (query: string) => void
}

export function SearchPanel({ isSearching, results, onSearch }: SearchPanelProps) {
  const [query, setQuery] = useState("")

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (query.trim()) {
      onSearch(query)
    }
  }

  return (
    <section className="flex flex-col gap-3" aria-label="检索测试">
      <h2 className="text-sm font-semibold text-foreground">检索测试</h2>

      <form onSubmit={handleSubmit} className="flex items-center gap-2">
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="输入查询文本，例如「如何申请退款」"
          aria-label="检索查询"
          className="h-10 min-w-0 flex-1 rounded-full border border-input bg-background px-4 text-sm outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-primary/45 focus-visible:ring-4 focus-visible:ring-ring/15"
        />
        <Button type="submit" size="default" disabled={isSearching || !query.trim()}>
          {isSearching ? <Loader2 className="size-4 animate-spin" /> : <Search className="size-4" />}
          检索
        </Button>
      </form>

      {results.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {results.map((result, index) => (
            <li
              key={`${result.documentName}-${index}`}
              className={cn(
                "flex flex-col gap-2 rounded-2xl border border-border bg-card px-4 py-3",
              )}
            >
              <span className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                <span className="truncate">来源：{result.documentName}</span>
                <span className="shrink-0 font-medium text-primary">
                  相似度 {(result.score * 100).toFixed(0)}%
                </span>
              </span>
              <span className="text-sm text-foreground">{result.content}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-2xl border border-dashed border-border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          输入查询后点击「检索」查看相似分块结果。
        </p>
      )}
    </section>
  )
}
