<template>
  <header class="topbar">
    <div class="breadcrumb">
      <span>{{ role || '教学端' }}</span>
      <b>/</b>
      <strong>{{ $route.meta.title || '教学概览' }}</strong>
    </div>
    <div class="header-user">
      <span class="role-tag">{{ role }}</span>
      <span>{{ name }}</span>
      <el-button type="text" @click="$router.push('/teaching/account')">账号与安全</el-button>
      <el-button type="text" :loading="loggingOut" @click="logout">退出登录</el-button>
    </div>
  </header>
</template>
<script>
import { errorMessage } from '@/views/teaching/api'
export default {
  data() {
    return { loggingOut: false, name: this.$storage.get('adminName'), role: this.$storage.get('role') }
  },
  methods: {
    async logout() {
      this.loggingOut = true
      try {
        const { data } = await this.$http({
          url: this.$storage.get('sessionTable') + '/logout',
          method: 'post',
          timeout: 15000
        })
        if (!data || Number(data.code) !== 0) throw new Error((data && data.msg) || '退出失败')
        this.$storage.clear()
        this.$router.replace('/login')
      } catch (error) {
        errorMessage(error)
      } finally {
        this.loggingOut = false
      }
    }
  }
}
</script>
<style>
.header-user {
  display: flex;
  gap: 12px;
  align-items: center;
  color: #68758b;
  font-size: 12px;
}
.header-user .el-button {
  color: #6e7f98;
  font-size: 12px;
  margin: 0;
}
.role-tag {
  padding: 4px 9px;
  border: 1px solid #dce4ef;
  border-radius: 5px;
  color: #8090a6;
  font-size: 10px;
}
</style>
