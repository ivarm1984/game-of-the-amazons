<script setup lang="ts">
import type { MoveEventDto } from '../types/api'

defineProps<{ moves: MoveEventDto[] }>()
</script>

<template>
  <div class="move-list">
    <div v-for="m in moves" :key="m.ply" class="move-row">
      <span class="ply">{{ m.ply }}.</span>
      <span class="mover">{{ m.mover }}</span>
      <span class="move">{{ m.move.from }}-{{ m.move.to }}/{{ m.move.arrow }}</span>
    </div>
  </div>
</template>

<style scoped>
.move-list {
  max-height: 420px;
  overflow-y: auto;
  font-family: monospace;
  min-width: 220px;
  border: 1px solid #ddd;
}
.move-row {
  display: flex;
  gap: 8px;
  padding: 3px 6px;
}
.move-row:nth-child(even) {
  background: #f5f5f5;
}
.ply {
  width: 32px;
  color: #888;
}
.mover {
  width: 52px;
  font-weight: bold;
}
</style>
