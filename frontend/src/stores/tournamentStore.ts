import { defineStore } from 'pinia'
import { subscribeToTournament } from '../api/client'
import { useMatchStore } from './matchStore'
import type { PlaybackTiming } from './matchStore'
import type { GameFinishedEventDto, GameResultDto, GameStartedEventDto, ScheduledGameDto, StandingDto } from '../types/api'

export interface EloPoint {
  gameIndex: number
  elo: number
}

export interface FinishedGame {
  gameIndex: number
  whiteBotId: string
  blackBotId: string
  result: GameResultDto
}

/**
 * The backend runs the whole round-robin as fast as the bots can move - a game can finish in
 * milliseconds - so spectating plays back noticeably faster than a standalone match view, and
 * skips the inter-move settle pause entirely so games flow into each other with no dead time.
 */
const SPECTATE_TIMING: Partial<PlaybackTiming> = {
  queenMs: 260,
  shotMs: 260,
  settleMs: 0,
}

const AUTOPLAY_STORAGE_KEY = 'amazons.tournamentAutoplay'

function loadStoredAutoplay(): boolean {
  try {
    return localStorage.getItem(AUTOPLAY_STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

interface TournamentState {
  tournamentId: string | null
  status: 'idle' | 'connecting' | 'running' | 'finished'
  schedule: ScheduledGameDto[]
  standings: StandingDto[]
  eloHistory: Record<string, EloPoint[]>
  finishedGames: FinishedGame[]
  currentGameIndex: number | null
  currentMatchId: string | null
  currentWhiteBotId: string | null
  currentBlackBotId: string | null
  unsubscribe: (() => void) | null
  /** gameIndex values already applied, so a reconnect's full backlog replay (see client.ts's connectSse) doesn't double-count games. */
  processedGameIndices: Set<number>
  /**
   * Games the server has already started, waiting to be spectated. The backend runs the whole
   * round-robin as fast as the bots can move - a game can finish in milliseconds - so
   * 'game-started' events arrive far faster than a human can watch a game play out. Without this
   * queue, spectating the next game would tear down the current match's animation mid-playback,
   * which looks like the game "ending abruptly" after only a couple of moves. Instead, games are
   * spectated one at a time, only advancing once matchStore reports the current one's playback
   * has actually finished.
   */
  spectateQueue: GameStartedEventDto[]
  spectating: boolean
  /**
   * True once the very first game has started spectating. Only that first game auto-plays; every
   * game after that waits for an explicit "Next game" click (see advanceToNextGame) so a viewer who
   * is skeptical a game really reached a legitimate end can look at the frozen final board - and the
   * result line - for as long as they want before moving on, rather than it being auto-advanced away.
   */
  hasStartedSpectating: boolean
  /** Persisted user preference: when true, the next queued game is spectated automatically as soon as it's ready, instead of waiting for the "Next game" button. */
  autoplay: boolean
  /**
   * Results ('game-finished') received from the server ahead of when they're spectated, keyed by
   * gameIndex - the same race that motivates spectateQueue. Applying these to standings/Elo/results
   * as soon as they arrive would spoil (and visually jump) the chart and standings table while an
   * earlier game is still animating, so they're held here until that game's spectate playback
   * catches up to it.
   */
  pendingResults: Map<number, GameFinishedEventDto>
  /** Set once the currently spectated game's playback finishes, so its result (once available) is applied and the queue advances - see tryFinalize. */
  awaitingResultGameIndex: number | null
  /** Final standings from the 'finished' tournament event, held back until every queued game has finished spectating. */
  pendingFinalStandings: StandingDto[] | null
}

export const useTournamentStore = defineStore('tournament', {
  state: (): TournamentState => ({
    tournamentId: null,
    status: 'idle',
    schedule: [],
    standings: [],
    eloHistory: {},
    finishedGames: [],
    currentGameIndex: null,
    currentMatchId: null,
    currentWhiteBotId: null,
    currentBlackBotId: null,
    unsubscribe: null,
    processedGameIndices: new Set(),
    spectateQueue: [],
    spectating: false,
    hasStartedSpectating: false,
    autoplay: loadStoredAutoplay(),
    pendingResults: new Map(),
    awaitingResultGameIndex: null,
    pendingFinalStandings: null,
  }),
  getters: {
    totalGames: (state) => state.schedule.length,
    rankedStandings: (state) => [...state.standings].sort((a, b) => b.elo - a.elo),
    /** Whether the current game has finished spectating and there's a queued game ready to jump to. */
    nextGameReady: (state) => !state.spectating && state.spectateQueue.length > 0,
  },
  actions: {
    connect(tournamentId: string) {
      this.disconnect()
      this.tournamentId = tournamentId
      this.status = 'connecting'
      this.schedule = []
      this.standings = []
      this.eloHistory = {}
      this.finishedGames = []
      this.currentGameIndex = null
      this.currentMatchId = null
      this.processedGameIndices = new Set()
      this.spectateQueue = []
      this.spectating = false
      this.hasStartedSpectating = false
      this.pendingResults = new Map()
      this.awaitingResultGameIndex = null
      this.pendingFinalStandings = null

      this.unsubscribe = subscribeToTournament(tournamentId, {
        onSchedule: (event) => {
          if (this.schedule.length > 0) return
          this.status = 'running'
          this.schedule = event.games
          this.standings = event.standings
          this.recordEloPoint(0, event.standings)
        },
        onGameStarted: (event) => {
          this.spectateQueue.push(event)
          // The very first game always auto-plays (nothing to click "next" from yet); after that,
          // only advance automatically if the autoplay preference is on.
          if (!this.hasStartedSpectating || this.autoplay) this.advanceSpectateQueue()
        },
        onGameFinished: (event: GameFinishedEventDto) => {
          if (this.processedGameIndices.has(event.gameIndex)) return
          this.pendingResults.set(event.gameIndex, event)
          this.tryFinalize(event.gameIndex)
        },
        onFinished: (event) => {
          this.pendingFinalStandings = event.standings
          this.tryFinishTournament()
        },
      })
    },
    disconnect() {
      this.unsubscribe?.()
      this.unsubscribe = null
      this.spectateQueue = []
      this.spectating = false
      this.hasStartedSpectating = false
      this.pendingResults = new Map()
      this.awaitingResultGameIndex = null
      this.pendingFinalStandings = null
    },
    /** Called by the "Next game" button. A no-op if a game is already spectating or none is queued yet. */
    advanceToNextGame() {
      this.advanceSpectateQueue()
    },
    setAutoplay(value: boolean) {
      this.autoplay = value
      try {
        localStorage.setItem(AUTOPLAY_STORAGE_KEY, String(value))
      } catch {
        // ignore storage errors (private browsing, quota, etc.)
      }
      // Turning it on shouldn't leave a ready game sitting there waiting for a click that will never come.
      if (value) this.advanceSpectateQueue()
    },
    advanceSpectateQueue() {
      if (this.spectating) return
      const next = this.spectateQueue.shift()
      if (!next) return
      this.spectating = true
      this.hasStartedSpectating = true
      this.currentGameIndex = next.gameIndex
      this.currentMatchId = next.matchId
      this.currentWhiteBotId = next.whiteBotId
      this.currentBlackBotId = next.blackBotId
      const matchStore = useMatchStore()
      matchStore.connect(next.matchId, {
        timing: SPECTATE_TIMING,
        onPlaybackFinished: () => {
          this.awaitingResultGameIndex = next.gameIndex
          this.tryFinalize(next.gameIndex)
        },
      })
    },
    /** Applies a game's result once both its spectate playback has finished AND its result has arrived - whichever comes last. */
    tryFinalize(gameIndex: number) {
      if (this.awaitingResultGameIndex !== gameIndex) return
      const result = this.pendingResults.get(gameIndex)
      if (!result) return
      this.pendingResults.delete(gameIndex)
      this.awaitingResultGameIndex = null

      if (!this.processedGameIndices.has(gameIndex)) {
        this.processedGameIndices.add(gameIndex)
        this.standings = result.standings
        this.recordEloPoint(gameIndex + 1, result.standings)
        const game = this.schedule[gameIndex]
        if (game) {
          this.finishedGames.push({
            gameIndex,
            whiteBotId: game.whiteBotId,
            blackBotId: game.blackBotId,
            result: result.result,
          })
        }
      }

      this.spectating = false
      if (this.autoplay) this.advanceSpectateQueue()
      this.tryFinishTournament()
    },
    tryFinishTournament() {
      if (this.pendingFinalStandings && this.spectateQueue.length === 0 && !this.spectating) {
        this.standings = this.pendingFinalStandings
        this.pendingFinalStandings = null
        this.status = 'finished'
      }
    },
    recordEloPoint(gameIndex: number, standings: StandingDto[]) {
      for (const standing of standings) {
        const history = this.eloHistory[standing.botId] ?? (this.eloHistory[standing.botId] = [])
        history.push({ gameIndex, elo: standing.elo })
      }
    },
  },
})