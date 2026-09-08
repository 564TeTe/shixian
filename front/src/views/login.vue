<template>
  <main class="teaching-login">
    <section class="login-intro">
      <div class="login-wordmark">
        <i class="el-icon-s-grid" />
        LAB TEACHING
      </div>
      <div class="intro-copy">
        <span class="intro-label">实验教学 · 有序协同</span>
        <h1>
          让每一次实验，
          <br />
          都有清晰的安排。
        </h1>
        <p>
          连接课程、教学任务与实验项目，
          <br />
          让教学数据回到真实、可追溯的工作流程。
        </p>
        <div class="intro-lines">
          <span>01 / 课程与课表</span>
          <span>02 / 实验项目</span>
          <span>03 / 教学统计</span>
        </div>
      </div>
      <span class="intro-footer">实验教学项目管理系统</span>
    </section>
    <section class="login-form-area">
      <div class="signin-card">
        <div class="signin-eyebrow">WELCOME TO YOUR WORKSPACE</div>
        <h2>登录教学工作台</h2>
        <p class="signin-help">使用管理员分配的账号登录</p>
        <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.native.prevent="login">
          <el-form-item label="登录身份">
            <el-radio-group v-model="form.role">
              <el-radio-button label="管理员" />
              <el-radio-button label="教师" />
            </el-radio-group>
          </el-form-item>
          <el-form-item label="用户名 / 教师工号" prop="username">
            <el-input
              v-model.trim="form.username"
              autocomplete="username"
              placeholder="请输入账号"
              prefix-icon="el-icon-user"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="current-password"
              placeholder="请输入密码"
              prefix-icon="el-icon-lock"
            />
          </el-form-item>
          <el-alert
            v-if="error"
            :title="error"
            type="error"
            :closable="false"
            show-icon
            class="login-error"
          />
          <el-button type="primary" native-type="submit" :loading="loading" class="signin-button">
            进入工作台
            <i class="el-icon-right" />
          </el-button>
        </el-form>
        <p class="signin-note">
          <i class="el-icon-info" />
          系统分配的临时教师账号，请在首次登录后修改密码。
        </p>
      </div>
    </section>
  </main>
</template>
<script>
export default {
  data() {
    return {
      loading: false,
      error: '',
      form: { username: '', password: '', role: '教师' },
      rules: {
        username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  methods: {
    async login() {
      if (this.loading) return
      const valid = await this.$refs.form.validate().catch(() => false)
      if (!valid) return
      this.loading = true
      this.error = ''
      try {
        const table = this.form.role === '管理员' ? 'users' : 'jiaoshi'
        const data = new URLSearchParams()
        data.append('username', this.form.username)
        data.append('password', this.form.password)
        const response = await this.$http({
          url: table + '/login',
          method: 'post',
          data,
          timeout: 30000,
          headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' }
        })
        const body = response.data
        if (!body || Number(body.code) !== 0 || !body.token) throw new Error((body && body.msg) || '登录失败')
        this.$storage.set('Token', body.token)
        this.$storage.set('role', this.form.role)
        this.$storage.set('sessionTable', table)
        this.$storage.set('adminName', this.form.username)
        this.$router.replace('/index')
      } catch (e) {
        this.error =
          (e.response && e.response.data && e.response.data.msg) ||
          (e.message === 'Network Error' ? '无法连接服务，请确认后端已启动' : e.message)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
<style scoped>
.teaching-login {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100vh;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}
.login-intro {
  background: #1e3a5f;
  color: #fff;
  padding: 50px 10%;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.login-intro::after {
  content: '';
  position: absolute;
  width: 450px;
  height: 450px;
  right: -210px;
  bottom: -180px;
  border: 1px solid #486689;
  border-radius: 50%;
  box-shadow: 0 0 0 60px rgba(112, 156, 200, 0.04), 0 0 0 120px rgba(112, 156, 200, 0.025);
}
.login-wordmark {
  font-size: 13px;
  letter-spacing: 3px;
  display: flex;
  align-items: center;
  gap: 13px;
}
.login-wordmark i {
  font-size: 29px;
  color: #88bdfa;
}
.intro-copy {
  margin: 80px 0 100px;
  position: relative;
  z-index: 1;
}
.intro-label {
  font-size: 12px;
  letter-spacing: 4px;
  color: #93b7e1;
}
.intro-copy h1 {
  font-size: 38px;
  line-height: 1.65;
  letter-spacing: 3px;
  font-weight: 500;
  margin: 24px 0;
}
.intro-copy p {
  font-size: 13px;
  line-height: 2.2;
  color: #adc2dc;
}
.intro-lines {
  display: flex;
  flex-direction: column;
  gap: 17px;
  border-left: 1px solid #597899;
  padding-left: 19px;
  margin-top: 47px;
  font-size: 10px;
  letter-spacing: 2px;
  color: #acc2dc;
}
.intro-footer {
  color: #7292b6;
  font-size: 10px;
  letter-spacing: 2px;
}
.login-form-area {
  background: #f7f9fc;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 50px;
}
.signin-card {
  width: 100%;
  max-width: 365px;
}
.signin-eyebrow {
  color: #7890aa;
  font-size: 9px;
  letter-spacing: 2px;
  margin-bottom: 15px;
}
.signin-card h2 {
  color: #1e3a5f;
  font-size: 27px;
  font-weight: 600;
}
.signin-help {
  color: #8190a4;
  font-size: 12px;
  margin: 13px 0 36px;
}
.signin-card .el-form-item {
  margin-bottom: 24px;
}
.signin-button {
  width: 100%;
  margin-top: 8px;
  height: 45px;
  letter-spacing: 2px;
}
.signin-button i {
  margin-left: 12px;
}
.signin-note {
  color: #8797aa;
  font-size: 11px;
  line-height: 1.9;
  margin-top: 24px;
}
.login-error {
  margin-bottom: 18px;
}
@media (max-width: 900px) {
  .intro-copy h1 {
    font-size: 29px;
  }
  .login-form-area {
    padding: 30px;
  }
}
@media (max-width: 680px) {
  .teaching-login {
    grid-template-columns: 1fr;
  }
  .login-intro {
    min-height: auto;
    padding: 25px;
  }
  .intro-copy,
  .intro-footer {
    display: none;
  }
  .login-form-area {
    min-height: 75vh;
  }
}
</style>
