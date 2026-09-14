<template><div class="teaching-page">
<page-heading title="课程与课表" eyebrow="COURSES & TIMETABLE" description="以学期为单位组织课程，清晰掌握每一周的实验教学安排。"><router-link v-if="isAdmin" class="btn" to="/teaching/imports"><sf-icon name="upload"/>导入课表</router-link><button v-if="isAdmin" class="btn primary" @click="openCreate"><sf-icon name="plus"/>创建当前教学任务</button></page-heading>
<section class="panel" v-loading="loading">
<form class="filter-bar" @submit.prevent="search"><label class="search-field"><sf-icon name="search"/><input v-model.trim="filters.q" placeholder="搜索课程、教师或班级" aria-label="搜索课程、教师或班级"/></label><sf-select v-model="filters.termId" placeholder="全部学期" @change="search"><sf-option v-for="term in lookups.terms" :key="term.id" :value="term.id" :label="term.name"/></sf-select><button class="btn primary">查询</button><button v-if="filters.q" class="btn" type="button" @click="filters.q='';search()">重置</button></form>
<div class="section-toolbar"><div class="tabs"><button class="btn" :class="{active:tab==='list'}" @click="tab='list'"><sf-icon name="book"/>教学任务</button><button class="btn" :class="{active:tab==='week'}" @click="openWeek"><sf-icon name="calendar"/>周课表</button></div><span class="muted">共 {{ total }} 个教学任务</span></div>
<task-table v-if="tab==='list'" :rows="rows" @detail="showDetail"/>
<div v-else v-loading="weekLoading"><div class="week-toolbar"><button class="btn" :disabled="week<=1" @click="week--">上一周</button><strong>第 {{ week }} 周</strong><button class="btn" :disabled="week>=maxWeek" @click="week++">下一周</button></div><div class="table-wrap"><div class="week-grid" style="grid-template-columns:70px repeat(7,minmax(130px,1fr))"><div class="week-head">节次</div><div v-for="day in 7" :key="'day'+day" class="week-head">{{ weekdays[day] }}</div><template v-for="period in periodRows"><div class="period" :key="'p'+period"><strong>{{ period }} 节</strong></div><div v-for="day in 7" :key="period+'-'+day" class="week-cell"><button v-for="lesson in lessons(day,period)" :key="lesson.id" class="lesson" :class="'lesson-'+lesson.lab_id%3" @click="showDetail({id:lesson.task_id})"><strong>{{ lesson.course_name }}</strong><span>{{ lesson.teacher_names }} · {{ lesson.lab_name }}</span><small>{{ lesson.class_composition }} · {{ lesson.period_start }}–{{ lesson.period_end }} 节</small></button></div></template></div></div><div v-if="!weekRows.length && !weekLoading" class="empty">所选范围暂无排课</div></div>
<sf-pagination v-if="tab==='list'" :current-page.sync="filters.page" :page-size="filters.limit" :total="total" @current-change="load"/>
</section>
    <sf-dialog title="教学任务与排课详情" :visible.sync="detailVisible" width="1000px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <dl class="detail-grid">
            <div v-for="item in detailFields" :key="item.key">
              <dt>{{ item.label }}</dt>
              <dd>{{ text(detail[item.key]) }}</dd>
            </div>
          </dl>
          <sf-alert
            v-if="Number(detail.planned_lab_hours) !== Number(detail.scheduled_hours)"
            title="计划实验学时与课表排课学时存在差异，请核对原始课表；统计采用实际排课口径。"
            type="warning"
            show-icon
            :closable="false"
          />
          <sf-table
            :data="detail.schedule || []"
            max-height="360"
            empty-text="该任务暂未录入排课明细，排课学时为 0"
          >
            <sf-column prop="teaching_week" label="教学周" width="85" />
            <sf-column label="星期" width="80">
              <template slot-scope="scope">{{ weekdays[scope.row.weekday] || scope.row.weekday }}</template>
            </sf-column>
            <sf-column label="节次" width="100">
              <template slot-scope="scope">
                {{ scope.row.period_start }}–{{ scope.row.period_end }} 节
              </template>
            </sf-column>
            <sf-column prop="hours" label="学时" width="80" />
            <sf-column label="实验室" min-width="200">
              <template slot-scope="scope">
                {{ scope.row.lab_name || scope.row.shiyanshimingcheng || labName(scope.row.lab_id) }}
              </template>
            </sf-column>
          </sf-table>
        </template>
      </div>
      <span slot="footer">
        <sf-button @click="detailVisible = false">关闭</sf-button>
        <sf-button v-if="detail" type="primary" @click="projects(detail)">进入实验项目</sf-button>
      </span>
    </sf-dialog>
    <sf-dialog
      title="创建当前教学任务"
      :visible.sync="createVisible"
      width="720px"
      :close-on-click-modal="false"
    >
      <sf-alert
        title="仅可为当前学期创建任务。此处维护课程、教师与计划学时；实际排课请通过课表 Excel 导入。"
        type="info"
        show-icon
        :closable="false"
      />
      <sf-form ref="createForm" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <sf-form-item label="当前学期" prop="termId">
            <sf-select v-model="form.termId" placeholder="选择当前学期">
              <sf-option v-for="term in currentTerms" :key="term.id" :value="term.id" :label="term.name" />
            </sf-select>
          </sf-form-item>
          <sf-form-item label="课程" prop="courseId">
            <sf-select v-model="form.courseId" filterable placeholder="选择已有课程">
              <sf-option
                v-for="course in lookups.courses"
                :key="course.id"
                :value="course.id"
                :label="course.course_code + ' · ' + course.course_name"
              />
            </sf-select>
          </sf-form-item>
          <sf-form-item label="任课教师（支持合授）" prop="teacherIds">
            <sf-select v-model="form.teacherIds" multiple filterable placeholder="选择教师">
              <sf-option
                v-for="teacher in lookups.teachers"
                :key="teacher.id"
                :value="teacher.id"
                :label="teacher.jiaoshixingming + ' · ' + teacher.gonghao"
              />
            </sf-select>
          </sf-form-item>
          <sf-form-item label="授课班级" prop="classComposition">
            <sf-input v-model.trim="form.classComposition" placeholder="输入完整班级构成" />
          </sf-form-item>
          <sf-form-item label="专业构成">
            <sf-input v-model.trim="form.majorComposition" placeholder="可选" />
          </sf-form-item>
          <sf-form-item label="选课人数" prop="enrollmentCount">
            <sf-input-number v-model="form.enrollmentCount" :min="0" :max="100000" :precision="0" />
          </sf-form-item>
          <sf-form-item label="计划实验学时" prop="plannedLabHours">
            <sf-input-number v-model="form.plannedLabHours" :min="0.01" :max="9999" :precision="2" />
          </sf-form-item>
        </div>
      </sf-form>
      <span slot="footer">
        <sf-button @click="createVisible = false">取消</sf-button>
        <sf-button type="primary" :loading="saving" @click="save">创建任务</sf-button>
      </span>
    </sf-dialog>
  </div>
</template>
<script>
import TaskTable from '@/components/workspace/TaskTable'
import PageHeading from './PageHeading'
import { request, shared } from './api'
const required = message => [{ required: true, message, trigger: 'change' }]
export default {
  components: { PageHeading, TaskTable },
  mixins: [shared],
  data: () => ({
    tab: 'list', week: 1, weekRows: [], weekLoading: false,
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
      classComposition: required('请输入授课班级'),
      enrollmentCount: required('请输入选课人数'),
      plannedLabHours: required('请输入计划学时')
    }
  }),
  computed: {
    maxWeek(){return Math.max(1,...this.weekRows.map(r=>Number(r.teaching_week)||1))},
    periodRows(){return Array.from({length:Math.max(8,...this.weekRows.map(r=>Number(r.period_end)||1))},(_,i)=>i+1)},
    currentTerms() {
      return this.lookups.terms.filter(term => ['CURRENT', 'OPEN'].includes(term.status))
    }
  },
  watch: {'$route.query.termId'(v){this.filters.termId=v||'';this.search()}},
  async mounted() {
    try {
      await this.loadLookups()
    } catch (e) {
      this.fail(e)
    }
    this.filters.termId=this.$route.query.termId||''
    await this.load()
    if(this.$route.query.taskId)this.showDetail({id:this.$route.query.taskId})
    if(this.$route.query.view==='week')this.openWeek()
  },
  methods: {
    lessons(day,period){return this.weekRows.filter(r=>Number(r.teaching_week)===this.week&&Number(r.weekday)===day&&Number(r.period_start)===period)},
    async openWeek(){this.tab='week';this.weekLoading=true;try{let all=[],data,page=1;do{data=await request('/tasks',{params:{...this.filters,page,limit:100}});all.push(...data.list);page++}while(data.list.length&&all.length<data.total);let details=[];for(let i=0;i<all.length;i+=8){const group=await Promise.all(all.slice(i,i+8).map(t=>request('/tasks/'+t.id)));details.push(...group)}this.weekRows=details.flatMap(t=>(t.schedule||[]).map(r=>({...r,task_id:t.id,course_name:t.course_name,teacher_names:t.teacher_names,class_composition:t.class_composition})));this.week=Math.min(this.week,this.maxWeek)}catch(e){this.fail(e)}finally{this.weekLoading=false}},
    search() {
      this.filters.page = 1
      this.load()
      if(this.tab==='week')this.openWeek()
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
