import { useParams } from 'react-router'
import { ApiError, type Schemas } from '../api/client'
import { useConversation } from '../api/conversations'
import { aiVerdictLabel, factKindLabel, fidelityLabel, formatDateTime, inputPathLabel, intentLabel } from '../labels'

type Turn = Schemas['UserTurnResponse']
type ReviewUnit = Schemas['ReviewUnitResponse']

const card = 'rounded-lg border border-stone-200 bg-white p-4 dark:border-stone-800 dark:bg-stone-900'
const badge = 'rounded px-2 py-0.5 text-xs'

export default function ConversationDetailPage() {
  const { id = '' } = useParams()
  const { data, isPending, error } = useConversation(id)

  if (isPending) return <p className="text-stone-500">불러오는 중…</p>
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404
    return <p className="text-red-600">{notFound ? '대화를 찾을 수 없어요.' : '대화를 불러오지 못했어요.'}</p>
  }

  const turnsByIndex = new Map(data.userTurns.map((t) => [t.index, t]))

  return (
    <article className="space-y-6">
      <header>
        <h1 className="text-xl font-bold">{data.topicHint ?? '주제 없음'}</h1>
        <p className="mt-1 text-sm text-stone-500">
          {formatDateTime(data.receivedAt)} · {inputPathLabel[data.inputPath]} · {fidelityLabel[data.fidelity]}
        </p>
      </header>

      {data.fidelity === 'model_transcribed' && (
        <p className="rounded-lg bg-sky-50 p-3 text-sm text-sky-900 dark:bg-sky-950/50 dark:text-sky-200">
          커넥터로 받은 발화는 모델이 옮겨 적은 것이라 실제로 입력한 문장과 다를 수 있어요. 내용이 맞는지 확인해 주세요.
        </p>
      )}

      {data.warnings.length > 0 && (
        <section className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-200">
          <h2 className="font-semibold">저장 시 경고</h2>
          <ul className="mt-1 list-disc pl-5">
            {data.warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </section>
      )}

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">복습 단위 {data.reviewUnits.length}개</h2>
        {data.reviewUnits.map((unit) => (
          <ReviewUnitCard key={unit.title} unit={unit} turnsByIndex={turnsByIndex} />
        ))}
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold">받은 발화 {data.userTurns.length}개</h2>
        {data.userTurns.map((turn) => (
          <TurnCard key={turn.index} turn={turn} />
        ))}
      </section>
    </article>
  )
}

function TurnRef({ index }: { index: number }) {
  return (
    <a href={`#turn-${index}`} className="mr-1 text-xs text-stone-500 underline-offset-2 hover:underline">
      #{index}
    </a>
  )
}

function ReviewUnitCard({ unit, turnsByIndex }: { unit: ReviewUnit; turnsByIndex: Map<number, Turn> }) {
  return (
    <div className={card}>
      <h3 className="font-semibold">{unit.title}</h3>
      <ul className="mt-2 space-y-2">
        {unit.keyPoints.map((p) => (
          <li key={p.point} className="text-sm">
            {factKindLabel[p.kind] && (
              <span className={`${badge} mr-1.5 bg-stone-100 dark:bg-stone-800`}>{factKindLabel[p.kind]}</span>
            )}
            {p.point}{' '}
            {p.turns.map((t) => (
              <TurnRef key={t} index={t} />
            ))}
          </li>
        ))}
      </ul>
      {unit.confusionPoints.length > 0 && (
        <div className="mt-3 space-y-2 border-t border-stone-200 pt-3 dark:border-stone-800">
          {unit.confusionPoints.map((c) => {
            const correction = turnsByIndex.get(c.turn)?.correction
            return (
              <div key={c.turn} className="text-sm">
                <p>
                  <span className="font-medium text-rose-700 dark:text-rose-400">헷갈린 점</span> {c.userBelief}{' '}
                  <TurnRef index={c.turn} />
                </p>
                {correction && <p className="mt-0.5 text-stone-600 dark:text-stone-400">대화 중 교정: {correction}</p>}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function TurnCard({ turn }: { turn: Turn }) {
  const verdict = aiVerdictLabel[turn.aiVerdict]
  const isMeta = turn.intent === 'meta'
  return (
    <div id={`turn-${turn.index}`} className={`${card} scroll-mt-4 ${isMeta ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-center gap-1.5 text-xs">
        <span className="text-stone-500">#{turn.index}</span>
        <span className={`${badge} bg-stone-100 dark:bg-stone-800`}>{intentLabel[turn.intent]}</span>
        {verdict && (
          <span className={`${badge} bg-violet-100 text-violet-800 dark:bg-violet-900/40 dark:text-violet-300`}>
            대화 중 AI 판정: {verdict}
          </span>
        )}
        {isMeta && <span className="text-stone-500">복습 근거로 쓰지 않음</span>}
      </div>
      {turn.quotedText && (
        <blockquote className="mt-2 border-l-2 border-stone-300 pl-3 text-sm text-stone-500 dark:border-stone-700">
          {turn.quotedText}
        </blockquote>
      )}
      <p className="mt-2 whitespace-pre-wrap">{turn.text}</p>
      {turn.correction && (
        <p className="mt-2 text-sm text-stone-600 dark:text-stone-400">대화 중 교정: {turn.correction}</p>
      )}
    </div>
  )
}
