import { createRouter, createWebHistory } from 'vue-router'
import Chat from '../views/Chat.vue'
import KnowledgeBases from '../views/KnowledgeBases.vue'
import Documents from '../views/Documents.vue'
import DocumentDetail from '../views/DocumentDetail.vue'
import History from '../views/History.vue'
import FeedbackBoard from '../views/FeedbackBoard.vue'
import Dashboard from '../views/Dashboard.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: Chat },
    { path: '/kb', component: KnowledgeBases },
    { path: '/kb/:kbId/docs', component: Documents, props: true },
    { path: '/kb/:kbId/docs/:docId', component: DocumentDetail, props: true },
    { path: '/history', component: History },
    { path: '/feedback', component: FeedbackBoard },
    { path: '/dashboard', component: Dashboard }
  ]
})
