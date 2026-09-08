<template>
  <header class="teaching-header">
    <router-link to="/index" class="brand">
      <span class="brand-mark"><i class="el-icon-s-grid" /></span>
      <span>
        实验教学项目管理系统
        <small>LAB TEACHING · ACADEMIC WORKSPACE</small>
      </span>
    </router-link>
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
<style scoped>
.teaching-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 76px;
  background: #1e3a5f;
  z-index: 100;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 29px;
  box-shadow: 0 2px 8px rgba(16, 42, 79, 0.12);
  font-family: 'Microsoft YaHei', sans-serif;
}
.brand {
  display: flex;
  gap: 13px;
  align-items: center;
  text-decoration: none;
  color: #fff;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 1px;
}
.brand small {
  display: block;
  color: #a5bedb;
  font-size: 8px;
  letter-spacing: 2px;
  margin-top: 5px;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  background: #36577e;
  border: 1px solid #6382a3;
  border-radius: 9px;
  font-size: 24px;
}
.header-user {
  display: flex;
  gap: 16px;
  align-items: center;
  color: #dfebf8;
  font-size: 12px;
}
.header-user .el-button {
  color: #d9e9fc;
  font-size: 12px;
  margin: 0;
}
.role-tag {
  padding: 4px 9px;
  border: 1px solid #657e9e;
  border-radius: 5px;
  color: #c3d7ee;
  font-size: 10px;
}
@media (max-width: 900px) {
  .brand {
    font-size: 15px;
  }
  .brand small,
  .header-user > span {
    display: none;
  }
  .teaching-header {
    padding: 0 15px;
  }
  .header-user {
    gap: 10px;
  }
}
</style>
