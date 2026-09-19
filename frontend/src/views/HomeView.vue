<script setup lang="ts">
import { onMounted } from 'vue'
import { useBotsStore } from '../stores/botsStore'

const botsStore = useBotsStore()
onMounted(() => botsStore.load())
</script>

<template>
  <div class="home">
    <h1>Game of the Amazons — Bot Arena</h1>
    <p>
      <RouterLink to="/match/setup">Start a head-to-head match ▸</RouterLink>
    </p>
    <p>
      <RouterLink to="/tournament/setup">Run a tournament ▸</RouterLink>
    </p>
    <h2>Bot catalog</h2>
    <table>
      <thead>
        <tr>
          <th>Difficulty</th>
          <th>Name</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="bot in botsStore.bots" :key="bot.id">
          <td>{{ bot.difficulty }}</td>
          <td>{{ bot.displayName }}</td>
          <td>{{ bot.description }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.home {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 12px;
}
th,
td {
  text-align: left;
  padding: 8px;
  border-bottom: 1px solid #ddd;
}
</style>
