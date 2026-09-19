import { defineStore } from 'pinia'
import { subscribeToMatch } from '../api/client'
import type { MoveAnimation } from '../components/AmazonsBoard.vue'
import type { BoardDto, GameOverEventDto, GameResultDto, MoveEventDto } from '../types/api'

export type PlaybackSpeed = 'spectate' | 'fast'

export interface PlaybackTiming {
  queenMs: number
  shotMs: number
  settleMs: number
}

const SPEED_STORAGE_KEY = 'amazons.playbackSpeed'
const DEFAULT_TIMING: PlaybackTiming = {
  queenMs: 550,
  shotMs: 550,
  // The animation itself already paces each move, so the pause between moves just
  // needs to keep the settled position readable for a beat - not a full extra step.
  settleMs: 120,
}

type QueueItem = { type: 'move'; event: MoveEventDto } | { type: 'finished'; event: GameOverEventDto }

export interface ConnectOptions {
  /** Fired once playback has actually caught up to the game-over state - see MatchState.onPlaybackFinished. */
  onPlaybackFinished?: () => void
  /** Overrides the default per-phase animation timing (e.g. tournament spectating runs faster with no inter-move pause). */
  timing?: Partial<PlaybackTiming>
}

function loadStoredSpeed(): PlaybackSpeed {
  try {
    const stored = localStorage.getItem(SPEED_STORAGE_KEY)
    return stored === 'fast' ? 'fast' : 'spectate'
  } catch {
    return 'spectate'
  }
}

function parseBoardPos(pos: string): { row: number; col: number } {
  const match = /^([a-j])(10|[1-9])$/i.exec(pos)
  const col = match![1].toLowerCase().charCodeAt(0) - 'a'.charCodeAt(0)
  const row = Number(match![2]) - 1
  return { row, col }
}

function withCell(board: BoardDto, row: number, col: number, ch: string): BoardDto {
  const rows = board.rows.slice()
  const chars = rows[row].split('')
  chars[col] = ch
  rows[row] = chars.join('')
  return { rows }
}

function moverChar(mover: 'WHITE' | 'BLACK'): string {
  return mover === 'WHITE' ? 'W' : 'B'
}

interface MatchState {
  matchId: string | null
  board: BoardDto | null
  moves: MoveEventDto[]
  status: 'idle' | 'connecting' | 'in_progress' | 'finished'
  result: GameResultDto | null
  unsubscribe: (() => void) | null
  speed: PlaybackSpeed
  queue: QueueItem[]
  playbackTimer: number | null
  anim: MoveAnimation | null
  /** The move currently animating, kept so switching to Fast mid-animation can resolve it instantly. */
  inFlightEvent: MoveEventDto | null
  /** Highest ply already applied or queued, so a reconnect's full backlog replay (see client.ts's connectSse) doesn't double-apply moves. */
  lastSeenPly: number
  /** Fired once playback has actually caught up to the game-over state (not just when the SSE 'finished' event arrives), so a caller pacing several matches back-to-back - e.g. tournament spectating - can wait for the animation to finish instead of cutting it off. */
  onPlaybackFinished: (() => void) | null
  timing: PlaybackTiming
}

export const useMatchStore = defineStore('match', {
  state: (): MatchState => ({
    matchId: null,
    board: null,
    moves: [],
    status: 'idle',
    result: null,
    unsubscribe: null,
    speed: loadStoredSpeed(),
    queue: [],
    playbackTimer: null,
    anim: null,
    inFlightEvent: null,
    lastSeenPly: 0,
    onPlaybackFinished: null,
    timing: { ...DEFAULT_TIMING },
  }),
  getters: {
    /** Moves received but not yet shown on the board, so the UI can convey playback lag. */
    pendingMoveCount: (state) => state.queue.filter((item) => item.type === 'move').length,
    lastMove: (state) => state.moves[state.moves.length - 1]?.move ?? null,
  },
  actions: {
    connect(matchId: string, options?: ConnectOptions) {
      this.disconnect()
      this.matchId = matchId
      this.board = null
      this.moves = []
      this.result = null
      this.queue = []
      this.status = 'connecting'
      this.lastSeenPly = 0
      this.onPlaybackFinished = options?.onPlaybackFinished ?? null
      this.timing = { ...DEFAULT_TIMING, ...options?.timing }

      this.unsubscribe = subscribeToMatch(matchId, {
        onMove: (event) => {
          // A dropped-and-retried SSE connection replays the whole backlog from
          // ply 1, so skip anything already applied or queued.
          if (event.ply <= this.lastSeenPly) return
          this.lastSeenPly = event.ply
          this.status = 'in_progress'
          this.enqueue({ type: 'move', event })
        },
        onFinished: (event) => {
          this.enqueue({ type: 'finished', event })
        },
      })
    },
    disconnect() {
      this.unsubscribe?.()
      this.unsubscribe = null
      if (this.playbackTimer !== null) {
        clearTimeout(this.playbackTimer)
        this.playbackTimer = null
      }
      this.queue = []
      this.anim = null
      this.inFlightEvent = null
      this.onPlaybackFinished = null
    },
    setSpeed(speed: PlaybackSpeed) {
      this.speed = speed
      try {
        localStorage.setItem(SPEED_STORAGE_KEY, speed)
      } catch {
        // ignore storage errors (private browsing, quota, etc.)
      }
      // Jumping to Fast mid-animation resolves the in-flight move instantly
      // instead of waiting out its remaining phases.
      if (speed === 'fast' && this.playbackTimer !== null && this.inFlightEvent) {
        clearTimeout(this.playbackTimer)
        this.playbackTimer = null
        this.board = this.inFlightEvent.board
        this.moves.push(this.inFlightEvent)
        this.anim = null
        this.inFlightEvent = null
        this.ensureTicking()
      }
    },
    enqueue(item: QueueItem) {
      this.queue.push(item)
      this.ensureTicking()
    },
    ensureTicking() {
      if (this.playbackTimer !== null) return
      const delay = this.speed === 'fast' ? 0 : this.timing.settleMs
      this.playbackTimer = window.setTimeout(() => this.tick(), delay)
    },
    tick() {
      this.playbackTimer = null
      const item = this.queue.shift()
      if (!item) return
      if (item.type === 'finished') {
        this.board = item.event.finalBoard
        this.result = item.event.result
        this.status = 'finished'
        this.anim = null
        if (this.queue.length > 0) {
          this.ensureTicking()
          return
        }
        const onPlaybackFinished = this.onPlaybackFinished
        this.onPlaybackFinished = null
        onPlaybackFinished?.()
        return
      }
      this.playMove(item.event)
    },
    playMove(event: MoveEventDto) {
      // No prior board to animate a departure from (the very first move) or
      // the viewer wants to catch up: apply it in one step.
      if (this.speed === 'fast' || !this.board) {
        this.board = event.board
        this.moves.push(event)
        this.anim = null
        if (this.queue.length > 0) this.ensureTicking()
        return
      }

      this.inFlightEvent = event
      const from = parseBoardPos(event.move.from)
      const to = parseBoardPos(event.move.to)
      const { queenMs, shotMs, settleMs } = this.timing

      let midBoard = withCell(this.board, from.row, from.col, '.')
      midBoard = withCell(midBoard, to.row, to.col, '.')
      this.board = midBoard
      this.anim = { phase: 'queen', move: event.move, mover: event.mover, durationMs: queenMs }

      this.playbackTimer = window.setTimeout(() => {
        this.board = withCell(midBoard, to.row, to.col, moverChar(event.mover))
        this.anim = { phase: 'shot', move: event.move, mover: event.mover, durationMs: shotMs }

        this.playbackTimer = window.setTimeout(() => {
          this.board = event.board
          this.anim = null
          this.inFlightEvent = null
          this.moves.push(event)

          this.playbackTimer = window.setTimeout(() => {
            this.playbackTimer = null
            if (this.queue.length > 0) this.ensureTicking()
          }, settleMs)
        }, shotMs)
      }, queenMs)
    },
  },
})
