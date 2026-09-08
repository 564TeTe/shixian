<template>
  <div class="teaching-page">
    <page-heading
      :title="isLabs ? '实验室' : '教师账号'"
      :description="
        isLabs
          ? '维护实验室基础资料与负责人；未知设备数量保留待确认。'
          : '维护教师身份与所属学院，为教学任务提供准确的任课教师关联。'
      "
      :eyebrow="isLabs ? 'LABORATORY RESOURCES' : 'TEACHER ACCOUNTS'"
    >
      <el-button v-if="isAdmin" type="primary" icon="el-icon-plus" @click="edit()">
        {{ isLabs ? '新增实验室' : '新增教师' }}
      </el-button>
    </page-heading>
    <section class="panel">
      <el-alert
        v-if="!isLabs"
        title="系统分配临时账号以 TMP / TEMP 开头，需由管理员核实正式工号。新增和重置的初始密码只显示一次，请及时交给账号本人。"
        type="info"
        show-icon
        :closable="false"
      />
      <div class="filter-bar">
        <el-input
          v-model.trim="q"
          :placeholder="isLabs ? '搜索实验室编号或名称' : '搜索教师姓名或工号'"
          clearable
          @keyup.enter.native="load"
          @clear="load"
        />
        <el-button type="primary" icon="el-icon-search" :loading="loading" @click="load">查询</el-button>
        <span class="muted">共 {{ total }} {{ isLabs ? '间实验室' : '位教师' }}</span>
      </div>
      <el-table v-if="isLabs" v-loading="loading" :data="rows" empty-text="暂无符合条件的实验室">
        <el-table-column prop="shiyanshibianhao" label="实验室编号" min-width="155" />
        <el-table-column prop="shiyanshimingcheng" label="实验室名称" min-width="220" />
        <el-table-column label="位置" min-width="155">
          <template slot-scope="scope">{{ text(scope.row.shiyanshiweizhi) }}</template>
        </el-table-column>
        <el-table-column label="负责人" width="130">
          <template slot-scope="scope">{{ manager(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="设备数量" width="130">
          <template slot-scope="scope">{{ text(scope.row.equipment_count) }}</template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="145">
          <template slot-scope="scope">
            <el-button type="text" @click="edit(scope.row)">编辑</el-button>
            <el-button type="text" :disabled="saving" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-else v-loading="loading" :data="rows" empty-text="暂无符合条件的教师">
        <el-table-column prop="gonghao" label="登录工号" min-width="150" />
        <el-table-column prop="jiaoshixingming" label="教师姓名" min-width="140" />
        <el-table-column label="所属学院" min-width="200">
          <template slot-scope="scope">{{ text(scope.row.xueyuan) }}</template>
        </el-table-column>
        <el-table-column label="账号状态" min-width="190">
          <template slot-scope="scope">
            <el-tag v-if="temporary(scope.row)" size="small" type="warning">系统分配临时账号</el-tag>
            <el-tag v-else size="small" type="info">正式工号账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template slot-scope="scope">
            <el-button type="text" @click="edit(scope.row)">编辑</el-button>
            <el-button type="text" :disabled="saving" @click="resetPassword(scope.row)">重置密码</el-button>
            <el-button type="text" :disabled="saving" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog
      :title="(form.id ? '编辑' : '新增') + (isLabs ? '实验室' : '教师账号')"
      :visible.sync="visible"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="form" :model="form" :rules="rules" label-position="top">
        <template v-if="isLabs">
          <el-form-item label="实验室编号" prop="shiyanshibianhao">
            <el-input v-model.trim="form.shiyanshibianhao" maxlength="64" />
          </el-form-item>
          <el-form-item label="实验室名称" prop="shiyanshimingcheng">
            <el-input v-model.trim="form.shiyanshimingcheng" maxlength="200" />
          </el-form-item>
          <el-form-item label="实验室位置">
            <el-input v-model.trim="form.shiyanshiweizhi" placeholder="未知可留空" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="负责人教师">
              <el-select v-model="form.manager_teacher_id" clearable filterable placeholder="尚未确认">
                <el-option
                  v-for="teacher in lookups.teachers"
                  :key="teacher.id"
                  :value="teacher.id"
                  :label="teacher.jiaoshixingming + ' · ' + teacher.gonghao"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="设备数量">
              <el-input-number
                v-model="form.equipment_count"
                :min="0"
                :max="1000000"
                :precision="0"
                placeholder="未知请清空"
              />
            </el-form-item>
          </div>
          <p class="muted">设备数量留空表示未知；0 表示已确认无设备。</p>
        </template>
        <template v-else>
          <el-form-item label="登录工号" prop="gonghao">
            <el-input
              v-model.trim="form.gonghao"
              maxlength="64"
              placeholder="正式工号，未知时使用唯一 TMP 前缀临时账号"
            />
          </el-form-item>
          <el-form-item label="教师姓名" prop="jiaoshixingming">
            <el-input v-model.trim="form.jiaoshixingming" maxlength="100" />
          </el-form-item>
          <el-form-item label="所属学院">
            <el-input v-model.trim="form.xueyuan" maxlength="200" placeholder="未知可留空" />
          </el-form-item>
          <el-alert
            v-if="!form.id"
            title="创建后由系统生成初始密码，只在本次操作后显示一次。"
            type="info"
            :closable="false"
          />
        </template>
      </el-form>
      <span slot="footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="请保存本次生成的密码"
      :visible.sync="passwordVisible"
      width="490px"
      :close-on-click-modal="false"
      @closed="password = ''"
    >
      <el-alert
        title="密码只显示一次，关闭后无法再次查看。请交给教师本人，并提醒首次登录后修改。"
        type="warning"
        :closable="false"
        show-icon
      />
      <p class="muted">账号：{{ passwordAccount }}</p>
      <div class="credential">{{ password }}</div>
      <span slot="footer">
        <el-button type="primary" @click="passwordVisible = false">已保存，关闭</el-button>
      </span>
    </el-dialog>
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
    q: '',
    rows: [],
    total: 0,
    visible: false,
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
  async mounted() {
    try {
      await this.loadLookups()
    } catch (e) {
      this.fail(e)
    }
    this.load()
  },
  methods: {
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
    edit(row) {
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
        this.fail(e)
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
