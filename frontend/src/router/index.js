import { createRouter, createWebHistory } from 'vue-router'
import Chat from '../views/Chat.vue'
import Docs from '../views/Docs.vue'
import History from '../views/History.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: Chat },
    { path: '/docs', component: Docs },
    { path: '/history', component: History }
  ]
})
