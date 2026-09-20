<template><div class="teaching-page" v-loading="loading">
<page-heading title="统计报表" eyebrow="TEACHING ANALYTICS" description="按学年、学期查看全部或单个实验室，并按教学实验项目采集表导出。"><button class="btn primary" :disabled="downloading || loading || !appliedFilters" @click="exportReport('projects')"><sf-icon name="download"/>导出项目采集表</button></page-heading>
<form class="filter-bar" @submit.prevent="load"><sf-select v-model="filters.yearId" placeholder="全部学年" @change="changeYear"><sf-option v-for="y in years" :key="y.id" :value="y.id" :label="y.name"/></sf-select><sf-select v-model="filters.termId" placeholder="全部学期" @change="selectTerm(filters.termId)"><sf-option v-for="t in terms" :key="t.id" :value="t.id" :label="t.name"/></sf-select><sf-select v-model="filters.labId" placeholder="全部实验室" @change="load"><sf-option v-for="lab in lookups.labs" :key="lab.id" :value="lab.id" :label="lab.shiyanshibianhao+' · '+lab.shiyanshimingcheng"/></sf-select><button class="btn primary">查询</button></form>
<div class="stat-grid"><div v-for="(c,i) in reportCards" :key="c[0]" class="stat-card"><div class="stat-top"><span>{{ c[0] }}</span><span class="stat-icon" :class="'tone-'+i"><sf-icon :name="c[3]"/></span></div><div class="stat-value">{{ Number(c[1]).toLocaleString('zh-CN') }}<small>{{ c[2] }}</small></div><div class="stat-foot">{{ c[4] }}</div></div></div>
<section class="panel"><div class="panel-title"><h2>实验室使用分析</h2><span class="badge gray">{{ scopeLabel }}</span></div><workspace-chart :rows="data.labs||[]"/></section>
<section class="panel"><div class="section-toolbar"><div class="tabs"><button class="btn" :class="{active:tab==='labs'}" @click="tab='labs'">实验室使用统计</button><button class="btn" :class="{active:tab==='projects'}" @click="tab='projects'">教学实验项目采集表</button></div><button v-if="tab==='labs'" class="btn" :disabled="downloading || loading || !appliedFilters" @click="exportReport('labs')"><sf-icon name="download"/>导出使用统计</button></div><div class="notice"><sf-icon name="info"/><span>{{ basis }}</span></div><!-- 黄色学时差异提醒条已按需求去除
<warnings :items="data.warnings||[]"/>-->
<div class="table-wrap" v-if="reportRows.length"><table><thead><tr><th v-for="c in reportColumns" :key="c[0]">{{ c[1] }}</th></tr></thead><tbody><tr v-for="(r,i) in reportRows" :key="i"><td v-for="c in reportColumns" :key="c[0]">{{ r[c[0]]==null?'待确认':r[c[0]] }}</td></tr></tbody></table></div><div v-else class="empty"><sf-icon name="chart"/><strong>所选范围暂无统计数据</strong></div><div class="table-footer"><span>实际参与人数未采集时保留空缺，不以选课人数推断</span><span>共 {{ reportRows.length }} 条</span></div></section></div></template>
<script>
import WorkspaceChart from '@/components/workspace/Chart'
import PageHeading from './PageHeading'
import { shared, request } from './api'
export default {
  components: { PageHeading, WorkspaceChart },
  mixins: [shared],
  data: () => ({ filters: { yearId: '', termId: '', labId: '' }, appliedFilters: null, loadVersion: 0, tab: 'projects', data: {} }),
  computed: {
    reportRows(){return this.data[this.tab]||[]},
    reportColumns() {
      return this.tab === 'labs'
        ? [['lab_code', '实验室编号'], ['lab_name', '实验室名称'], ['course_count', '课程数'], ['task_count', '任务数'], ['scheduled_hours', '实际排课学时'], ['person_hours', '教学人时']]
        : [['school_code', '学校代码'], ['project_code', '实验编号'], ['project_name', '实验名称'], ['category_code', '实验类别'], ['type_code', '实验类型'], ['discipline_code', '实验所属学科'], ['requirement_code', '实验要求'], ['participant_type_code', '实验者类别'], ['participant_count', '实验者人数'], ['group_size', '每组人数'], ['hours', '实验学时数'], ['course_code', '课程号'], ['course_name', '课程名称'], ['planned_lab_hours', '实验总学时'], ['teacher_names', '授课教师']]
    },
    reportCards(){const labs=this.data.labs||[],projects=this.data.projects||[];return [['统计实验室',labs.length,'间','building',this.isAdmin?'所选范围，含未排课实验室':'本人在所选范围使用的实验室'],['实际排课学时',labs.reduce((n,r)=>n+Number(r.scheduled_hours||0),0),'学时','clock','根据逐周课表汇总'],['教学人时',labs.reduce((n,r)=>n+Number(r.person_hours||0),0),'人时','chart','排课学时 × 课程选课人数'],['实验项目',projects.length,'项','flask',projects.reduce((n,r)=>n+Number(r.hours||0),0)+' 个项目学时']]},
    scopeLabel() {
      const filters = this.appliedFilters || {}
      const term = this.lookups.terms.find(t => String(t.id) === String(filters.termId))
      const year = this.years.find(y => String(y.id) === String(filters.yearId))
      const lab = this.lookups.labs.find(l => String(l.id) === String(filters.labId))
      return [term ? term.name : year ? year.name : '全部学年', lab ? lab.shiyanshimingcheng : '全部实验室'].join(' · ')
    },
    years() {
      const map = new Map()
      this.lookups.terms.forEach(term => {
        if (term.academic_year_id != null)
          map.set(term.academic_year_id, {
            id: term.academic_year_id,
            name:
              term.academic_year_name ||
              (term.start_year
                ? term.start_year + '–' + (Number(term.start_year) + 1) + '学年'
                : term.name.replace(/第.*$/, ''))
          })
      })
      return Array.from(map.values())
    },
    terms() {
      return this.lookups.terms.filter(
        term => !this.filters.yearId || String(term.academic_year_id) === String(this.filters.yearId)
      )
    },
    basis() {
      return typeof this.data.basis === 'string'
        ? this.data.basis
        : this.data.basis
        ? JSON.stringify(this.data.basis)
        : '教学人时 = 实际排课学时 × 对应教学任务的选课人数。计划学时与排课学时分开统计。'
    }
  },
  watch:{'$route.query.termId'(v){this.filters.termId=v||'';this.alignYear();this.load()}},
  async mounted() {
    try {
      await this.loadLookups()
    } catch (e) {
      this.fail(e)
    }
    this.filters.termId=this.$route.query.termId||''
    this.alignYear()
    this.load()
  },
  methods: {
    alignYear() {
      if (this.filters.termId && !this.terms.some(term => String(term.id) === String(this.filters.termId)))
        this.filters.yearId = ''
    },
    changeYear() {
      if (this.filters.termId && !this.terms.some(term => String(term.id) === String(this.filters.termId))) {
        this.filters.termId = ''
        this.selectTerm('')
      } else this.load()
    },
    async load() {
      const version = ++this.loadVersion
      const filters = { ...this.filters }
      this.loading = true
      try {
        const data = await request('/reports', { params: filters })
        if (version !== this.loadVersion) return
        this.data = data
        this.appliedFilters = filters
      } catch (e) {
        if (version === this.loadVersion) this.fail(e)
      } finally {
        if (version === this.loadVersion) this.loading = false
      }
    },
    exportReport(type) {
      if (!this.appliedFilters || this.loading) return
      this.downloadFile(
        '/reports/export',
        (type === 'labs' ? '实验室教学统计' : '教学实验项目采集表') + '-' + this.scopeLabel.replace(/[\\/:*?"<>|]/g, '_') + '.xlsx',
        Object.assign({}, this.appliedFilters, { type })
      )
    }
  }
}
</script>
