<template>
  <div class="teaching-page">
    <page-heading
      title="账号与安全"
      description="维护当前登录账号的密码。教师身份和工号由管理员统一维护。"
      eyebrow="ACCOUNT SECURITY"
    />
    <section class="panel account-panel">
      <dl class="detail-grid">
        <div>
          <dt>当前账号</dt>
          <dd>{{ $storage.get('adminName') }}</dd>
        </div>
        <div>
          <dt>登录身份</dt>
          <dd>{{ isAdmin ? '管理员' : '教师' }}</dd>
        </div>
      </dl>
      <div class="panel-title"><h2>修改密码</h2></div>
      <el-form ref="form" :model="form" :rules="rules" label-position="top" @submit.native.prevent="save">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="8 至 64 位字符"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="saving">保存新密码</el-button>
      </el-form>
    </section>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import { request, shared } from './api'
export default {
  components: { PageHeading },
  mixins: [shared],
  data() {
    return {
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
      if (!(await this.$refs.form.validate().catch(() => false))) return
      this.saving = true
      try {
        await request('/account/password', {
          method: 'post',
          data: { oldPassword: this.form.oldPassword, newPassword: this.form.newPassword }
        })
        this.form = { oldPassword: '', newPassword: '', confirm: '' }
        this.$refs.form.clearValidate()
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
<style scoped>
.account-panel {
  max-width: 600px;
}
.account-panel .detail-grid {
  grid-template-columns: 1fr 1fr;
  padding-bottom: 20px;
  border-bottom: 1px solid #e4ebf3;
}
</style>
