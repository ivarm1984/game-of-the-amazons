<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useTournamentStore } from '../stores/tournamentStore'
import { useMatchStore } from '../stores/matchStore'
import { useBotsStore } from '../stores/botsStore'
import AmazonsBoard from '../components/AmazonsBoard.vue'
import MoveList from '../components/MoveList.vue'
import EloChart from '../components/EloChart.vue'
import { colorMapFor } from '../utils/botColors'

const route = useRoute()
const tournamentStore = useTournamentStore()
const matchStore = useMatchStore()
const botsStore = useBotsStore()

onMounted(async () => {
  await botsStore.load()
  tournamentStore.connect(route.params.id as string)
})
onUnmounted(() => {
  tournamentStore.disconnect()
  matchStore.disconnect()
})

const botNames = computed<Record<string, string>>(() => {
  const map: Record<string, string> = {}
  for (const bot of botsStore.bots) map[bot.id] = bot.displayName
  return map
})
function nameFor(botId: string | null): string {
  if (!botId) return ''
  return botNames.value[botId] ?? botId
}

// Same bot -> color mapping as EloChart, sharing its key order (tournamentStore.eloHistory) so a
// bot's swatch here always matches its line color in the chart below.
const botColors = computed(() => colorMapFor(Object.keys(tournamentStore.eloHistory)))
function colorFor(botId: string | null): string | undefined {
  return botId ? botColors.value[botId] : undefined
}

const gamesDone = computed(() => tournamentStore.finishedGames.length)

function resultLabel(game: (typeof tournamentStore.finishedGames)[number]): string {
  const winnerId = game.result.winner === 'WHITE' ? game.whiteBotId : game.result.winner === 'BLACK' ? game.blackBotId : null
  if (!winnerId) return 'draw'
  return `${nameFor(winnerId)} won`
}

// Banner shown over the board once the current game's result is in, naming the current game's
// winner by color and bot name (e.g. "White (Territory Bot) won") rather than just the color the
// below-board result line already gives.
const currentResultLabel = computed(() => {
  const result = matchStore.result
  if (!result) return ''
  if (!result.winner) return 'Draw'
  const winnerId = result.winner === 'WHITE' ? tournamentStore.currentWhiteBotId : tournamentStore.currentBlackBotId
  const colorLabel = result.winner === 'WHITE' ? 'White' : 'Black'
  return `${colorLabel} (${nameFor(winnerId)}) won`
})

const nextGameLabel = computed(() => {
  if (tournamentStore.status === 'finished' && !tournamentStore.nextGameReady) return 'Tournament complete'
  if (tournamentStore.autoplay) return 'Autoplaying…'
  if (matchStore.status !== 'finished') return 'Next game'
  if (tournamentStore.nextGameReady) return 'Next game →'
  return 'Waiting for next game…'
})
const nextGameDisabled = computed(
  () => tournamentStore.autoplay || matchStore.status !== 'finished' || !tournamentStore.nextGameReady,
)
function onAutoplayChange(event: Event) {
  tournamentStore.setAutoplay((event.target as HTMLInputElement).checked)
}
function onShowAnimationsChange(event: Event) {
  matchStore.setSpeed((event.target as HTMLInputElement).checked ? 'spectate' : 'fast')
}
</script>

<template>
  <div class="tournament">
    <h1>Tournament {{ route.params.id }}</h1>
    <p class="status">
      Status: {{ tournamentStore.status }}
      <template v-if="tournamentStore.totalGames > 0">
        — game {{ Math.min(gamesDone + 1, tournamentStore.totalGames) }} of {{ tournamentStore.totalGames }}
      </template>
    </p>

    <div class="layout">
      <div class="board-column">
        <p v-if="tournamentStore.currentWhiteBotId" class="matchup">
          <span class="bot-swatch" :style="{ background: colorFor(tournamentStore.currentWhiteBotId) }"></span>
          <strong>{{ nameFor(tournamentStore.currentWhiteBotId) }}</strong> (white) vs
          <span class="bot-swatch" :style="{ background: colorFor(tournamentStore.currentBlackBotId) }"></span>
          <strong>{{ nameFor(tournamentStore.currentBlackBotId) }}</strong> (black)
        </p>
        <div class="board-wrap">
          <AmazonsBoard :board="matchStore.board" :last-move="matchStore.lastMove" :anim="matchStore.anim" />
          <div v-if="matchStore.result" class="result-overlay">
            <p class="result-overlay-text">{{ currentResultLabel }}</p>
          </div>
        </div>
        <p v-if="matchStore.result" class="match-result">
          {{ matchStore.result.winner ? `${matchStore.result.winner} wins` : 'Draw' }}
          ({{ matchStore.result.reason }}, {{ matchStore.result.totalPlies }} plies)
        </p>
        <div class="next-game">
          <button type="button" :disabled="nextGameDisabled" @click="tournamentStore.advanceToNextGame()">
            {{ nextGameLabel }}
          </button>
          <label class="autoplay-toggle">
            <input type="checkbox" :checked="tournamentStore.autoplay" @change="onAutoplayChange" />
            Autoplay next game
          </label>
          <label class="autoplay-toggle">
            <input
              type="checkbox"
              :checked="matchStore.speed === 'spectate'"
              @change="onShowAnimationsChange"
            />
            Show animations
          </label>
        </div>
        <MoveList :moves="matchStore.moves" />
      </div>

      <div class="side-column">
        <section>
          <h2>Standings</h2>
          <table class="standings">
            <thead>
              <tr>
                <th>#</th>
                <th>Bot</th>
                <th>Elo</th>
                <th>W</th>
                <th>L</th>
                <th>D</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(s, i) in tournamentStore.rankedStandings"
                :key="s.botId"
                :class="{ current: s.botId === tournamentStore.currentWhiteBotId || s.botId === tournamentStore.currentBlackBotId }"
              >
                <td>{{ i + 1 }}</td>
                <td>{{ nameFor(s.botId) }}</td>
                <td>{{ Math.round(s.elo) }}</td>
                <td>{{ s.wins }}</td>
                <td>{{ s.losses }}</td>
                <td>{{ s.draws }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section>
          <h2>Elo over the tournament</h2>
          <EloChart :history="tournamentStore.eloHistory" :bot-names="botNames" />
        </section>

        <section>
          <h2>Results</h2>
          <div class="results-list">
            <div v-for="game in [...tournamentStore.finishedGames].reverse()" :key="game.gameIndex" class="result-row">
              <span class="game-index">{{ game.gameIndex + 1 }}.</span>
              <span>{{ nameFor(game.whiteBotId) }} vs {{ nameFor(game.blackBotId) }}</span>
              <span class="outcome">{{ resultLabel(game) }}</span>
            </div>
            <p v-if="tournamentStore.finishedGames.length === 0" class="empty">No games finished yet.</p>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tournament {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}
.tournament h1 {
  font-size: 18px;
  word-break: break-all;
  margin-bottom: 8px;
}
.status {
  font-weight: bold;
  margin-bottom: 16px;
}
.layout {
  display: flex;
  gap: 32px;
  align-items: flex-start;
}
.board-column {
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* Without this, flex's default cross-axis stretch widens every child - including the board,
     which is display:inline-block and gets blockified as a flex item - to match the widest
     sibling. A long matchup line (long bot names) is often wider than the 400px board, which
     stretched the board's box to match and left a blank gap to the right of the actual grid. */
  align-items: flex-start;
}
.matchup {
  color: var(--text);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.bot-swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex: none;
}
.board-wrap {
  position: relative;
  display: inline-block;
  line-height: 0;
}
.result-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  animation: result-overlay-in 200ms ease-out;
}
.result-overlay-text {
  margin: 0;
  padding: 10px 20px;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  text-align: center;
}
@keyframes result-overlay-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.match-result {
  margin: 0;
  font-weight: 600;
  color: var(--text-h);
}
.next-game {
  display: flex;
  align-items: center;
  gap: 14px;
}
.next-game button {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--accent, #2a78d6);
  color: #fff;
  cursor: pointer;
}
.next-game button:disabled {
  background: var(--border);
  color: var(--text);
  cursor: default;
}
.autoplay-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-h);
  cursor: pointer;
}
.side-column {
  flex: 1;
  min-width: 320px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}
h2 {
  font-size: 15px;
}
.standings {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.standings th,
.standings td {
  text-align: left;
  padding: 6px 8px;
  border-bottom: 1px solid var(--border);
}
.standings tr.current {
  background: rgba(42, 120, 214, 0.1);
}
.results-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 260px;
  overflow-y: auto;
  font-size: 13px;
}
.result-row {
  display: flex;
  gap: 8px;
  padding: 4px 6px;
  border-bottom: 1px solid var(--border);
}
.game-index {
  color: var(--text);
  width: 24px;
  flex: none;
}
.outcome {
  margin-left: auto;
  color: var(--text-h);
  font-weight: 600;
}
.empty {
  color: var(--text);
  font-size: 13px;
}
</style>
