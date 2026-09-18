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
    <p class="status">
      Status: {{ matchStore.status }}
      <template v-if="matchStore.result">
        — {{ matchStore.result.winner }} wins ({{ matchStore.result.reason }}, {{ matchStore.result.totalPlies }} plies)
      </template>
    </p>
    <div class="layout">
      <AmazonsBoard :board="matchStore.board" />
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
.status {
  font-weight: bold;
}
</style>
