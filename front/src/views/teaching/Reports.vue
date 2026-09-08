<template>
  <div class="teaching-page">
    <page-heading
      title="统计报表"
      description="按学年学期汇总实验室教学使用与实验项目，报表遵循实际排课口径。"
      eyebrow="TEACHING REPORTS"
    >
      <el-button icon="el-icon-download" :loading="downloading" @click="exportReport">导出当前报表</el-button>
    </page-heading>
    <section class="panel">
      <div class="filter-bar">
        <el-select v-model="filters.yearId" clearable placeholder="全部学年" @change="changeYear">
          <el-option v-for="year in years" :key="year.id" :label="year.name" :value="year.id" />
        </el-select>
        <el-select v-model="filters.termId" clearable placeholder="全部学期" @change="load">
          <el-option v-for="term in terms" :key="term.id" :label="term.name" :value="term.id" />
        </el-select>
        <el-button type="primary" icon="el-icon-search" :loading="loading" @click="load">查询</el-button>
      </div>
      <el-alert :title="basis" type="info" :closable="false" show-icon />
      <warnings :items="data.warnings || []" />
      <el-tabs v-model="tab">
        <el-tab-pane label="实验室使用统计" name="labs">
          <el-table v-loading="loading" :data="data.labs || []" empty-text="当前范围暂无实验室统计">
            <el-table-column prop="lab_code" label="实验室编号" min-width="145" />
            <el-table-column prop="lab_name" label="实验室名称" min-width="230" />
            <el-table-column prop="course_count" label="课程数" width="110" />
            <el-table-column prop="task_count" label="任务数" width="110" />
            <el-table-column prop="scheduled_hours" label="实际排课学时" width="150" />
            <el-table-column prop="person_hours" label="教学人时" width="150" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="实验项目清单" name="projects">
          <el-table v-loading="loading" :data="data.projects || []" empty-text="当前范围暂无实验项目">
            <el-table-column prop="term_name" label="学期" min-width="180" />
            <el-table-column prop="course_code" label="课程号" min-width="120" />
            <el-table-column prop="course_name" label="课程名称" min-width="190" />
            <el-table-column prop="lab_names" label="实验室" min-width="170" />
            <el-table-column prop="project_code" label="实验编号" min-width="160" />
            <el-table-column prop="project_name" label="实验名称" min-width="190" />
            <el-table-column prop="hours" label="项目学时" width="100" />
            <el-table-column prop="enrollment_count" label="课程选课人数" width="135" />
          </el-table>
          <p class="muted report-note">
            课程选课人数用于说明教学任务规模，不代表每一个实验项目的实际参与人数。
          </p>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import Warnings from './Warnings'
import { shared, request } from './api'
export default {
  components: { PageHeading, Warnings },
  mixins: [shared],
  data: () => ({ filters: { yearId: '', termId: '' }, tab: 'labs', data: {} }),
  computed: {
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
  async mounted() {
    try {
      await this.loadLookups()
    } catch (e) {
      this.fail(e)
    }
    this.load()
  },
  methods: {
    changeYear() {
      if (!this.terms.some(term => term.id === this.filters.termId)) this.filters.termId = ''
      this.load()
    },
    async load() {
      this.loading = true
      try {
        this.data = await request('/reports', { params: this.filters })
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    exportReport() {
      this.downloadFile(
        '/reports/export',
        (this.tab === 'labs' ? '实验室教学统计' : '实验项目清单') + '.xlsx',
        Object.assign({}, this.filters, { type: this.tab })
      )
    }
  }
}
</script>
<style scoped>
.report-note {
  margin-top: 18px;
}
</style>
