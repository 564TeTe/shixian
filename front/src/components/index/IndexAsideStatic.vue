<template>
  <aside class="sidebar">
    <router-link to="/index" class="brand">
      <span class="brand-mark"><i class="el-icon-s-grid icon" /></span>
      <span>实验教学<span class="brand-sub">TEACHING WORKSPACE</span></span>
    </router-link>
    <nav aria-label="主导航">
      <div class="nav-group">教学工作台</div>
      <router-link
        v-for="item in items"
        :key="item.path"
        :to="item.path"
        class="nav-link"
        active-class="selected"
      >
        <i :class="[item.icon, 'icon']" />
        <span>{{ item.title }}</span>
        <span v-if="item.title === '智能查询'" class="ai-label">AI</span>
      </router-link>
    </nav>
    <div class="sidebar-footer">
      <div class="demo-status"><span class="status-dot" />已连接后端数据库</div>
      <p>数据来自 Spring Boot API</p>
      <router-link to="/teaching/account"><i class="el-icon-setting icon" />账号与安全</router-link>
    </div>
  </aside>
</template>
<script>
import { navigation } from '@/utils/menu'
export default {
  computed: {
    isAdmin() {
      return this.$storage.get('sessionTable') === 'users'
    },
    items() {
      return navigation.filter(item => !item.admin || this.isAdmin)
    }
  }
}
</script>
