import type {
  BotSummary,
  CreateMatchRequest,
  CreateMatchResponse,
  CreateTournamentRequest,
  CreateTournamentResponse,
  GameFinishedEventDto,
  GameOverEventDto,
  GameStartedEventDto,
  MoveEventDto,
  ScheduleEventDto,
  TournamentFinishedEventDto,
} from '../types/api'

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

const MAX_RECONNECT_ATTEMPTS = 4
const RECONNECT_DELAY_MS = 300

/**
 * Opens an EventSource with bounded auto-retry on hard connection failures. A
 * game/tournament that finishes before the browser's SSE request lands can hit a
 * transient proxy hiccup (the dev server's proxy struggling with a rapid-fire
 * open-then-immediately-finished connection); the server always replays its full
 * event backlog on (re)connect, so a retry recovers cleanly instead of leaving
 * the viewer stuck on an empty board.
 */
function connectSse(url: string, wire: (source: EventSource) => void, onError?: (error: Event) => void): () => void {
  let source: EventSource | null = null
  let attempts = 0
  let stopped = false

  function open() {
    source = new EventSource(url)
    wire(source)
    source.addEventListener('error', (event) => {
      if (stopped || !source) return
      if (source.readyState === EventSource.CLOSED && attempts < MAX_RECONNECT_ATTEMPTS) {
        attempts++
        setTimeout(open, RECONNECT_DELAY_MS * attempts)
      } else {
        onError?.(event)
      }
    })
  }
  open()

  return () => {
    stopped = true
    source?.close()
  }
}

export interface MatchStreamHandlers {
  onMove: (event: MoveEventDto) => void
  onFinished: (event: GameOverEventDto) => void
  onError?: (error: Event) => void
}

/** Subscribes to a match's live SSE stream. Call the returned function to unsubscribe. */
export function subscribeToMatch(matchId: string, handlers: MatchStreamHandlers): () => void {
  return connectSse(
    `/api/matches/${matchId}/stream`,
    (source) => {
      source.addEventListener('move', (event) => {
        handlers.onMove(JSON.parse((event as MessageEvent).data))
      })
      source.addEventListener('finished', (event) => {
        handlers.onFinished(JSON.parse((event as MessageEvent).data))
        source.close()
      })
    },
    handlers.onError,
  )
}

export async function createTournament(request: CreateTournamentRequest): Promise<CreateTournamentResponse> {
  const res = await fetch('/api/tournaments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error(`failed to create tournament: ${res.status}`)
  return res.json()
}

export interface TournamentStreamHandlers {
  onSchedule: (event: ScheduleEventDto) => void
  onGameStarted: (event: GameStartedEventDto) => void
  onGameFinished: (event: GameFinishedEventDto) => void
  onFinished: (event: TournamentFinishedEventDto) => void
  onError?: (error: Event) => void
}

/** Subscribes to a tournament's live SSE stream. Call the returned function to unsubscribe. */
export function subscribeToTournament(tournamentId: string, handlers: TournamentStreamHandlers): () => void {
  return connectSse(
    `/api/tournaments/${tournamentId}/stream`,
    (source) => {
      source.addEventListener('schedule', (event) => {
        handlers.onSchedule(JSON.parse((event as MessageEvent).data))
      })
      source.addEventListener('game-started', (event) => {
        handlers.onGameStarted(JSON.parse((event as MessageEvent).data))
      })
      source.addEventListener('game-finished', (event) => {
        handlers.onGameFinished(JSON.parse((event as MessageEvent).data))
      })
      source.addEventListener('finished', (event) => {
        handlers.onFinished(JSON.parse((event as MessageEvent).data))
        source.close()
      })
    },
    handlers.onError,
  )
}
