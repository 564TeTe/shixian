<template><main class="login-screen"><section class="login-story"><div class="brand"><span class="brand-mark"><sf-icon name="flask"/></span><span>实验教学<span class="brand-sub">LAB TEACHING WORKSPACE</span></span></div><div><div class="eyebrow">A BETTER SPACE FOR TEACHING</div><h1>连接实验与教学，<br/>让探索更有方向。</h1><p>课程管理 · 实验项目 · 资源统计 · 智能查询</p><div class="login-art" aria-hidden="true"><sf-icon name="flask"/><span class="orbit"></span><span class="orbit orbit-two"></span></div></div><span class="muted">实验教学项目管理系统</span></section>
<section class="login-form-wrap"><form class="login-form" @submit.prevent="login"><span class="eyebrow">WELCOME BACK</span><h2>欢迎来到教学工作台</h2><p>使用管理员分配的账号登录。</p><label>登录身份<select v-model="form.role"><option>管理员</option><option>教师</option></select></label><label>用户名 / 教师工号<input v-model.trim="form.username" required autocomplete="username" placeholder="请输入账号"/></label><label>密码<input v-model="form.password" required type="password" autocomplete="current-password" placeholder="请输入密码"/></label><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="btn primary" :disabled="loading">{{ loading?'登录中…':'进入工作台' }}<sf-icon name="arrow"/></button><div class="note-box"><sf-icon name="info"/>临时账号首次登录后，请在账号与安全页面修改密码。</div></form></section></main></template>
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
      const valid = !!this.form.username && !!this.form.password
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
