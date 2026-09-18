<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useMatchStore } from '../stores/matchStore'
import AmazonsBoard from '../components/AmazonsBoard.vue'
import MoveList from '../components/MoveList.vue'

const route = useRoute()
const matchStore = useMatchStore()

onMounted(() => {
  matchStore.connect(route.params.id as string)
})
onUnmounted(() => {
  matchStore.disconnect()
})
</script>

<template>
  <div class="viewer">
    <h1>Match {{ route.params.id }}</h1>
    <div class="toolbar">
      <p class="status">
        Status: {{ matchStore.status }}
        <template v-if="matchStore.result">
          — {{ matchStore.result.winner }} wins ({{ matchStore.result.reason }}, {{ matchStore.result.totalPlies }} plies)
        </template>
        <template v-else-if="matchStore.pendingMoveCount > 0">
          — {{ matchStore.pendingMoveCount }} move{{ matchStore.pendingMoveCount === 1 ? '' : 's' }} behind
        </template>
      </p>
      <div class="speed-toggle">
        <button
          type="button"
          :class="{ active: matchStore.speed === 'spectate' }"
          @click="matchStore.setSpeed('spectate')"
        >
          Spectate
        </button>
        <button
          type="button"
          :class="{ active: matchStore.speed === 'fast' }"
          @click="matchStore.setSpeed('fast')"
        >
          Fast
        </button>
      </div>
    </div>
    <div class="layout">
      <AmazonsBoard :board="matchStore.board" :last-move="matchStore.lastMove" :anim="matchStore.anim" />
      <MoveList :moves="matchStore.moves" />
    </div>
  </div>
</template>

<style scoped>
.viewer {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}
.viewer h1 {
  font-size: 18px;
  word-break: break-all;
  margin-bottom: 8px;
}
.layout {
  display: flex;
  gap: 32px;
  margin-top: 16px;
  align-items: flex-start;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.status {
  font-weight: bold;
}
.speed-toggle {
  display: inline-flex;
  border: 1px solid #ccc;
  border-radius: 6px;
  overflow: hidden;
}
.speed-toggle button {
  border: none;
  background: #f5f5f5;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
}
.speed-toggle button + button {
  border-left: 1px solid #ccc;
}
.speed-toggle button.active {
  background: #333;
  color: #fff;
  font-weight: bold;
}
</style>
