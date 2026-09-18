import { defineStore } from 'pinia'
import { fetchBots } from '../api/client'
import type { BotSummary } from '../types/api'

export const useBotsStore = defineStore('bots', {
  state: () => ({
    bots: [] as BotSummary[],
    loaded: false,
  }),
  actions: {
    async load() {
      if (this.loaded) return
      this.bots = await fetchBots()
      this.loaded = true
    },
  },
})
