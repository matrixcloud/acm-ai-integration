import { FileText, Loader2, Trash2, Upload } from "lucide-react"
import { useRef } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import type { KbDocument, KnowledgeBase } from "@/types/kb"

type DocumentListProps = {
  knowledgeBase: KnowledgeBase | null
  documents: KbDocument[]
  isUploading: boolean
  onUpload: (file: File) => void
  onDelete: (docId: string) => void
}

const STATUS_LABEL: Record<KbDocument["status"], string> = {
  processing: "处理中",
  ready: "已就绪",
  failed: "失败",
}

export function DocumentList({
  knowledgeBase,
  documents,
  isUploading,
  onUpload,
  onDelete,
}: DocumentListProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const canUpload = knowledgeBase?.status === "active"

  return (
    <section className="flex flex-col gap-3" aria-label="文档列表">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-foreground">
          {knowledgeBase ? knowledgeBase.name : "未选择知识库"}
          <span className="ml-2 text-xs font-normal text-muted-foreground">
            共 {documents.length} 份文档
          </span>
        </h2>
        <input
          ref={fileInputRef}
          type="file"
          accept=".txt,.md,text/plain,text/markdown"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) {
              onUpload(file)
            }
            event.target.value = ""
          }}
        />
        <Button
          type="button"
          size="sm"
          variant="coral"
          disabled={!canUpload || isUploading}
          onClick={() => fileInputRef.current?.click()}
        >
          {isUploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
          上传文档
        </Button>
      </div>

      {documents.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-border bg-card px-4 py-6 text-center text-sm text-muted-foreground">
          该知识库暂无文档，{canUpload ? "点击右上角上传文本或 Markdown 文件。" : "请先启用知识库再上传。"}
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {documents.map((doc) => (
            <li
              key={doc.id}
              className="flex items-center gap-3 rounded-2xl border border-border bg-card px-4 py-3"
            >
              <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
                <FileText className="size-4" />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="truncate text-sm font-medium text-foreground">{doc.name}</span>
                <span className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span>分块 {doc.chunkCount}</span>
                  <Badge variant={doc.status === "ready" ? "success" : "secondary"}>
                    {STATUS_LABEL[doc.status]}
                  </Badge>
                </span>
              </span>
              <Button
                type="button"
                size="icon"
                variant="ghost"
                aria-label={`删除文档 ${doc.name}`}
                onClick={() => onDelete(doc.id)}
              >
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
