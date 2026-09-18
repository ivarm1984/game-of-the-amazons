<script setup lang="ts">
import type { BotSummary } from '../types/api'

defineProps<{ bots: BotSummary[]; modelValue: string; label: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()
</script>

<template>
  <label class="bot-selector">
    <span>{{ label }}</span>
    <select :value="modelValue" @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value)">
      <option v-for="bot in bots" :key="bot.id" :value="bot.id">
        {{ bot.displayName }} (difficulty {{ bot.difficulty }})
      </option>
    </select>
  </label>
</template>

<style scoped>
.bot-selector {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
select {
  padding: 6px;
  font-size: 14px;
}
</style>
