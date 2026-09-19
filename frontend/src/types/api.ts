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

export interface ScheduledGameDto {
  index: number
  whiteBotId: string
  blackBotId: string
}

export interface StandingDto {
  botId: string
  elo: number
  wins: number
  losses: number
  draws: number
  gamesPlayed: number
}

export interface CreateTournamentRequest {
  botIds: string[]
  gamesPerPairing?: number
  softMoveBudgetMs?: number
  maxParallelGames?: number
}

export interface CreateTournamentResponse {
  tournamentId: string
}

export interface ScheduleEventDto {
  games: ScheduledGameDto[]
  standings: StandingDto[]
}

export interface GameStartedEventDto {
  gameIndex: number
  matchId: string
  whiteBotId: string
  blackBotId: string
}

export interface GameFinishedEventDto {
  gameIndex: number
  matchId: string
  result: GameResultDto
  standings: StandingDto[]
}

export interface TournamentFinishedEventDto {
  standings: StandingDto[]
}
