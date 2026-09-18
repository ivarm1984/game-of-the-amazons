export interface BotSummary {
  id: string
  displayName: string
  difficulty: number
  description: string
}

export interface CreateMatchRequest {
  whiteBotId: string
  blackBotId: string
  softMoveBudgetMs?: number
}

export interface CreateMatchResponse {
  matchId: string
}

export interface MoveDto {
  from: string
  to: string
  arrow: string
}

export interface BoardDto {
  rows: string[]
}

export interface MoveEventDto {
  ply: number
  mover: 'WHITE' | 'BLACK'
  move: MoveDto
  board: BoardDto
  nextToMove: 'WHITE' | 'BLACK'
}

export interface GameResultDto {
  status: 'IN_PROGRESS' | 'WHITE_WINS' | 'BLACK_WINS' | 'DRAW'
  winner: 'WHITE' | 'BLACK' | null
  reason: string
  totalPlies: number
}

export interface GameOverEventDto {
  result: GameResultDto
  finalBoard: BoardDto
}
