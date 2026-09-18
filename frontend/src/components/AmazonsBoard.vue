<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { BoardDto, MoveDto } from '../types/api'

export interface MoveAnimation {
  phase: 'queen' | 'shot'
  move: MoveDto
  mover: 'WHITE' | 'BLACK'
  durationMs: number
}

const props = defineProps<{
  board: BoardDto | null
  lastMove?: MoveDto | null
  anim?: MoveAnimation | null
}>()

const CELL = 40

interface Point {
  x: number
  y: number
}

// "d1" -> column d (0-indexed from a), rank 1 -> board row 0. Board row 9 is
// drawn at the top (see displayRows below), so screen y flips the row.
function cellCenter(pos: string): Point {
  const match = /^([a-j])(10|[1-9])$/i.exec(pos)
  if (!match) return { x: 0, y: 0 }
  const col = match[1].toLowerCase().charCodeAt(0) - 'a'.charCodeAt(0)
  const row = Number(match[2]) - 1
  return { x: col * CELL + CELL / 2, y: (9 - row) * CELL + CELL / 2 }
}

// 'settled' = most recent move fully played out (both lines stay as a
// lingering indicator); 'queen'/'shot' = actively animating that leg of the
// current move.
const phase = computed<'queen' | 'shot' | 'settled' | null>(() => {
  if (props.anim) return props.anim.phase
  return props.lastMove ? 'settled' : null
})

const activeMove = computed(() => props.anim?.move ?? props.lastMove ?? null)

const moveOverlay = computed(() => {
  if (!activeMove.value) return null
  return {
    from: cellCenter(activeMove.value.from),
    to: cellCenter(activeMove.value.to),
    arrow: cellCenter(activeMove.value.arrow),
  }
})

// The queen's travel line appears once it lands and lingers through the shot;
// the shot's travel line only appears once the arrow has actually landed, so
// it doesn't pre-draw the flight path the animation is about to fly along.
const showMoveLine = computed(() => phase.value === 'shot' || phase.value === 'settled')
const showShotLine = computed(() => phase.value === 'settled')

// A floating glyph that slides from its start to end cell over the phase's
// duration, giving the queen's move and the arrow's shot each a visible beat
// instead of the board just jumping to the result.
const floatingSymbol = computed(() => {
  if (phase.value === 'queen') return props.anim!.mover === 'WHITE' ? '♕' : '♛'
  if (phase.value === 'shot') return '✕'
  return null
})
const floatingClass = computed(() => {
  if (phase.value === 'queen') return props.anim!.mover === 'WHITE' ? 'white-queen' : 'black-queen'
  if (phase.value === 'shot') return 'shot-glyph'
  return ''
})
const floatingPos = ref<Point | null>(null)
const floatingTransitioning = ref(false)
const floatingDurationMs = ref(0)

watch(
  () => props.anim,
  async (anim) => {
    if (!anim) {
      floatingPos.value = null
      floatingTransitioning.value = false
      return
    }
    const start = anim.phase === 'queen' ? cellCenter(anim.move.from) : cellCenter(anim.move.to)
    const end = anim.phase === 'queen' ? cellCenter(anim.move.to) : cellCenter(anim.move.arrow)
    floatingDurationMs.value = anim.durationMs
    floatingTransitioning.value = false
    floatingPos.value = start
    await nextTick()
    requestAnimationFrame(() => {
      floatingTransitioning.value = true
      floatingPos.value = end
    })
  },
  { immediate: true, deep: true },
)

function toneClass(row: number, col: number): string {
  return (row + col) % 2 === 0 ? 'square-light' : 'square-dark'
}

function pieceClass(ch: string): string {
  switch (ch) {
    case 'W':
      return 'white-queen'
    case 'B':
      return 'black-queen'
    case 'x':
      return 'arrow'
    default:
      return ''
  }
}

function cellSymbol(ch: string): string {
  switch (ch) {
    case 'W':
      return '♕'
    case 'B':
      return '♛'
    case 'x':
      return '✕'
    default:
      return ''
  }
}

// Displayed top-to-bottom as row 9 down to row 0, matching the backend's own
// board.render() convention, so a screenshot and the CLI output line up. Each
// row keeps its real board index (needed for correct checkerboard parity)
// even though the array itself is shown in reverse.
const displayRows = computed(() =>
  props.board
    ? props.board.rows.map((cells, row) => ({ row, cells: cells.split('') })).reverse()
    : [],
)
</script>

<template>
  <div class="board">
    <template v-if="board">
      <div v-for="rowData in displayRows" :key="rowData.row" class="board-row">
        <div
          v-for="(ch, cIdx) in rowData.cells"
          :key="cIdx"
          class="cell"
          :class="[toneClass(rowData.row, cIdx), pieceClass(ch)]"
        >
          {{ cellSymbol(ch) }}
        </div>
      </div>
      <svg v-if="moveOverlay" class="move-overlay" viewBox="0 0 400 400">
        <defs>
          <marker id="move-arrowhead" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 Z" fill="#2563eb" />
          </marker>
          <marker id="shot-arrowhead" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto">
            <path d="M0,0 L8,4 L0,8 Z" fill="#dc2626" />
          </marker>
        </defs>
        <line
          v-if="showMoveLine"
          :x1="moveOverlay.from.x"
          :y1="moveOverlay.from.y"
          :x2="moveOverlay.to.x"
          :y2="moveOverlay.to.y"
          class="move-line"
          marker-end="url(#move-arrowhead)"
        />
        <line
          v-if="showShotLine"
          :x1="moveOverlay.to.x"
          :y1="moveOverlay.to.y"
          :x2="moveOverlay.arrow.x"
          :y2="moveOverlay.arrow.y"
          class="shot-line"
          marker-end="url(#shot-arrowhead)"
        />
      </svg>
      <div
        v-if="floatingSymbol && floatingPos"
        class="floating-piece"
        :class="floatingClass"
        :style="{
          left: floatingPos.x + 'px',
          top: floatingPos.y + 'px',
          transitionDuration: floatingTransitioning ? floatingDurationMs + 'ms' : '0ms',
        }"
      >
        {{ floatingSymbol }}
      </div>
    </template>
    <div v-else class="board-empty">Waiting for match to start…</div>
  </div>
</template>

<style scoped>
.board {
  position: relative;
  display: inline-block;
  border: 2px solid #333;
  line-height: 0;
}
.move-overlay {
  position: absolute;
  inset: 0;
  width: 400px;
  height: 400px;
  pointer-events: none;
}
.move-line {
  stroke: #2563eb;
  stroke-width: 3;
  stroke-linecap: round;
}
.shot-line {
  stroke: #dc2626;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-dasharray: 7 5;
}
.floating-piece {
  position: absolute;
  width: 40px;
  height: 40px;
  margin-left: -20px;
  margin-top: -20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: bold;
  pointer-events: none;
  z-index: 2;
  transition-property: left, top;
  transition-timing-function: linear;
}
.floating-piece.shot-glyph {
  color: #dc2626;
}
.board-row {
  display: flex;
}
.cell {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  border: 1px solid #ccc;
  box-sizing: border-box;
}
.square-light {
  background: #f1ebd9;
}
.square-dark {
  background: #d7c7a3;
}
.white-queen {
  color: #b0470a;
  font-weight: bold;
}
.black-queen {
  color: #1c2733;
  font-weight: bold;
}
.arrow {
  background: #3a3a3a;
  color: #eee;
}
.board-empty {
  width: 402px;
  height: 402px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
}
</style>
