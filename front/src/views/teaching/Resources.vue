<template>
<div class="teaching-page">
<page-heading :title="isLabs ? '实验室' : '教师账号'" :eyebrow="isLabs ? 'LABORATORY RESOURCES' : 'FACULTY MANAGEMENT'" :description="isLabs ? '查看实验空间与基础信息，为教学安排提供参考。' : '查看教师信息与教学任务，管理教师登录密码。'">
<span class="badge" :class="isLabs ? 'gray' : 'blue'">{{ isLabs ? '实验室资料' : '管理员端' }}</span>
<button v-if="isAdmin" class="btn primary" @click="edit()"><sf-icon name="plus"/>新增{{ isLabs ? '实验室' : '教师' }}</button>
</page-heading>
<section class="panel" v-loading="loading">
<form class="filter-bar" @submit.prevent="load"><label class="search-field"><sf-icon name="search"/><input v-model.trim="q" :aria-label="isLabs ? '搜索实验室' : '搜索教师'" :placeholder="isLabs ? '搜索实验室、编号或位置' : '搜索教师姓名、工号或学院'"/></label><button class="btn primary" :disabled="loading">查询</button><button v-if="q" type="button" class="btn" @click="q='';load()">重置</button><span class="filter-hint muted">共 {{ total }} {{ isLabs ? '间实验室' : '位教师' }}</span></form>
<div v-if="isLabs" class="lab-grid">
<article v-for="(row,i) in rows" :key="row.id" class="lab-card">
<div class="lab-visual" :class="'lab-visual-'+i%3"><span class="room-label">{{ text(row.shiyanshiweizhi) }}</span><div class="room-art" aria-hidden="true"><sf-icon name="building"/><span></span><i></i><i></i><i></i></div><span class="badge" :class="row.equipment_count == null || !row.manager_teacher_id ? 'amber' : 'green'">{{ row.equipment_count == null || !row.manager_teacher_id ? '资料待补充' : '资料完整' }}</span></div>
<div class="lab-body"><small>{{ labCode(row) }}</small><h2>{{ labName(row) }}</h2><div class="lab-meta"><span><sf-icon name="users"/>负责人 <b>{{ manager(row) }}</b></span><span><sf-icon name="grid"/>设备数 <b>{{ row.equipment_count == null ? '待补充' : row.equipment_count+' 台' }}</b></span></div>
<div class="lab-card-footer"><span>{{ labTasks(row.id) }} 个所选学期教学任务</span><button class="btn text" @click="selectedLab=row">查看详情 →</button></div></div></article>
</div>
<div v-else>
<div class="notice"><sf-icon name="info"/><span>系统分配的临时账号需核实正式工号。新增和重置的密码只显示一次，请及时保存。</span></div>
<div class="table-wrap" v-if="rows.length"><table><thead><tr><th>教师</th><th>工号 / 登录账号</th><th>所属学院</th><th>账号类型</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><span class="person-cell"><span class="avatar">{{ (row.jiaoshixingming||'')[0] }}</span><strong>{{ row.jiaoshixingming }}</strong></span></td><td>{{ row.gonghao }}</td><td>{{ text(row.xueyuan) }}</td><td><span class="badge" :class="temporary(row)?'amber':'green'">{{ temporary(row)?'临时账号':'正式账号' }}</span></td><td><div class="row-actions"><button class="btn text" @click="edit(row)">编辑</button><button class="btn text" :disabled="saving" @click="resetPassword(row)">重置密码</button><button class="btn text danger" :disabled="saving" @click="remove(row)">删除</button></div></td></tr></tbody></table></div>
</div>
<div class="empty" v-if="!loading && !rows.length"><sf-icon name="search"/><strong>没有找到符合条件的记录</strong><span>请调整筛选条件或导入真实资料。</span></div>
</section>
<sf-dialog title="实验室详情" :visible="!!selectedLab" @update:visible="selectedLab=null">
<template v-if="selectedLab"><dl class="detail-grid"><div><dt>编号</dt><dd>{{ labCode(selectedLab) }}</dd></div><div><dt>名称</dt><dd>{{ labName(selectedLab) }}</dd></div><div><dt>位置</dt><dd>{{ text(selectedLab.shiyanshiweizhi) }}</dd></div><div><dt>负责人</dt><dd>{{ manager(selectedLab) }}</dd></div><div><dt>设备数</dt><dd>{{ text(selectedLab.equipment_count) }}</dd></div></dl></template>
<span slot="footer"><button v-if="isAdmin" class="btn" @click="edit(selectedLab);selectedLab=null">编辑资料</button><button v-if="isAdmin" class="btn text danger" @click="remove(selectedLab);selectedLab=null">删除</button><button class="btn primary" @click="selectedLab=null">关闭</button></span>
</sf-dialog>
    <sf-dialog
      :title="(form.id ? '编辑' : '新增') + (isLabs ? '实验室' : '教师账号')"
      :visible.sync="visible"
      width="600px"
      :close-on-click-modal="false"
    >
      <sf-alert v-if="saveError" :title="saveError" type="error" />
      <sf-form ref="form" :model="form" :rules="rules" label-position="top">
        <template v-if="isLabs">
          <sf-form-item label="实验室编号" prop="shiyanshibianhao">
            <sf-input v-model.trim="form.shiyanshibianhao" maxlength="64" />
          </sf-form-item>
          <sf-form-item label="实验室名称" prop="shiyanshimingcheng">
            <sf-input v-model.trim="form.shiyanshimingcheng" maxlength="200" />
          </sf-form-item>
          <sf-form-item label="实验室位置">
            <sf-input v-model.trim="form.shiyanshiweizhi" placeholder="未知可留空" />
          </sf-form-item>
          <div class="form-grid">
            <sf-form-item label="负责人教师">
              <sf-select v-model="form.manager_teacher_id" clearable filterable placeholder="尚未确认">
                <sf-option
                  v-for="teacher in lookups.teachers"
                  :key="teacher.id"
                  :value="teacher.id"
                  :label="teacher.jiaoshixingming + ' · ' + teacher.gonghao"
                />
              </sf-select>
            </sf-form-item>
            <sf-form-item label="设备数量">
              <sf-input-number
                v-model="form.equipment_count"
                :min="0"
                :max="1000000"
                :precision="0"
                placeholder="未知请清空"
              />
            </sf-form-item>
          </div>
          <p class="muted">设备数量留空表示未知；0 表示已确认无设备。</p>
        </template>
        <template v-else>
          <sf-form-item label="登录工号" prop="gonghao">
            <sf-input
              v-model.trim="form.gonghao"
              maxlength="64"
              placeholder="正式工号，未知时使用唯一 TMP 前缀临时账号"
            />
          </sf-form-item>
          <sf-form-item label="教师姓名" prop="jiaoshixingming">
            <sf-input v-model.trim="form.jiaoshixingming" maxlength="100" />
          </sf-form-item>
          <sf-form-item label="所属学院">
            <sf-input v-model.trim="form.xueyuan" maxlength="100" placeholder="未知可留空" />
          </sf-form-item>
          <sf-alert
            v-if="!form.id"
            title="创建后由系统生成初始密码，只在本次操作后显示一次。"
            type="info"
            :closable="false"
          />
        </template>
      </sf-form>
      <span slot="footer">
        <sf-button @click="visible = false">取消</sf-button>
        <sf-button type="primary" :loading="saving" @click="save">保存</sf-button>
      </span>
    </sf-dialog>
    <sf-dialog
      title="请保存本次生成的密码"
      :visible.sync="passwordVisible"
      width="490px"
      :close-on-click-modal="false"
      @closed="password = ''"
    >
      <sf-alert
        title="密码只显示一次，关闭后无法再次查看。请交给教师本人，并提醒首次登录后修改。"
        type="warning"
        :closable="false"
        show-icon
      />
      <p class="muted">账号：{{ passwordAccount }}</p>
      <div class="credential">{{ password }}</div>
      <span slot="footer">
        <sf-button type="primary" @click="passwordVisible = false">已保存，关闭</sf-button>
      </span>
    </sf-dialog>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import { shared, request } from './api'
export default {
  props: { entity: { type: String, required: true } },
  components: { PageHeading },
  mixins: [shared],
  data: () => ({
    selectedLab: null,
    reportLabs: [],
    q: '',
    rows: [],
    total: 0,
    visible: false,
    saveError: '',
    form: {},
    passwordVisible: false,
    password: '',
    passwordAccount: ''
  }),
  computed: {
    isLabs() {
      return this.entity === 'labs'
    },
    rules() {
      return (this.isLabs
        ? ['shiyanshibianhao', 'shiyanshimingcheng']
        : ['gonghao', 'jiaoshixingming']
      ).reduce((rules, key) => {
        rules[key] = [{ required: true, message: '请填写此项', trigger: 'blur' }]
        return rules
      }, {})
    }
  },
  watch: { '$route.query.termId': 'loadLabCounts' },
  async mounted() {
    try {
      await this.loadLookups()
    } catch (e) {
      this.fail(e)
    }
    this.load()
    this.loadLabCounts()
  },
  methods: {
    async loadLabCounts() { if(!this.isLabs)return; try { const r=await request('/reports',{params:{termId:this.$route.query.termId}}); this.reportLabs=r.labs||[] } catch(e){this.fail(e)} },
    labTasks(id) { const lab=this.rows.find(l=>l.id===id); const r=this.reportLabs.find(l=>lab && String(l.lab_code)===String(lab.shiyanshibianhao)); return r ? r.task_count : 0 },
    async load() {
      this.loading = true
      try {
        const data = await request('/' + this.entity, { params: { q: this.q } })
        this.rows = data.list
        this.total = data.total
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    temporary(row) {
      return row.is_temporary === true || row.is_temporary === 1 || /^(TMP|TEMP)/i.test(row.gonghao || '')
    },
    manager(row) {
      if (row.manager_name || row.manager_teacher_name) return row.manager_name || row.manager_teacher_name
      const teacher = this.lookups.teachers.find(t => String(t.id) === String(row.manager_teacher_id))
      return teacher ? teacher.jiaoshixingming : '待确认'
    },
    labCode(row) {
      return this.text(row.shiyanshibianhao || row.lab_code || row.code)
    },
    labName(row) {
      return this.text(row.shiyanshimingcheng || row.lab_name || row.name)
    },
    edit(row) {
      this.saveError = ''
      this.form = row
        ? Object.assign({}, row, {
            equipment_count: row.equipment_count == null ? undefined : row.equipment_count
          })
        : this.isLabs
        ? {
            shiyanshibianhao: '',
            shiyanshimingcheng: '',
            shiyanshiweizhi: '',
            manager_teacher_id: null,
            equipment_count: undefined
          }
        : { gonghao: '', jiaoshixingming: '', xueyuan: '' }
      this.visible = true
      this.$nextTick(() => this.$refs.form.clearValidate())
    },
    async save() {
      this.saveError = ''
      if (!(await this.$refs.form.validate().catch(() => false))) return
      this.saving = true
      try {
        const fields = this.isLabs
          ? [
              'shiyanshibianhao',
              'shiyanshimingcheng',
              'shiyanshiweizhi',
              'manager_teacher_id',
              'equipment_count'
            ]
          : ['gonghao', 'jiaoshixingming', 'xueyuan']
        const data = fields.reduce((obj, key) => {
          obj[key] =
            this.form[key] === undefined || (this.form[key] === '' && key === 'manager_teacher_id')
              ? null
              : this.form[key]
          return obj
        }, {})
        const result = await request('/' + this.entity + (this.form.id ? '/' + this.form.id : ''), {
          method: this.form.id ? 'put' : 'post',
          data
        })
        this.visible = false
        this.$message.success('资料已保存')
        if (result && result.password) this.showPassword(result.password, this.form.gonghao)
        await this.load()
        await this.loadLookups()
      } catch (e) {
        // Native modal dialogs sit above global toast messages.
        this.saveError = (e.response && e.response.data && e.response.data.msg) ||
          (e.message === 'Network Error' ? '无法连接后端，请确认服务已启动。' : e.message) || '保存失败，请稍后重试。'
      } finally {
        this.saving = false
      }
    },
    async remove(row) {
      try {
        await this.$confirm(
          '确认删除“' +
            (this.isLabs ? row.shiyanshimingcheng : row.jiaoshixingming) +
            '”？已被教学任务或项目引用的记录无法删除。',
          '删除' + (this.isLabs ? '实验室' : '教师账号'),
          { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
        )
      } catch (e) {
        return
      }
      this.saving = true
      try {
        await request('/' + this.entity + '/' + row.id, { method: 'delete' })
        this.$message.success('记录已删除')
        await this.load()
        await this.loadLookups()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    },
    showPassword(password, account) {
      this.password = password
      this.passwordAccount = account
      this.passwordVisible = true
    },
    async resetPassword(row) {
      try {
        await this.$confirm(
          '重置后原密码将失效。确定重置“' + row.jiaoshixingming + '”的密码？',
          '重置教师密码',
          { type: 'warning', confirmButtonText: '重置密码', cancelButtonText: '取消' }
        )
      } catch (e) {
        return
      }
      this.saving = true
      try {
        const data = await request('/teachers/' + row.id + '/reset-password', { method: 'post' })
        this.showPassword(data.password, row.gonghao)
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
