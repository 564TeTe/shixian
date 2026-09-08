<template>
  <el-aside class="teaching-aside" width="218px">
    <div class="rail-label">教学工作台</div>
    <el-menu
      router
      :default-active="$route.path"
      background-color="#1e3a5f"
      text-color="#c6d4e6"
      active-text-color="#ffffff"
    >
      <el-menu-item v-for="item in items" :key="item.path" :index="item.path">
        <i :class="item.icon" />
        <span slot="title">{{ item.title }}</span>
      </el-menu-item>
    </el-menu>
    <div class="rail-note">
      <span class="dot" />
      {{ isAdmin ? '管理员工作空间' : '我的教学空间' }}
      <small>{{ isAdmin ? '课程 · 项目 · 资源 · 数据' : '仅展示本人参与的教学任务' }}</small>
    </div>
  </el-aside>
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
<style scoped>
.teaching-aside {
  background: #1e3a5f;
  padding: 100px 12px 110px;
  min-height: 100vh;
  box-sizing: border-box;
  overflow-y: auto;
  flex-shrink: 0;
  font-family: 'Microsoft YaHei', sans-serif;
}
.rail-label {
  padding: 8px 17px 20px;
  color: #95aecb;
  font-size: 10px;
  letter-spacing: 2px;
}
.teaching-aside .el-menu {
  border: 0;
}
.teaching-aside .el-menu-item {
  height: 48px;
  line-height: 48px;
  margin-bottom: 7px;
  border-radius: 6px;
  font-size: 13px;
}
.teaching-aside .el-menu-item i {
  color: #9fb9d9;
  font-size: 19px;
  margin-right: 12px;
}
.teaching-aside .el-menu-item.is-active {
  background: #365c88 !important;
  box-shadow: inset 3px 0 #6bb3ff;
}
.teaching-aside .el-menu-item.is-active i {
  color: #85c0ff;
}
.rail-note {
  position: fixed;
  bottom: 24px;
  left: 28px;
  color: #bccde1;
  font-size: 11px;
}
.rail-note small {
  display: block;
  color: #7f9bbd;
  font-size: 9px;
  margin-top: 9px;
}
.dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  background: #76b6fa;
  border-radius: 100%;
  margin-right: 8px;
}
@media (max-width: 900px) {
  .teaching-aside {
    width: 180px !important;
    padding-left: 7px;
    padding-right: 7px;
  }
  .teaching-aside .el-menu-item {
    padding-left: 12px !important;
  }
  .rail-note {
    left: 18px;
  }
}
@media (max-width: 650px) {
  .teaching-aside {
    width: 60px !important;
  }
  .rail-label,
  .rail-note,
  .teaching-aside .el-menu-item span {
    display: none;
  }
  .teaching-aside .el-menu-item i {
    margin-right: 0;
  }
  .teaching-aside .el-menu-item {
    padding-left: 12px !important;
  }
}
</style>
