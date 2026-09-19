<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EloPoint } from '../stores/tournamentStore'
import { colorMapFor } from '../utils/botColors'

const props = defineProps<{
  history: Record<string, EloPoint[]>
  botNames: Record<string, string>
  highlightBotId?: string | null
}>()

const WIDTH = 640
const HEIGHT = 260
const MARGIN = { top: 16, right: 16, bottom: 28, left: 44 }

const botIds = computed(() => Object.keys(props.history))

const colorByBot = computed(() => colorMapFor(botIds.value))

const maxGameIndex = computed(() => {
  let max = 0
  for (const points of Object.values(props.history)) {
    for (const p of points) max = Math.max(max, p.gameIndex)
  }
  return Math.max(max, 1)
})

const eloRange = computed(() => {
  let min = Infinity
  let max = -Infinity
  for (const points of Object.values(props.history)) {
    for (const p of points) {
      min = Math.min(min, p.elo)
      max = Math.max(max, p.elo)
    }
  }
  if (!Number.isFinite(min)) return { min: 1400, max: 1600 }
  const pad = Math.max(20, (max - min) * 0.15)
  return { min: Math.floor(min - pad), max: Math.ceil(max + pad) }
})

const innerWidth = WIDTH - MARGIN.left - MARGIN.right
const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom

function xFor(gameIndex: number): number {
  return MARGIN.left + (gameIndex / maxGameIndex.value) * innerWidth
}
function yFor(elo: number): number {
  const { min, max } = eloRange.value
  const ratio = (elo - min) / (max - min || 1)
  return MARGIN.top + innerHeight - ratio * innerHeight
}

function pathFor(points: EloPoint[]): string {
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${xFor(p.gameIndex)} ${yFor(p.elo)}`).join(' ')
}

const yTicks = computed(() => {
  const { min, max } = eloRange.value
  const step = Math.ceil((max - min) / 4 / 10) * 10 || 10
  const ticks: number[] = []
  for (let v = Math.ceil(min / step) * step; v <= max; v += step) ticks.push(v)
  return ticks
})

const hoverGameIndex = ref<number | null>(null)

function onMouseMove(event: MouseEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  const scaleX = WIDTH / rect.width
  const localX = (event.clientX - rect.left) * scaleX
  const ratio = (localX - MARGIN.left) / innerWidth
  const gameIndex = Math.round(ratio * maxGameIndex.value)
  hoverGameIndex.value = Math.min(Math.max(gameIndex, 0), maxGameIndex.value)
}
function onMouseLeave() {
  hoverGameIndex.value = null
}

function eloAtOrBefore(points: EloPoint[], gameIndex: number): number | null {
  let result: number | null = null
  for (const p of points) {
    if (p.gameIndex <= gameIndex) result = p.elo
    else break
  }
  return result
}

const tooltipRows = computed(() => {
  if (hoverGameIndex.value === null) return []
  const idx = hoverGameIndex.value
  return botIds.value
    .map((id) => ({ id, name: props.botNames[id] ?? id, elo: eloAtOrBefore(props.history[id], idx), color: colorByBot.value[id] }))
    .filter((row) => row.elo !== null)
    .sort((a, b) => (b.elo ?? 0) - (a.elo ?? 0))
})

const tooltipX = computed(() => (hoverGameIndex.value === null ? 0 : xFor(hoverGameIndex.value)))
const tooltipLeft = computed(() => tooltipX.value > WIDTH - 160)
</script>

<template>
  <div class="elo-chart">
    <svg
      :viewBox="`0 0 ${WIDTH} ${HEIGHT}`"
      class="chart-svg"
      @mousemove="onMouseMove"
      @mouseleave="onMouseLeave"
    >
      <line
        v-for="tick in yTicks"
        :key="tick"
        class="gridline"
        :x1="MARGIN.left"
        :x2="WIDTH - MARGIN.right"
        :y1="yFor(tick)"
        :y2="yFor(tick)"
      />
      <text v-for="tick in yTicks" :key="'label-' + tick" class="axis-label" :x="MARGIN.left - 8" :y="yFor(tick) + 4" text-anchor="end">
        {{ tick }}
      </text>
      <text class="axis-label" :x="MARGIN.left" :y="HEIGHT - 6">0</text>
      <text class="axis-label" :x="WIDTH - MARGIN.right" :y="HEIGHT - 6" text-anchor="end">game {{ maxGameIndex }}</text>

      <line
        v-if="hoverGameIndex !== null"
        class="crosshair"
        :x1="tooltipX"
        :x2="tooltipX"
        :y1="MARGIN.top"
        :y2="HEIGHT - MARGIN.bottom"
      />

      <path
        v-for="id in botIds"
        :key="id"
        :d="pathFor(history[id])"
        fill="none"
        :stroke="colorByBot[id]"
        :stroke-width="highlightBotId && highlightBotId !== id ? 1.5 : 2.5"
        :opacity="highlightBotId && highlightBotId !== id ? 0.25 : 1"
      />

      <g v-if="tooltipRows.length" :transform="`translate(${tooltipLeft ? tooltipX - 150 : tooltipX + 10}, ${MARGIN.top})`">
        <rect class="tooltip-bg" width="140" :height="16 + tooltipRows.length * 16" rx="6" />
        <text class="tooltip-title" x="8" y="14">game {{ hoverGameIndex }}</text>
        <g v-for="(row, i) in tooltipRows" :key="row.id" :transform="`translate(8, ${16 + i * 16 + 10})`">
          <circle r="4" :fill="row.color" cy="-4" />
          <text class="tooltip-row" x="10" y="0">{{ row.name }}</text>
          <text class="tooltip-elo" x="132" y="0" text-anchor="end">{{ Math.round(row.elo ?? 0) }}</text>
        </g>
      </g>
    </svg>

    <div class="legend">
      <div v-for="id in botIds" :key="id" class="legend-item">
        <span class="swatch" :style="{ background: colorByBot[id] }"></span>
        <span>{{ botNames[id] ?? id }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.elo-chart {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.chart-svg {
  width: 100%;
  height: auto;
  overflow: visible;
}
.gridline {
  stroke: var(--border);
  stroke-width: 1;
}
.axis-label {
  fill: var(--text);
  font-size: 10px;
}
.crosshair {
  stroke: var(--text);
  stroke-width: 1;
  stroke-dasharray: 3 3;
  opacity: 0.5;
}
.tooltip-bg {
  fill: var(--bg);
  stroke: var(--border);
  stroke-width: 1;
}
.tooltip-title {
  fill: var(--text);
  font-size: 10px;
  font-weight: 600;
}
.tooltip-row {
  fill: var(--text-h);
  font-size: 11px;
}
.tooltip-elo {
  fill: var(--text);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  font-size: 12px;
  color: var(--text-h);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex: none;
}
</style>
