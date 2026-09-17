<template><div class="teaching-page">
<page-heading title="实验项目" eyebrow="EXPERIMENT PROJECTS" description="围绕课程构建实验内容，记录每一学期的教学探索。"><button class="btn" :disabled="downloading" @click="downloadFile('/templates/projects','实验项目导入模板.xlsx')"><sf-icon name="download"/>项目模板</button><button class="btn primary" :disabled="!editable" @click="edit()"><sf-icon name="plus"/>新增实验项目</button></page-heading>
<div v-if="task && !editable" class="notice"><sf-icon name="info"/><span>当前任务为只读。历史项目可查阅，选择有权限的当前学期任务后可维护项目。</span></div>
<section class="panel" v-loading="loading"><form class="filter-bar" @submit.prevent="loadProjects"><label class="search-field"><sf-icon name="search"/><input v-model.trim="projectQuery" aria-label="搜索实验名称或编号" placeholder="搜索实验名称或编号"/></label><sf-select v-model="termId" placeholder="全部学期" @change="selectTerm(termId)"><sf-option v-for="t in lookups.terms" :key="t.id" :value="t.id" :label="t.name"/></sf-select><sf-select v-model="taskId" placeholder="选择课程任务" @change="loadProjects"><sf-option v-for="t in filteredTasks" :key="t.id" :value="t.id" :label="t.course_name+' · '+t.teacher_names+' · '+t.task_code"/></sf-select><sf-select v-model="projectType" placeholder="全部实验类型"><sf-option v-for="o in optionFields.find(f=>f.key==='type_code').options" :key="o.value" :value="o.value" :label="o.label"/></sf-select><button class="btn primary" :disabled="loading">查询</button></form>
<div v-if="task" class="task-summary"><div><span>当前课程</span><strong>{{ task.course_name }}</strong></div><div><span>授课班级 / 教师</span><strong>{{ task.class_composition }} · {{ task.teacher_names }}</strong></div><div><span>已设置项目 / 计划学时</span><strong>{{ projectHours }} / {{ task.planned_lab_hours }} 学时</strong></div></div>
<div class="section-toolbar"><h2>项目清单 <span class="count">{{ displayRows.length }}</span></h2><div class="actions"><button class="btn" :disabled="!editable" @click="openUpload"><sf-icon name="upload"/>Excel 导入</button><button class="btn" :disabled="!editable" @click="openCopy"><sf-icon name="copy"/>从历史任务复制</button></div></div>
<div class="table-wrap" v-if="displayRows.length"><table><thead><tr><th>实验项目</th><th>所属课程</th><th>实验类型</th><th>实验要求</th><th>每组人数</th><th>学时</th><th>操作</th></tr></thead><tbody><tr v-for="p in displayRows" :key="p.id"><td><strong>{{ p.name }}</strong><small>{{ p.project_code }}</small></td><td>{{ task ? task.course_name : '—' }}</td><td><span class="badge" :class="String(p.type_code)==='4'?'purple':'blue'">{{ optionLabel('type_code',p.type_code) }}</span></td><td>{{ optionLabel('requirement_code',p.requirement_code) }}</td><td>{{ p.group_size }} 人</td><td><strong>{{ p.hours }}</strong></td><td><div class="row-actions"><button class="btn text" @click="view(p)">详情</button><button class="btn text" :disabled="!editable" @click="edit(p)">编辑</button><button class="btn text danger" :disabled="!editable||saving" @click="remove(p)">删除</button></div></td></tr></tbody></table></div><div v-else class="empty"><sf-icon name="search"/><strong>{{ taskId?'没有找到符合条件的实验项目':'请先选择课程教学任务' }}</strong></div>
<div class="table-footer"><span>选课人数不等同于选做项目实际参与人数</span><span>共 {{ displayRows.length }} 项 · {{ projectHours }} 学时</span></div></section>
    <sf-dialog
      :title="readOnly ? '实验项目详情' : form.id ? '编辑实验项目' : '新增实验项目'"
      :visible.sync="editVisible"
      width="760px"
      :close-on-click-modal="false"
    >
      <sf-form ref="form" :model="form" :rules="rules" label-position="top" :disabled="readOnly">
        <div class="form-grid">
          <sf-form-item label="实验名称" prop="name" class="span-two">
            <sf-input v-model.trim="form.name" maxlength="50" show-word-limit />
          </sf-form-item>
          <sf-form-item label="实验编号">
            <sf-input
              v-model.trim="form.project_code"
              :disabled="!!form.id"
              placeholder="留空由系统自动生成"
              maxlength="64"
            />
          </sf-form-item>
          <sf-form-item label="学校代码" prop="school_code">
            <sf-input v-model.trim="form.school_code" placeholder="输入实际学校代码" maxlength="5" />
          </sf-form-item>
          <sf-form-item v-for="field in optionFields" :key="field.key" :label="field.label" :prop="field.key">
            <sf-select v-model="form[field.key]">
              <sf-option
                v-for="option in field.options"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </sf-select>
          </sf-form-item>
          <sf-form-item label="实验所属学科代码" prop="discipline_code">
            <sf-input v-model.trim="form.discipline_code" placeholder="保留前导零，如 0809" maxlength="16" />
          </sf-form-item>
          <sf-form-item label="每组人数" prop="group_size">
            <sf-input-number v-model="form.group_size" :min="1" :max="99" :precision="0" />
          </sf-form-item>
          <sf-form-item label="实验学时" prop="hours">
            <sf-input-number v-model="form.hours" :min="0.01" :max="9999" :precision="2" />
          </sf-form-item>
          <sf-form-item label="显示顺序">
            <sf-input-number v-model="form.sort_order" :min="0" :max="9999" :precision="0" />
          </sf-form-item>
        </div>
      </sf-form>
      <span slot="footer">
        <sf-button @click="editVisible = false">{{ readOnly ? '关闭' : '取消' }}</sf-button>
        <sf-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存项目</sf-button>
      </span>
    </sf-dialog>
    <sf-dialog title="导入实验项目" :visible.sync="uploadVisible" width="560px" :close-on-click-modal="false">
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
        <sf-button @click="uploadVisible = false">关闭</sf-button>
        <sf-button type="primary" :disabled="!file || !editable" :loading="saving" @click="importProjects">
          上传并导入
        </sf-button>
      </span>
    </sf-dialog>
    <sf-dialog
      title="从历史任务复制实验项目"
      :visible.sync="copyVisible"
      width="640px"
      :close-on-click-modal="false"
    >
      <sf-alert
        title="复制会保留历史原项目，在当前任务中创建独立版本；请核对课程与学时后操作。"
        type="info"
        :closable="false"
        show-icon
      />
      <sf-form label-position="top">
        <sf-form-item label="目标任务">
          <sf-input :value="task ? task.course_name + ' · ' + task.task_code : ''" disabled />
        </sf-form-item>
        <sf-form-item label="历史来源任务">
          <sf-select v-model="sourceTaskId" filterable placeholder="选择历史学期中已有项目的任务">
            <sf-option
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
          </sf-select>
        </sf-form-item>
      </sf-form>
      <p v-if="!sourceTasks.length" class="muted">暂无同一课程中有权限访问且包含项目的历史任务。</p>
      <span slot="footer">
        <sf-button @click="copyVisible = false">取消</sf-button>
        <sf-button type="primary" :disabled="!sourceTaskId || !editable" :loading="saving" @click="copy">
          复制到当前任务
        </sf-button>
      </span>
    </sf-dialog>
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
    projectQuery: '', projectType: '',
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
    displayRows(){return this.rows.filter(p=>(!this.projectType||String(p.type_code)===String(this.projectType))&&[p.name,p.project_code].join(' ').toLowerCase().includes(this.projectQuery.toLowerCase()))},
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
  watch:{'$route.query.termId'(v){this.termId=v||'';this.changeTerm()}},
  async mounted() {
    this.loading = true
    try {
      await this.loadLookups()
      await this.loadTasks()
      this.termId=this.$route.query.termId||''
      const requested = this.$route.query.taskId
      const selected =
        this.filteredTasks.find(item => String(item.id) === String(requested)) ||
        this.filteredTasks.find(item => ['CURRENT', 'OPEN'].includes(item.status)) ||
        this.filteredTasks[0]
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
