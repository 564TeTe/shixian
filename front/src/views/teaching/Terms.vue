<template>
  <div class="teaching-page">
    <page-heading
      title="学年学期"
      description="当前学期允许维护实验项目；历史归档保留完整教学记录。"
      eyebrow="ACADEMIC CALENDAR"
    >
      <el-button v-if="isAdmin" type="primary" :loading="saving" icon="el-icon-plus" @click="generate">
        补齐当前学期
      </el-button>
    </page-heading>
    <section class="panel">
      <el-alert
        title="学期状态由教学日历自动确定；历史项目不可修改，可复制到当前教学任务后继续维护。"
        type="info"
        show-icon
        :closable="false"
      />
      <el-table v-loading="loading" :data="rows" empty-text="尚无学期，请由管理员补齐当前学期">
        <el-table-column prop="academic_year_name" label="学年" min-width="180" />
        <el-table-column prop="name" label="学期" min-width="220" />
        <el-table-column prop="starts_on" label="开始日期" min-width="140" />
        <el-table-column prop="ends_on" label="结束日期" min-width="140" />
        <el-table-column label="状态" width="130">
          <template slot-scope="scope">
            <el-tag :type="['CURRENT', 'OPEN'].includes(scope.row.status) ? 'success' : 'info'" size="small">
              {{ status(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import { request, shared } from './api'
export default {
  components: { PageHeading },
  mixins: [shared],
  data: () => ({ rows: [] }),
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.rows = await request('/terms')
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    async generate() {
      this.saving = true
      try {
        await request('/terms/generate', { method: 'post' })
        this.$message.success('当前学期已补齐')
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
