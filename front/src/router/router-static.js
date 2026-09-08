import Vue from 'vue'
import VueRouter from 'vue-router'
import Index from '@/views/index'
import Home from '@/views/home'
import Login from '@/views/login'
import storage from '@/utils/storage'
Vue.use(VueRouter)
const router = new VueRouter({
  mode: 'hash',
  routes: [
    { path: '/login', name: 'login', component: Login },
    { path: '/', redirect: '/index' },
    {
      path: '/index',
      component: Index,
      children: [
        { path: '', name: 'home', component: Home, meta: { title: '教学概览' } },
        {
          path: '/teaching/tasks',
          component: () => import('@/views/teaching/Tasks'),
          meta: { title: '课程与课表' }
        },
        {
          path: '/teaching/projects',
          component: () => import('@/views/teaching/Projects'),
          meta: { title: '实验项目' }
        },
        {
          path: '/teaching/terms',
          component: () => import('@/views/teaching/Terms'),
          meta: { title: '学年学期' }
        },
        {
          path: '/teaching/labs',
          component: () => import('@/views/teaching/Resources'),
          props: { entity: 'labs' },
          meta: { title: '实验室' }
        },
        {
          path: '/teaching/teachers',
          component: () => import('@/views/teaching/Resources'),
          props: { entity: 'teachers' },
          meta: { title: '教师账号', admin: true }
        },
        {
          path: '/teaching/imports',
          component: () => import('@/views/teaching/Imports'),
          meta: { title: '导入中心', admin: true }
        },
        {
          path: '/teaching/reports',
          component: () => import('@/views/teaching/Reports'),
          meta: { title: '统计报表' }
        },
        {
          path: '/teaching/ai',
          component: () => import('@/views/teaching/Ai'),
          meta: { title: '智能查询', admin: true }
        },
        {
          path: '/teaching/account',
          component: () => import('@/views/teaching/Account'),
          meta: { title: '账号与安全' }
        },
        { path: '/center', redirect: '/teaching/account' },
        { path: '/updatePassword', redirect: '/teaching/account' }
      ]
    },
    { path: '*', redirect: '/index' }
  ]
})
router.beforeEach((to, from, next) => {
  const table = storage.get('sessionTable')
  if (to.path !== '/login' && (!storage.get('Token') || !['users', 'jiaoshi'].includes(table)))
    return next('/login')
  if (to.matched.some(route => route.meta.admin) && table !== 'users') return next('/index')
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '实验教学项目管理系统'
  next()
})
export default router
