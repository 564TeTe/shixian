<template><div class="teaching-page"><page-heading title="账号与安全" eyebrow="ACCOUNT & SECURITY" description="管理个人信息与登录安全。"/><div class="two-columns"><section class="panel profile"><span class="avatar huge">{{ isAdmin?'管':($storage.get('adminName')||'教')[0] }}</span><h2>{{ $storage.get('adminName') }}</h2><p>{{ isAdmin?'系统管理员':'授课教师' }}</p><span class="badge blue">{{ isAdmin?'管理员':'授课教师' }}</span><dl class="detail-grid"><div><dt>登录账号</dt><dd>{{ $storage.get('adminName') }}</dd></div><div><dt>访问范围</dt><dd>{{ isAdmin?'全部教学任务':'本人授课任务' }}</dd></div></dl><button class="btn" @click="$parent.logout()"><sf-icon name="logout"/>退出登录</button></section><section class="panel"><div class="panel-title"><h2>修改登录密码</h2><sf-icon name="shield"/></div><form class="password-form" @submit.prevent="save"><label>原密码<input v-model="form.oldPassword" required type="password" autocomplete="current-password"/></label><label>新密码<input v-model="form.newPassword" required minlength="8" maxlength="64" type="password" autocomplete="new-password" placeholder="8 至 64 位字符"/></label><label>确认新密码<input v-model="form.confirm" required type="password" autocomplete="new-password"/></label><p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p><button class="btn primary" :disabled="saving">{{ saving?'保存中…':'保存修改' }}</button></form></section></div></div></template>
<script>
import PageHeading from './PageHeading'
import { request, shared } from './api'
export default {
  components: { PageHeading },
  mixins: [shared],
  data() {
    return {
      passwordError: '',
      form: { oldPassword: '', newPassword: '', confirm: '' },
      rules: {
        oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
        newPassword: [
          { required: true, min: 8, max: 64, message: '新密码须为 8 至 64 位字符', trigger: 'blur' }
        ],
        confirm: [
          {
            validator: (rule, value, callback) => {
              value && value === this.form.newPassword
                ? callback()
                : callback(new Error('两次输入的密码不一致'))
            },
            trigger: 'blur'
          }
        ]
      }
    }
  },
  methods: {
    async save() {
      this.passwordError = ''
      if (this.saving) return
      if (!this.form.oldPassword || this.form.newPassword.length < 8 || this.form.newPassword.length > 64 || this.form.confirm !== this.form.newPassword) { this.passwordError = '请填写原密码，新密码须为 8 至 64 位且两次输入一致'; return }
      this.saving = true
      try {
        await request('/account/password', {
          method: 'post',
          data: { oldPassword: this.form.oldPassword, newPassword: this.form.newPassword }
        })
        this.form = { oldPassword: '', newPassword: '', confirm: '' }

        this.$message.success('密码已更新，请重新登录')
        this.$storage.clear()
        this.$router.replace('/login')
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
