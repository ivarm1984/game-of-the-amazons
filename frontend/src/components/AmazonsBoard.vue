<script setup lang="ts">
import { computed } from 'vue'
import type { BoardDto } from '../types/api'

const props = defineProps<{ board: BoardDto | null }>()

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
    </template>
    <div v-else class="board-empty">Waiting for match to start…</div>
  </div>
</template>

<style scoped>
.board {
  display: inline-block;
  border: 2px solid #333;
  line-height: 0;
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
