<template>
  <div class="teaching-page">
    <page-heading
      title="实验项目"
      description="以教学任务为单位维护项目版本，当前学期支持编辑，历史学期可查阅和复用。"
      eyebrow="EXPERIMENT PROJECTS"
    >
      <el-button
        icon="el-icon-download"
        :loading="downloading"
        @click="downloadFile('/templates/projects', '实验项目导入模板.xlsx')"
      >
        下载项目模板
      </el-button>
    </page-heading>
    <section class="panel">
      <div class="filter-bar">
        <el-select v-model="termId" clearable placeholder="全部学期" @change="changeTerm">
          <el-option v-for="term in lookups.terms" :key="term.id" :value="term.id" :label="term.name" />
        </el-select>
        <el-select
          class="wide"
          v-model="taskId"
          filterable
          placeholder="选择课程教学任务"
          @change="loadProjects"
        >
          <el-option
            v-for="task in filteredTasks"
            :key="task.id"
            :value="task.id"
            :label="task.course_name + ' · ' + task.task_code + ' · ' + task.teacher_names"
          />
        </el-select>
        <el-button icon="el-icon-refresh" :loading="loading" @click="loadProjects">刷新</el-button>
      </div>
      <template v-if="task">
        <dl class="detail-grid">
          <div>
            <dt>课程 / 学期</dt>
            <dd>{{ task.course_name }} · {{ task.term_name }}</dd>
          </div>
          <div>
            <dt>任课教师 / 班级</dt>
            <dd>{{ task.teacher_names }} · {{ task.class_composition }}</dd>
          </div>
          <div>
            <dt>计划学时 / 已设置项目学时</dt>
            <dd>{{ task.planned_lab_hours }} / {{ projectHours }} 学时</dd>
          </div>
        </dl>
        <el-alert
          v-if="!editable"
          :title="
            task.status === 'ARCHIVED'
              ? '历史学期已归档，仅可查看。请选择当前教学任务，再从历史任务复制项目。'
              : '此任务当前不可编辑。仅当前学期且有权限的任务允许维护实验项目。'
          "
          type="info"
          show-icon
          :closable="false"
        />
        <div class="action-row">
          <el-button type="primary" icon="el-icon-plus" :disabled="!editable" @click="edit()">
            新增项目
          </el-button>
          <el-button icon="el-icon-upload2" :disabled="!editable" @click="openUpload">Excel 导入</el-button>
          <el-button icon="el-icon-copy-document" :disabled="!editable" @click="openCopy">
            从历史任务复制
          </el-button>
          <span class="muted">选做项目实际参与人数不作推断</span>
        </div>
      </template>
    </section>
    <section class="panel" v-loading="loading">
      <div class="panel-title">
        <h2>项目清单</h2>
        <span class="muted">共 {{ rows.length }} 个项目</span>
      </div>
      <el-table :data="rows" :empty-text="taskId ? '此教学任务暂无实验项目' : '请先选择教学任务'">
        <el-table-column prop="sort_order" label="排序" width="65" />
        <el-table-column prop="project_code" label="实验编号" min-width="175" />
        <el-table-column prop="name" label="实验名称" min-width="210" />
        <el-table-column label="实验类型" width="105">
          <template slot-scope="scope">{{ optionLabel('type_code', scope.row.type_code) }}</template>
        </el-table-column>
        <el-table-column label="实验要求" width="95">
          <template slot-scope="scope">
            {{ optionLabel('requirement_code', scope.row.requirement_code) }}
          </template>
        </el-table-column>
        <el-table-column prop="group_size" label="每组人数" width="100" />
        <el-table-column prop="hours" label="实验学时" width="100" />
        <el-table-column label="操作" width="155" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" @click="view(scope.row)">详情</el-button>
            <el-button type="text" :disabled="!editable" @click="edit(scope.row)">编辑</el-button>
            <el-button type="text" :disabled="!editable || saving" @click="remove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog
      :title="readOnly ? '实验项目详情' : form.id ? '编辑实验项目' : '新增实验项目'"
      :visible.sync="editVisible"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-form ref="form" :model="form" :rules="rules" label-position="top" :disabled="readOnly">
        <div class="form-grid">
          <el-form-item label="实验名称" prop="name" class="span-two">
            <el-input v-model.trim="form.name" maxlength="50" show-word-limit />
          </el-form-item>
          <el-form-item label="实验编号">
            <el-input
              v-model.trim="form.project_code"
              :disabled="!!form.id"
              placeholder="留空由系统自动生成"
              maxlength="64"
            />
          </el-form-item>
          <el-form-item label="学校代码" prop="school_code">
            <el-input v-model.trim="form.school_code" placeholder="输入实际学校代码" maxlength="32" />
          </el-form-item>
          <el-form-item v-for="field in optionFields" :key="field.key" :label="field.label" :prop="field.key">
            <el-select v-model="form[field.key]">
              <el-option
                v-for="option in field.options"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="实验所属学科代码" prop="discipline_code">
            <el-input v-model.trim="form.discipline_code" placeholder="保留前导零，如 0809" maxlength="16" />
          </el-form-item>
          <el-form-item label="每组人数" prop="group_size">
            <el-input-number v-model="form.group_size" :min="1" :max="99" :precision="0" />
          </el-form-item>
          <el-form-item label="实验学时" prop="hours">
            <el-input-number v-model="form.hours" :min="0.01" :max="9999" :precision="2" />
          </el-form-item>
          <el-form-item label="显示顺序">
            <el-input-number v-model="form.sort_order" :min="0" :max="9999" :precision="0" />
          </el-form-item>
        </div>
      </el-form>
      <span slot="footer">
        <el-button @click="editVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存项目</el-button>
      </span>
    </el-dialog>
    <el-dialog title="导入实验项目" :visible.sync="uploadVisible" width="560px" :close-on-click-modal="false">
      <p class="muted">
        项目将导入当前选定的教学任务。请先下载模板，填写真实实验项目；课程号、课程名称和教师必须与任务对应。
      </p>
      <div class="file-pick">
        <strong>{{ task ? task.course_name : '' }}</strong>
        <input
          :key="fileKey"
          type="file"
          accept=".xlsx"
          aria-label="选择项目 Excel 文件"
          @change="file = $event.target.files[0]"
        />
      </div>
      <warnings :items="uploadWarnings" />
      <span slot="footer">
        <el-button @click="uploadVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!file || !editable" :loading="saving" @click="importProjects">
          上传并导入
        </el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="从历史任务复制实验项目"
      :visible.sync="copyVisible"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="复制会保留历史原项目，在当前任务中创建独立版本；请核对课程与学时后操作。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form label-position="top">
        <el-form-item label="目标任务">
          <el-input :value="task ? task.course_name + ' · ' + task.task_code : ''" disabled />
        </el-form-item>
        <el-form-item label="历史来源任务">
          <el-select v-model="sourceTaskId" filterable placeholder="选择历史学期中已有项目的任务">
            <el-option
              v-for="item in sourceTasks"
              :key="item.id"
              :value="item.id"
              :label="
                item.course_name +
                  ' · ' +
                  item.term_name +
                  ' · ' +
                  item.task_code +
                  '（' +
                  item.project_count +
                  ' 项）'
              "
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="!sourceTasks.length" class="muted">暂无同一课程中有权限访问且包含项目的历史任务。</p>
      <span slot="footer">
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!sourceTaskId || !editable" :loading="saving" @click="copy">
          复制到当前任务
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import Warnings from './Warnings'
import { request, upload, shared } from './api'
const fields = [
  { key: 'category_code', label: '实验类别', names: ['基础', '专业基础', '专业', '其它'] },
  { key: 'type_code', label: '实验类型', names: ['演示性', '验证性', '综合性', '设计研究', '其它'] },
  { key: 'requirement_code', label: '实验要求', names: ['必做', '选做', '其它'] },
  { key: 'participant_type_code', label: '实验者类别', names: ['博士', '硕士', '本科', '专科', '其他'] }
].map(field =>
  Object.assign(field, { options: field.names.map((label, i) => ({ value: String(i + 1), label })) })
)
export default {
  components: { PageHeading, Warnings },
  mixins: [shared],
  data: () => ({
    tasks: [],
    termId: '',
    taskId: '',
    rows: [],
    editable: false,
    editVisible: false,
    readOnly: false,
    form: {},
    optionFields: fields,
    uploadVisible: false,
    file: null,
    fileKey: 0,
    uploadWarnings: [],
    copyVisible: false,
    sourceTaskId: '',
    rules: [
      'name',
      'school_code',
      'discipline_code',
      'group_size',
      'hours',
      ...fields.map(f => f.key)
    ].reduce((rules, key) => {
      rules[key] = [{ required: true, message: '请填写此项', trigger: 'change' }]
      return rules
    }, {})
  }),
  computed: {
    task() {
      return this.tasks.find(item => String(item.id) === String(this.taskId))
    },
    filteredTasks() {
      return this.tasks.filter(
        item =>
          !this.termId ||
          String(item.term_id) === String(this.termId) ||
          item.term_name === (this.lookups.terms.find(term => term.id === this.termId) || {}).name
      )
    },
    sourceTasks() {
      return this.tasks
        .filter(
          item =>
            item.status === 'ARCHIVED' &&
            item.project_count > 0 &&
            this.task &&
            String(item.course_id) === String(this.task.course_id) &&
            String(item.id) !== String(this.taskId)
        )
        .sort(
          (a, b) =>
            Number(b.course_code === (this.task || {}).course_code) -
            Number(a.course_code === (this.task || {}).course_code)
        )
    },
    projectHours() {
      return this.rows.reduce((sum, row) => sum + Number(row.hours || 0), 0).toFixed(2)
    }
  },
  async mounted() {
    this.loading = true
    try {
      await this.loadLookups()
      await this.loadTasks()
      const requested = this.$route.query.taskId
      const selected =
        this.tasks.find(item => String(item.id) === String(requested)) ||
        this.tasks.find(item => ['CURRENT', 'OPEN'].includes(item.status)) ||
        this.tasks[0]
      if (selected) this.taskId = selected.id
      await this.loadProjects()
    } catch (e) {
      this.fail(e)
    } finally {
      this.loading = false
    }
  },
  methods: {
    async loadTasks() {
      let page = 1
      let data
      const tasks = []
      do {
        data = await request('/tasks', { params: { page, limit: 100 } })
        tasks.push(...data.list)
        page++
      } while (data.list.length && tasks.length < data.total)
      this.tasks = tasks
    },
    changeTerm() {
      if (!this.filteredTasks.some(item => item.id === this.taskId))
        this.taskId = this.filteredTasks[0] ? this.filteredTasks[0].id : ''
      this.loadProjects()
    },
    async loadProjects() {
      this.rows = []
      this.editable = false
      if (!this.taskId) return
      this.loading = true
      const selectedId = this.taskId
      try {
        const data = await request('/projects', { params: { taskId: selectedId } })
        if (String(this.taskId) !== String(selectedId)) return
        this.rows = data.list
        this.editable = data.editable === true && this.task && ['CURRENT', 'OPEN'].includes(this.task.status)
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    optionLabel(key, value) {
      const field = fields.find(item => item.key === key)
      const option = field.options.find(item => item.value === String(value))
      return option ? option.label : value
    },
    view(row) {
      this.edit(row, true)
    },
    edit(row, readOnly = false) {
      this.readOnly = readOnly
      this.form = row
        ? Object.assign({}, row)
        : {
            task_id: this.taskId,
            name: '',
            project_code: '',
            school_code: '',
            category_code: '1',
            type_code: '2',
            discipline_code: '',
            requirement_code: '1',
            participant_type_code: '3',
            group_size: 1,
            hours: 2,
            sort_order: this.rows.length + 1
          }
      fields.forEach(field => {
        this.form[field.key] = String(this.form[field.key])
      })
      this.editVisible = true
      this.$nextTick(() => this.$refs.form.clearValidate())
    },
    async save() {
      if (!this.editable || !(await this.$refs.form.validate().catch(() => false))) return
      this.saving = true
      try {
        const allowed = [
          'task_id',
          'name',
          'project_code',
          'school_code',
          'discipline_code',
          'group_size',
          'hours',
          'sort_order',
          ...fields.map(field => field.key)
        ]
        const data = allowed.reduce((obj, key) => {
          obj[key] = this.form[key]
          return obj
        }, {})
        await request('/projects' + (this.form.id ? '/' + this.form.id : ''), {
          method: this.form.id ? 'put' : 'post',
          data
        })
        this.editVisible = false
        this.$message.success('实验项目已保存')
        await this.loadProjects()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    },
    async remove(row) {
      try {
        await this.$confirm('确定删除实验项目“' + row.name + '”？', '删除项目', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消'
        })
      } catch (e) {
        return
      }
      this.saving = true
      try {
        await request('/projects/' + row.id, { method: 'delete' })
        this.$message.success('项目已删除')
        await this.loadProjects()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    },
    openUpload() {
      this.file = null
      this.fileKey++
      this.uploadWarnings = []
      this.uploadVisible = true
    },
    async importProjects() {
      this.saving = true
      try {
        const result = await upload('/imports/projects', this.file, { taskId: this.taskId })
        this.uploadWarnings = result.warnings || []
        this.$message.success('已导入 ' + result.imported + ' 个项目')
        this.file = null
        this.fileKey++
        await this.loadProjects()
        if (!this.uploadWarnings.length) this.uploadVisible = false
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    },
    async openCopy() {
      this.sourceTaskId = ''
      this.copyVisible = true
      try {
        await this.loadTasks()
      } catch (e) {
        this.fail(e)
      }
    },
    async copy() {
      this.saving = true
      try {
        const result = await request('/projects/copy', {
          method: 'post',
          data: { sourceTaskId: this.sourceTaskId, targetTaskId: this.taskId }
        })
        this.$message.success(
          '已复制 ' + result.copied + ' 个项目，跳过已复制的 ' + result.skipped + ' 个项目'
        )
        this.copyVisible = false
        await this.loadProjects()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
