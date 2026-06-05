import { createRouter, createWebHashHistory } from 'vue-router'
const ProxyConfig = () => import('@/views/ProxyConfig.vue')
const Users = () => import('@/views/Users.vue')
const Monitor = () => import('@/views/Monitor.vue')
const Config = () => import('@/views/Config.vue')

const routes = [
  { path: '/', redirect: '/proxy' },
  { path: '/proxy', name: 'Proxy', component: ProxyConfig, meta: { title: '代理配置' } },
  { path: '/users', name: 'Users', component: Users, meta: { title: '用户管理' } },
  { path: '/monitor', name: 'Monitor', component: Monitor, meta: { title: '实时监控' } },
  { path: '/config', name: 'Config', component: Config, meta: { title: '系统配置' } }
]

const router = createRouter({ history: createWebHashHistory(), routes })
export default router
