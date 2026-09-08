<template>
  <el-main class="teaching-main">
    <div class="workspace-breadcrumb">
      <span>教学管理</span>
      <i class="el-icon-arrow-right" />
      <span>{{ $route.meta.title }}</span>
    </div>
    <router-view :key="$route.path" />
  </el-main>
</template>
<script>
import '@/views/teaching/teaching.scss'
export default { name: 'IndexMain' }
</script>
<style scoped>
.teaching-main {
  padding: 100px 30px 20px;
  background: #f5f8fc;
  min-width: 0;
  height: 100vh;
  box-sizing: border-box;
}
.workspace-breadcrumb {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #92a0b0;
  font-size: 11px;
  margin-bottom: 26px;
}
.workspace-breadcrumb span:last-child {
  color: #5d728b;
}
@media (max-width: 900px) {
  .teaching-main {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>
