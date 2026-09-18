import type { BotSummary, CreateMatchRequest, CreateMatchResponse, GameOverEventDto, MoveEventDto } from '../types/api'

export async function fetchBots(): Promise<BotSummary[]> {
  const res = await fetch('/api/bots')
  if (!res.ok) throw new Error(`failed to load bots: ${res.status}`)
  return res.json()
}

export async function createMatch(request: CreateMatchRequest): Promise<CreateMatchResponse> {
  const res = await fetch('/api/matches', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error(`failed to create match: ${res.status}`)
  return res.json()
}

export interface MatchStreamHandlers {
  onMove: (event: MoveEventDto) => void
  onFinished: (event: GameOverEventDto) => void
  onError?: (error: Event) => void
}

/** Subscribes to a match's live SSE stream. Call the returned function to unsubscribe. */
export function subscribeToMatch(matchId: string, handlers: MatchStreamHandlers): () => void {
  const source = new EventSource(`/api/matches/${matchId}/stream`)

  source.addEventListener('move', (event) => {
    handlers.onMove(JSON.parse((event as MessageEvent).data))
  })
  source.addEventListener('finished', (event) => {
    handlers.onFinished(JSON.parse((event as MessageEvent).data))
    source.close()
  })
  if (handlers.onError) {
    source.addEventListener('error', handlers.onError)
  }

  return () => source.close()
}
