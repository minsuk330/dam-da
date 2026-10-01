import { Link } from 'react-router'
import { useConversations } from '../api/conversations'
import { fidelityLabel, formatDateTime, inputPathLabel } from '../labels'

export default function ConversationListPage() {
  const { data, isPending, isError } = useConversations()

  if (isPending) return <p className="text-stone-500">불러오는 중…</p>
  if (isError) return <p className="text-red-600">대화 목록을 불러오지 못했어요.</p>

  // API는 오래된 것부터 돌려준다. 화면은 최근 것부터.
  const conversations = [...data].reverse()

  return (
    <section>
      <h1 className="mb-4 text-xl font-bold">받은 학습 대화</h1>
      {conversations.length === 0 ? (
        <p className="text-stone-500">아직 저장된 대화가 없어요. Claude에서 “복습에 넣어줘”라고 요청해 보세요.</p>
      ) : (
        <ul className="space-y-3">
          {conversations.map((c) => (
            <li key={c.id}>
              <Link
                to={`/conversations/${c.id}`}
                className="block rounded-lg border border-stone-200 bg-white p-4 hover:border-stone-400 dark:border-stone-800 dark:bg-stone-900 dark:hover:border-stone-600"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="font-medium">{c.topicHint ?? '주제 없음'}</span>
                  {c.warningCount > 0 && (
                    <span className="shrink-0 rounded bg-amber-100 px-2 py-0.5 text-xs text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">
                      경고 {c.warningCount}
                    </span>
                  )}
                </div>
                <p className="mt-1 text-sm text-stone-500">
                  {formatDateTime(c.receivedAt)} · {inputPathLabel[c.inputPath]} · {fidelityLabel[c.fidelity]}
                </p>
                <p className="mt-1 text-sm text-stone-500">
                  발화 {c.userTurnCount}개 · 복습 단위 {c.reviewUnitCount}개
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
