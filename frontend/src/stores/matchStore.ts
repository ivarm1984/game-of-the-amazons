import { defineStore } from 'pinia'
import { subscribeToMatch } from '../api/client'
import type { BoardDto, GameResultDto, MoveEventDto } from '../types/api'

interface MatchState {
  matchId: string | null
  board: BoardDto | null
  moves: MoveEventDto[]
  status: 'idle' | 'connecting' | 'in_progress' | 'finished'
  result: GameResultDto | null
  unsubscribe: (() => void) | null
}

export const useMatchStore = defineStore('match', {
  state: (): MatchState => ({
    matchId: null,
    board: null,
    moves: [],
    status: 'idle',
    result: null,
    unsubscribe: null,
  }),
  actions: {
    connect(matchId: string) {
      this.disconnect()
      this.matchId = matchId
      this.board = null
      this.moves = []
      this.result = null
      this.status = 'connecting'

      this.unsubscribe = subscribeToMatch(matchId, {
        onMove: (event) => {
          this.status = 'in_progress'
          this.board = event.board
          this.moves.push(event)
        },
        onFinished: (event) => {
          this.status = 'finished'
          this.board = event.finalBoard
          this.result = event.result
        },
      })
    },
    disconnect() {
      this.unsubscribe?.()
      this.unsubscribe = null
    },
  },
})
