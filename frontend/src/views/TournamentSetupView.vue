<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBotsStore } from '../stores/botsStore'
import { createTournament } from '../api/client'

const router = useRouter()
const botsStore = useBotsStore()
const selectedBotIds = ref<Set<string>>(new Set())
const gamesPerPairing = ref(2)
const softMoveBudgetMs = ref(3000)
const maxParallelGames = ref(4)
const starting = ref(false)
const error = ref('')

onMounted(() => botsStore.load())

function toggle(botId: string) {
  if (selectedBotIds.value.has(botId)) {
    selectedBotIds.value.delete(botId)
  } else {
    selectedBotIds.value.add(botId)
  }
  // Reassign so Vue's reactivity picks up the Set mutation.
  selectedBotIds.value = new Set(selectedBotIds.value)
}

const pairingCount = computed(() => {
  const n = selectedBotIds.value.size
  return (n * (n - 1)) / 2
})
const totalGames = computed(() => pairingCount.value * gamesPerPairing.value)
const canStart = computed(
  () => selectedBotIds.value.size >= 2 && gamesPerPairing.value >= 1 && maxParallelGames.value >= 1,
)

async function start() {
  error.value = ''
  starting.value = true
  try {
    const { tournamentId } = await createTournament({
      botIds: [...selectedBotIds.value],
      gamesPerPairing: gamesPerPairing.value,
      softMoveBudgetMs: softMoveBudgetMs.value,
      maxParallelGames: maxParallelGames.value,
    })
    router.push(`/tournament/${tournamentId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="setup">
    <h1>Set up a tournament</h1>
    <p class="hint">
      Every selected bot plays every other bot {{ gamesPerPairing }} time{{ gamesPerPairing === 1 ? '' : 's' }},
      alternating colors. Elo ratings start at 1500 and update after each game.
    </p>

    <h2>Bots ({{ selectedBotIds.size }} selected)</h2>
    <div class="bot-grid">
      <label v-for="bot in botsStore.bots" :key="bot.id" class="bot-card" :class="{ checked: selectedBotIds.has(bot.id) }">
        <input type="checkbox" :checked="selectedBotIds.has(bot.id)" @change="toggle(bot.id)" />
        <span class="bot-name">{{ bot.displayName }}</span>
        <span class="bot-difficulty">difficulty {{ bot.difficulty }}</span>
      </label>
    </div>

    <div class="options">
      <label class="option">
        Games per pairing
        <input v-model.number="gamesPerPairing" type="number" min="1" step="1" />
      </label>
      <label class="option">
        Per-move time budget (ms)
        <input v-model.number="softMoveBudgetMs" type="number" min="100" step="100" />
      </label>
      <label class="option">
        Games in parallel
        <input v-model.number="maxParallelGames" type="number" min="1" step="1" />
      </label>
    </div>

    <p class="summary">{{ pairingCount }} pairing{{ pairingCount === 1 ? '' : 's' }} — {{ totalGames }} game{{ totalGames === 1 ? '' : 's' }} total</p>

    <button :disabled="starting || !canStart" @click="start">Start tournament</button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.setup {
  max-width: 700px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.hint {
  color: var(--text);
}
.bot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
}
.bot-card {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
}
.bot-card input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.bot-card.checked {
  border-color: #2a78d6;
  background: rgba(42, 120, 214, 0.08);
}
.bot-name {
  font-weight: 600;
  color: var(--text-h);
}
.bot-difficulty {
  font-size: 12px;
  color: var(--text);
}
.options {
  display: flex;
  gap: 24px;
  margin-top: 8px;
}
.option {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.option input {
  padding: 6px;
  font-size: 14px;
}
.summary {
  font-weight: 600;
  color: var(--text-h);
}
button {
  align-self: flex-start;
  padding: 10px 18px;
  font-size: 16px;
  cursor: pointer;
}
.error {
  color: #c0392b;
}
</style>
