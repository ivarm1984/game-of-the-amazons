<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useBotsStore } from '../stores/botsStore'
import { createMatch } from '../api/client'
import BotSelector from '../components/BotSelector.vue'

const router = useRouter()
const botsStore = useBotsStore()
const whiteBotId = ref('')
const blackBotId = ref('')
const softMoveBudgetMs = ref(3000)
const starting = ref(false)
const error = ref('')

onMounted(async () => {
  await botsStore.load()
  if (botsStore.bots.length > 0) {
    whiteBotId.value = botsStore.bots[0].id
    blackBotId.value = botsStore.bots[0].id
  }
})

async function start() {
  error.value = ''
  starting.value = true
  try {
    const { matchId } = await createMatch({
      whiteBotId: whiteBotId.value,
      blackBotId: blackBotId.value,
      softMoveBudgetMs: softMoveBudgetMs.value,
    })
    router.push(`/match/${matchId}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="setup">
    <h1>Set up a match</h1>
    <div class="pickers">
      <BotSelector v-model="whiteBotId" :bots="botsStore.bots" label="White" />
      <BotSelector v-model="blackBotId" :bots="botsStore.bots" label="Black" />
    </div>
    <label class="budget">
      Per-move time budget (ms)
      <input v-model.number="softMoveBudgetMs" type="number" min="100" step="100" />
    </label>
    <button :disabled="starting || !whiteBotId || !blackBotId" @click="start">Start match</button>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.setup {
  max-width: 500px;
  margin: 0 auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.pickers {
  display: flex;
  gap: 24px;
}
.budget {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
button {
  padding: 10px;
  font-size: 16px;
  cursor: pointer;
}
.error {
  color: #c0392b;
}
</style>
