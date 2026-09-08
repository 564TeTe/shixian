<template>
  <div class="teaching-page">
    <page-heading
      title="智能查询"
      description="用自然语言查询教学数据，结果来自允许访问的业务表。"
      eyebrow="NATURAL LANGUAGE QUERY"
    >
      <el-button icon="el-icon-refresh" :loading="loading" @click="loadStatus">检查配置</el-button>
    </page-heading>
    <section class="panel" v-loading="loading">
      <div class="panel-title">
        <h2>教学数据问答</h2>
        <el-tag :type="state.configured ? 'success' : 'warning'" size="small">
          {{ state.configured ? '模型已配置' : '模型未配置' }}
        </el-tag>
      </div>
      <el-alert
        v-if="!state.configured"
        :title="
          state.message ||
            '尚未配置 AI 模型密钥。请由系统管理员完成服务端模型配置后再使用；当前无法发起智能查询。'
        "
        type="warning"
        show-icon
        :closable="false"
      />
      <el-alert
        v-if="state.admin_only && !isAdmin"
        title="智能查询目前仅向管理员开放。教师可通过课程与课表、实验项目和统计报表查看本人教学数据。"
        type="info"
        show-icon
        :closable="false"
      />
      <p v-if="state.configured && state.model" class="muted">当前模型：{{ state.model }}</p>
      <el-form @submit.native.prevent="query">
        <el-form-item>
          <el-input
            v-model.trim="question"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="例如：本学期各实验室的排课学时是多少？"
            :disabled="!canQuery"
          />
        </el-form-item>
        <div class="action-row">
          <el-button
            type="primary"
            icon="el-icon-chat-dot-round"
            native-type="submit"
            :loading="saving"
            :disabled="!canQuery || !question"
          >
            查询教学数据
          </el-button>
          <span class="muted">只读查询 · 结果有行数限制</span>
        </div>
      </el-form>
      <div class="question-examples">
        <span class="muted">可以这样提问</span>
        <el-button
          v-for="(example, i) in state.examples || []"
          :key="i"
          size="small"
          plain
          :disabled="!canQuery"
          @click="question = example"
        >
          {{ example }}
        </el-button>
      </div>
    </section>
    <section v-if="result" class="panel">
      <div class="panel-title">
        <h2>查询结果</h2>
        <span class="muted">{{ (result.rows || []).length }} 行</span>
      </div>
      <el-alert
        v-if="result.truncated"
        title="结果已达到行数上限，仅展示部分记录。可缩小学期或课程范围后重新查询。"
        type="warning"
        show-icon
        :closable="false"
      />
      <el-table :data="resultRows" empty-text="查询完成，当前条件下无匹配记录">
        <el-table-column
          v-for="column in columns"
          :key="column"
          :prop="column"
          :label="column"
          min-width="150"
        >
          <template slot-scope="scope">{{ scope.row[column] == null ? '—' : scope.row[column] }}</template>
        </el-table-column>
      </el-table>
      <el-collapse class="sql-collapse">
        <el-collapse-item title="查看本次查询语句" name="sql">
          <pre class="query-sql">{{ result.sql }}</pre>
        </el-collapse-item>
      </el-collapse>
    </section>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import { shared, request } from './api'
export default {
  components: { PageHeading },
  mixins: [shared],
  data: () => ({ state: { configured: false, examples: [] }, question: '', result: null }),
  computed: {
    canQuery() {
      return this.isAdmin && this.state.configured === true
    },
    columns() {
      return this.result
        ? (this.result.columns || []).map(column =>
            typeof column === 'string' ? column : column.name || column.label
          )
        : []
    },
    resultRows() {
      return this.result
        ? (this.result.rows || []).map(row =>
            Array.isArray(row)
              ? this.columns.reduce((obj, col, i) => {
                  obj[col] = row[i]
                  return obj
                }, {})
              : row
          )
        : []
    }
  },
  mounted() {
    this.loadStatus()
  },
  methods: {
    async loadStatus() {
      this.loading = true
      try {
        this.state = await request('/ai/status')
      } catch (e) {
        this.fail(e)
        this.state.configured = false
      } finally {
        this.loading = false
      }
    },
    async query() {
      if (!this.canQuery || !this.question || this.saving) return
      this.saving = true
      this.result = null
      try {
        this.result = await request('/ai/query', {
          method: 'post',
          data: { question: this.question },
          timeout: 120000
        })
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>
<style scoped>
.question-examples {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  padding-top: 22px;
  margin-top: 23px;
  border-top: 1px solid #e4ebf3;
}
.question-examples .el-button {
  margin: 0;
  white-space: normal;
  line-height: 1.7;
  text-align: left;
}
.sql-collapse {
  margin-top: 24px;
}
</style>
