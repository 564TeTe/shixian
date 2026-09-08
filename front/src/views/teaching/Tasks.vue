<template>
  <div class="teaching-page">
    <page-heading
      title="课程与课表"
      description="按学期查询教学任务，查看任课教师、授课班级和逐周排课。"
      eyebrow="COURSES & TIMETABLE"
    >
      <el-button v-if="isAdmin" type="primary" icon="el-icon-plus" @click="openCreate">
        创建当前教学任务
      </el-button>
    </page-heading>
    <section class="panel">
      <div class="filter-bar">
        <el-select v-model="filters.termId" clearable placeholder="全部学期" @change="search">
          <el-option
            v-for="term in lookups.terms"
            :key="term.id"
            :value="term.id"
            :label="term.name + ' · ' + status(term.status)"
          />
        </el-select>
        <el-input
          v-model.trim="filters.q"
          clearable
          placeholder="搜索课程、教师、任务或班级"
          @keyup.enter.native="search"
          @clear="search"
        />
        <el-button type="primary" icon="el-icon-search" :loading="loading" @click="search">查询</el-button>
        <span class="muted">共 {{ total }} 个教学任务</span>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无符合条件的教学任务">
        <el-table-column label="课程 / 任务" min-width="240">
          <template slot-scope="scope">
            <strong>{{ scope.row.course_name }}</strong>
            <div class="muted mono">{{ scope.row.course_code }} · {{ scope.row.task_code }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="term_name" label="学期" min-width="160" />
        <el-table-column prop="teacher_names" label="教师" min-width="110" />
        <el-table-column prop="class_composition" label="授课班级" min-width="170" show-overflow-tooltip />
        <el-table-column prop="enrollment_count" label="选课人数" width="95" />
        <el-table-column label="学时 · 计划 / 排课" width="155">
          <template slot-scope="scope">
            {{ scope.row.planned_lab_hours }} / {{ scope.row.scheduled_hours }}
            <div
              v-if="Number(scope.row.planned_lab_hours) !== Number(scope.row.scheduled_hours)"
              class="muted"
            >
              两种口径存在差异
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="['CURRENT', 'OPEN'].includes(scope.row.status) ? 'success' : 'info'">
              {{ status(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="135" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" @click="showDetail(scope.row)">课表</el-button>
            <el-button type="text" @click="projects(scope.row)">实验项目</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page.sync="filters.page"
        :page-size="filters.limit"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </section>
    <el-dialog title="教学任务与排课详情" :visible.sync="detailVisible" width="1000px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <dl class="detail-grid">
            <div v-for="item in detailFields" :key="item.key">
              <dt>{{ item.label }}</dt>
              <dd>{{ text(detail[item.key]) }}</dd>
            </div>
          </dl>
          <el-alert
            v-if="Number(detail.planned_lab_hours) !== Number(detail.scheduled_hours)"
            title="计划实验学时与课表排课学时存在差异，请核对原始课表；统计采用实际排课口径。"
            type="warning"
            show-icon
            :closable="false"
          />
          <el-table
            :data="detail.schedule || []"
            max-height="360"
            empty-text="该任务暂未录入排课明细，排课学时为 0"
          >
            <el-table-column prop="teaching_week" label="教学周" width="85" />
            <el-table-column label="星期" width="80">
              <template slot-scope="scope">{{ weekdays[scope.row.weekday] || scope.row.weekday }}</template>
            </el-table-column>
            <el-table-column label="节次" width="100">
              <template slot-scope="scope">
                {{ scope.row.period_start }}–{{ scope.row.period_end }} 节
              </template>
            </el-table-column>
            <el-table-column prop="hours" label="学时" width="80" />
            <el-table-column label="实验室" min-width="200">
              <template slot-scope="scope">
                {{ scope.row.lab_name || scope.row.shiyanshimingcheng || labName(scope.row.lab_id) }}
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <span slot="footer">
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detail" type="primary" @click="projects(detail)">进入实验项目</el-button>
      </span>
    </el-dialog>
    <el-dialog
      title="创建当前教学任务"
      :visible.sync="createVisible"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="仅可为当前学期创建任务。此处维护课程、教师与计划学时；实际排课请通过课表 Excel 导入。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-form ref="createForm" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="当前学期" prop="termId">
            <el-select v-model="form.termId" placeholder="选择当前学期">
              <el-option v-for="term in currentTerms" :key="term.id" :value="term.id" :label="term.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="课程" prop="courseId">
            <el-select v-model="form.courseId" filterable placeholder="选择已有课程">
              <el-option
                v-for="course in lookups.courses"
                :key="course.id"
                :value="course.id"
                :label="course.course_code + ' · ' + course.course_name"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="任课教师（支持合授）" prop="teacherIds">
            <el-select v-model="form.teacherIds" multiple filterable placeholder="选择教师">
              <el-option
                v-for="teacher in lookups.teachers"
                :key="teacher.id"
                :value="teacher.id"
                :label="teacher.jiaoshixingming + ' · ' + teacher.gonghao"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="实验室" prop="labId">
            <el-select v-model="form.labId" filterable placeholder="选择实验室">
              <el-option
                v-for="lab in lookups.labs"
                :key="lab.id"
                :value="lab.id"
                :label="lab.shiyanshibianhao + ' · ' + lab.shiyanshimingcheng"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="授课班级" prop="classComposition">
            <el-input v-model.trim="form.classComposition" placeholder="输入完整班级构成" />
          </el-form-item>
          <el-form-item label="专业构成">
            <el-input v-model.trim="form.majorComposition" placeholder="可选" />
          </el-form-item>
          <el-form-item label="选课人数" prop="enrollmentCount">
            <el-input-number v-model="form.enrollmentCount" :min="0" :max="100000" :precision="0" />
          </el-form-item>
          <el-form-item label="计划实验学时" prop="plannedLabHours">
            <el-input-number v-model="form.plannedLabHours" :min="0.01" :max="9999" :precision="2" />
          </el-form-item>
        </div>
      </el-form>
      <span slot="footer">
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建任务</el-button>
      </span>
    </el-dialog>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import { request, shared } from './api'
const required = message => [{ required: true, message, trigger: 'change' }]
export default {
  components: { PageHeading },
  mixins: [shared],
  data: () => ({
    rows: [],
    total: 0,
    filters: { termId: '', q: '', page: 1, limit: 20 },
    detailVisible: false,
    detailLoading: false,
    detail: null,
    createVisible: false,
    form: {},
    weekdays: ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    detailFields: [
      { key: 'course_name', label: '课程名称' },
      { key: 'course_code', label: '课程号' },
      { key: 'task_code', label: '任务编号' },
      { key: 'term_name', label: '学期' },
      { key: 'teacher_names', label: '任课教师' },
      { key: 'class_composition', label: '班级构成' },
      { key: 'enrollment_count', label: '选课人数' },
      { key: 'planned_lab_hours', label: '计划实验学时' },
      { key: 'scheduled_hours', label: '实际排课学时' },
      { key: 'lab_names', label: '实验室' },
      { key: 'major_composition', label: '专业构成' },
      { key: 'project_count', label: '实验项目数量' }
    ],
    rules: {
      termId: required('请选择学期'),
      courseId: required('请选择课程'),
      teacherIds: required('请选择任课教师'),
      labId: required('请选择实验室'),
      classComposition: required('请输入授课班级'),
      enrollmentCount: required('请输入选课人数'),
      plannedLabHours: required('请输入计划学时')
    }
  }),
  computed: {
    currentTerms() {
      return this.lookups.terms.filter(term => ['CURRENT', 'OPEN'].includes(term.status))
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
    search() {
      this.filters.page = 1
      this.load()
    },
    async load() {
      this.loading = true
      try {
        const data = await request('/tasks', { params: this.filters })
        this.rows = data.list
        this.total = data.total
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    async showDetail(row) {
      this.detail = null
      this.detailVisible = true
      this.detailLoading = true
      try {
        this.detail = await request('/tasks/' + row.id)
      } catch (e) {
        this.fail(e)
      } finally {
        this.detailLoading = false
      }
    },
    labName(id) {
      const lab = this.lookups.labs.find(item => String(item.id) === String(id))
      return lab ? lab.shiyanshimingcheng : '待确认'
    },
    projects(row) {
      this.$router.push({ path: '/teaching/projects', query: { taskId: row.id } })
    },
    openCreate() {
      this.form = {
        termId: this.currentTerms[0] ? this.currentTerms[0].id : '',
        courseId: '',
        teacherIds: [],
        labId: '',
        classComposition: '',
        majorComposition: '',
        enrollmentCount: 0,
        plannedLabHours: 2
      }
      this.createVisible = true
      this.$nextTick(() => this.$refs.createForm.clearValidate())
    },
    async save() {
      if (!(await this.$refs.createForm.validate().catch(() => false))) return
      this.saving = true
      try {
        await request('/tasks', { method: 'post', data: this.form })
        this.$message.success('教学任务已创建')
        this.createVisible = false
        await this.load()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
