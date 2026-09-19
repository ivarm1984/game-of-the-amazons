import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import MatchSetupView from '../views/MatchSetupView.vue'
import MatchViewerView from '../views/MatchViewerView.vue'
import TournamentSetupView from '../views/TournamentSetupView.vue'
import TournamentView from '../views/TournamentView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/match/setup', name: 'match-setup', component: MatchSetupView },
    { path: '/match/:id', name: 'match-viewer', component: MatchViewerView, props: true },
    { path: '/tournament/setup', name: 'tournament-setup', component: TournamentSetupView },
    { path: '/tournament/:id', name: 'tournament-viewer', component: TournamentView, props: true },
  ],
})

export default router
