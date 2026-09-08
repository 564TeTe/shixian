<template>
  <div class="teaching-page" v-loading="loading">
    <page-heading
      title="教学概览"
      :description="
        isAdmin
          ? '掌握教学任务与实验资源的整体情况，让安排有据可依。'
          : '查看本人参与的教学任务，维护当前学期的实验项目。'
      "
      eyebrow="OVERVIEW / 教学工作台"
    >
      <el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新数据</el-button>
    </page-heading>
    <div class="overview-banner">
      <div>
        <span class="eyebrow">CURRENT SEMESTER</span>
        <h2>{{ data.currentTerm ? data.currentTerm.name : '当前学期待生成' }}</h2>
        <p>
          {{
            data.currentTerm
              ? '当前任务可维护，历史学期项目保留归档。'
              : '请联系管理员在学年学期页面生成当前学期。'
          }}
        </p>
      </div>
      <i class="el-icon-date" />
    </div>
    <div class="stat-grid">
      <div v-for="card in cards" :key="card.key" class="stat-card">
        <i :class="card.icon" />
        <div class="label">{{ card.label }}</div>
        <div class="value">
          {{ data.counts && data.counts[card.key] != null ? data.counts[card.key] : '—' }}
        </div>
        <div class="foot">{{ isAdmin ? '全部学期 · 实际业务数据' : '本人参与的教学范围' }}</div>
      </div>
    </div>
    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-title">
          <h2>教学工作入口</h2>
          <span class="muted">从任务开始，完成项目维护</span>
        </div>
        <button class="quick-link" @click="$router.push('/teaching/tasks')">
          <i class="el-icon-date" />
          <span>
            <strong>课程与课表</strong>
            <span class="muted">按学期查看课程、任课教师与实际排课</span>
          </span>
        </button>
        <button class="quick-link" @click="$router.push('/teaching/projects')">
          <i class="el-icon-notebook-2" />
          <span>
            <strong>实验项目管理</strong>
            <span class="muted">维护项目，导入 Excel，复用历史教学内容</span>
          </span>
        </button>
        <button class="quick-link" @click="$router.push('/teaching/reports')">
          <i class="el-icon-pie-chart" />
          <span>
            <strong>实验教学统计</strong>
            <span class="muted">按学年学期统计实验室学时与教学项目</span>
          </span>
        </button>
      </section>
      <section class="panel">
        <div class="panel-title">
          <h2>数据提醒</h2>
          <el-tag size="mini" type="info">真实数据口径</el-tag>
        </div>
        <warnings :items="data.warnings || []" />
        <div v-if="!(data.warnings || []).length" class="empty-state">
          <i class="el-icon-circle-check" />
          暂无需要处理的数据提醒
        </div>
        <p class="muted">报表中的排课学时以课表实际安排计算；选做实验项目的实际参与人数需另行确认。</p>
        <el-button v-if="isAdmin" type="text" @click="$router.push('/teaching/imports')">
          查看导入批次
          <i class="el-icon-right" />
        </el-button>
      </section>
    </div>
  </div>
</template>
<script>
import PageHeading from './teaching/PageHeading'
import Warnings from './teaching/Warnings'
import { shared, request } from './teaching/api'
export default {
  components: { PageHeading, Warnings },
  mixins: [shared],
  data: () => ({
    data: {},
    cards: [
      { key: 'tasks', label: '教学任务', icon: 'el-icon-collection' },
      { key: 'courses', label: '课程数量', icon: 'el-icon-reading' },
      { key: 'projects', label: '实验项目', icon: 'el-icon-notebook-2' },
      { key: 'labs', label: '实验室', icon: 'el-icon-office-building' }
    ]
  }),
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.data = await request('/dashboard')
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>
<style scoped>
.overview-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 25px 30px;
  background: #e9f2fc;
  border: 1px solid #d6e5f5;
  border-left: 4px solid #649fdd;
  border-radius: 8px;
  margin-bottom: 24px;
}
.overview-banner .eyebrow {
  color: #7195bc;
}
.overview-banner h2 {
  color: #244a73;
  font-size: 22px;
  font-weight: 500;
  margin: 8px 0;
}
.overview-banner p {
  color: #7590ad;
  font-size: 12px;
}
.overview-banner > i {
  color: #95b9de;
  font-size: 58px;
  margin-right: 15px;
}
</style>
