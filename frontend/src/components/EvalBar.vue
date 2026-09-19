<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** Static position evaluation from White's point of view (positive favors White). */
  evaluation: number
}>()

// Squashes the raw territory-diff score (bounded to roughly +-180 on a 10x10
// board) into [-1, 1] so a near-total-domination reading doesn't just pin the
// bar at its limit for the rest of the game - 40 was picked so a ~20-point
// edge (a clear, visible lean mid-game) still leaves room to grow, while a
// 100+ point rout reads as "near-decisive".
const SQUASH = 40
const lean = computed(() => Math.tanh(props.evaluation / SQUASH))

const leader = computed<'WHITE' | 'BLACK' | null>(() => {
  if (props.evaluation > 1) return 'WHITE'
  if (props.evaluation < -1) return 'BLACK'
  return null
})

// Fill grows outward from the center tick toward whichever side is ahead -
// nothing shown at all when even, half the track's reach at a decisive lean.
const fillPct = computed(() => Math.abs(lean.value) * 50)

const label = computed(() => {
  if (!leader.value) return 'Even'
  const sign = props.evaluation > 0 ? '+' : ''
  return `${leader.value === 'WHITE' ? 'White' : 'Black'} ${sign}${props.evaluation}`
})
</script>

<template>
  <div class="eval-bar">
    <div class="eval-track">
      <div
        v-if="leader"
        class="eval-fill"
        :class="leader === 'WHITE' ? 'eval-fill-white' : 'eval-fill-black'"
        :style="{ width: fillPct + '%' }"
      ></div>
      <div class="eval-center-tick"></div>
    </div>
    <p class="eval-label" :class="{ white: leader === 'WHITE', black: leader === 'BLACK' }">{{ label }}</p>
  </div>
</template>

<style scoped>
.eval-bar {
  width: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.eval-track {
  position: relative;
  width: 100%;
  height: 14px;
  border-radius: 7px;
  overflow: hidden;
  background: var(--border);
}
.eval-fill {
  position: absolute;
  top: 0;
  bottom: 0;
  transition: width 350ms ease;
}
/* Anchored at the center tick, growing leftward - White is listed/shown on the left throughout the UI (e.g. the "White vs Black" matchup line). */
.eval-fill-white {
  right: 50%;
  background: #b0470a;
}
/* Anchored at the center tick, growing rightward toward Black's side. */
.eval-fill-black {
  left: 50%;
  background: #1c2733;
}
.eval-center-tick {
  position: absolute;
  z-index: 1;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  margin-left: -1px;
  background: var(--bg);
}
.eval-label {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}
.eval-label.white {
  color: #b0470a;
}
.eval-label.black {
  color: var(--text-h);
}
</style>
