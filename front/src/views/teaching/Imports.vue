<template>
  <div class="teaching-page">
    <page-heading
      title="导入中心"
      description="上传真实 Excel 数据，保留原始行与核对记录。疑似重复的教学任务不会覆盖已有数据。"
      eyebrow="DATA IMPORT CENTER"
    >
      <el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新批次</el-button>
    </page-heading>
    <section class="panel">
      <div class="panel-title">
        <h2>导入教学资料</h2>
        <span class="muted">支持 .xlsx · 文件小于 10 MB</span>
      </div>
      <el-radio-group v-model="kind" :disabled="saving" @change="resetUpload">
        <el-radio-button label="timetable">课程课表</el-radio-button>
        <el-radio-button label="teachers">教师账号</el-radio-button>
        <el-radio-button label="labs">实验室资料</el-radio-button>
      </el-radio-group>
      <div class="file-pick">
        <strong>{{ kinds[kind] }} Excel</strong>
        <p class="muted">{{ descriptions[kind] }}</p>
        <input
          :key="fileKey"
          type="file"
          accept=".xlsx"
          :disabled="saving"
          aria-label="选择导入 Excel 文件"
          @change="file = $event.target.files[0]"
        />
      </div>
      <div class="action-row">
        <el-button
          type="primary"
          icon="el-icon-upload2"
          :disabled="!file"
          :loading="saving"
          @click="importFile"
        >
          上传并导入
        </el-button>
        <el-button
          icon="el-icon-download"
          :loading="downloading"
          @click="downloadFile('/templates/' + kind, kinds[kind] + '导入模板.xlsx')"
        >
          下载模板
        </el-button>
        <span v-if="file" class="muted">已选择：{{ file.name }}</span>
      </div>
      <div v-if="result" class="import-result">
        <el-alert
          :title="resultTitle"
          :type="Number(result.errors) > 0 || result.review_count > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
        />
        <warnings :items="resultIssues" />
        <el-button v-if="result.batchId" type="text" @click="detail(result.batchId)">
          查看本次导入的逐行记录
          <i class="el-icon-right" />
        </el-button>
      </div>
    </section>
    <section class="panel">
      <div class="panel-title">
        <h2>课表导入批次记录</h2>
        <span class="muted">共 {{ total }} 批</span>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无导入记录，请上传实际课表开始">
        <el-table-column prop="id" label="批次" width="80" />
        <el-table-column label="文件名称" min-width="260">
          <template slot-scope="scope">{{ scope.row.file_name || scope.row.filename || '—' }}</template>
        </el-table-column>
        <el-table-column prop="row_count" label="来源行数" width="100" />
        <el-table-column prop="promoted" label="已入库" width="90" />
        <el-table-column prop="created_at" label="导入时间" min-width="175" />
        <el-table-column label="状态" min-width="130">
          <template slot-scope="scope">
            <el-tag
              size="small"
              :type="Number(scope.row.errors) ? 'danger' : Number(scope.row.warnings) ? 'warning' : 'success'"
            >
              {{
                Number(scope.row.errors)
                  ? scope.row.errors + ' 行错误'
                  : Number(scope.row.warnings)
                  ? scope.row.warnings + ' 行待核对'
                  : '处理完成'
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template slot-scope="scope">
            <el-button type="text" @click="detail(scope.row.id)">查看批次</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog title="导入批次与逐行核对" :visible.sync="detailVisible" width="1100px">
      <div v-loading="detailLoading">
        <template v-if="batch">
          <dl class="detail-grid">
            <div>
              <dt>批次编号</dt>
              <dd>{{ batch.id }}</dd>
            </div>
            <div>
              <dt>来源文件</dt>
              <dd>{{ batch.file_name || batch.filename }}</dd>
            </div>
            <div>
              <dt>导入时间</dt>
              <dd>{{ text(batch.created_at) }}</dd>
            </div>
          </dl>
          <el-alert
            title="待核对行保留原始内容。确认是另一独立开课后可入库；操作会新增任务，不会覆盖已有课程任务。错误行需要修正 Excel 后重新导入。"
            type="info"
            show-icon
            :closable="false"
          />
          <div v-if="summaryText" class="muted summary-line">{{ summaryText }}</div>
          <el-table :data="detailRows" max-height="470" empty-text="本批次没有逐行记录">
            <el-table-column prop="sheet_name" label="工作表" width="100" />
            <el-table-column prop="source_row" label="原始行号" width="95" />
            <el-table-column label="状态" width="115">
              <template slot-scope="scope">
                <el-tag
                  size="mini"
                  :type="
                    scope.row.status === 'ERROR'
                      ? 'danger'
                      : scope.row.status === 'PROMOTED'
                      ? 'success'
                      : 'warning'
                  "
                >
                  {{ status(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数据疑点" min-width="330">
              <template slot-scope="scope">
                <div v-for="(issue, i) in issues(scope.row.issues)" :key="i">{{ issueText(issue) }}</div>
                <span v-if="!issues(scope.row.issues).length" class="muted">无数据疑点</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template slot-scope="scope">
                <el-button
                  v-if="scope.row.status === 'REVIEW'"
                  type="text"
                  :disabled="saving"
                  @click="confirmRow(scope.row)"
                >
                  确认独立开课
                </el-button>
                <span v-else class="muted">
                  {{ scope.row.status === 'PROMOTED' ? '已生成任务' : '需修正来源数据' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column type="expand">
              <template slot-scope="scope">
                <pre class="query-sql">{{ pretty(scope.row.raw_data || scope.row) }}</pre>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <span slot="footer"><el-button @click="detailVisible = false">关闭</el-button></span>
    </el-dialog>
  </div>
</template>
<script>
import PageHeading from './PageHeading'
import Warnings from './Warnings'
import { request, upload, shared } from './api'
export default {
  components: { PageHeading, Warnings },
  mixins: [shared],
  data: () => ({
    rows: [],
    total: 0,
    kind: 'timetable',
    kinds: { timetable: '课程课表', teachers: '教师账号', labs: '实验室资料' },
    descriptions: {
      timetable: '支持原始课表或标准模板。同文件重复上传不会重复生成任务；跨文件的疑似重复内容会保留待核对。',
      teachers:
        '请填写真实工号与姓名。未知工号使用 TMP 前缀唯一临时账号。导入后请到教师账号页面重置密码，并将一次性显示的密码交给教师本人。',
      labs: '按实验室编号维护资料，负责人、设备数量未知时保留空值。'
    },
    file: null,
    fileKey: 0,
    result: null,
    detailVisible: false,
    detailLoading: false,
    batch: null,
    detailRows: [],
    summary: null
  }),
  computed: {
    resultTitle() {
      if (!this.result) return ''
      return this.kind === 'timetable'
        ? '批次 ' +
            this.result.batchId +
            '：读取 ' +
            this.result.rows +
            ' 行，入库 ' +
            this.result.promoted +
            ' 行，待核对 ' +
            (this.result.review_count || 0) +
            ' 行，错误 ' +
            (this.result.errors || 0) +
            ' 行'
        : '已导入 ' + this.result.imported + ' 条'
    },
    resultIssues() {
      return this.result
        ? [
            ...(Array.isArray(this.result.errors) ? this.result.errors : []),
            ...this.issues(this.result.warnings)
          ]
        : []
    },
    summaryText() {
      if (!this.summary) return ''
      if (typeof this.summary === 'string') return this.summary
      return Object.entries(this.summary)
        .map(
          ([key, value]) =>
            (({
              total: '总行数',
              rows: '总行数',
              promoted: '已入库',
              review: '待核对',
              review_count: '待核对',
              batchId: '批次',
              errors: '错误',
              error: '错误',
              warnings: '提醒'
            }[key] || key) +
            '：' +
            (typeof value === 'object' ? JSON.stringify(value) : value))
        )
        .join(' · ')
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const data = await request('/imports')
        this.rows = data.list
        this.total = data.total
      } catch (e) {
        this.fail(e)
      } finally {
        this.loading = false
      }
    },
    resetUpload() {
      this.file = null
      this.fileKey++
      this.result = null
    },
    async importFile() {
      if (!this.file) return
      this.saving = true
      this.result = null
      try {
        this.result = await upload('/imports/' + this.kind, this.file)
        this.file = null
        this.fileKey++
        this.$message.success('文件处理完成，请查看导入结果')
        await this.load()
      } catch (e) {
        this.fail(e)
      } finally {
        this.saving = false
      }
    },
    async detail(id) {
      this.detailVisible = true
      this.detailLoading = true
      this.batch = null
      this.detailRows = []
      try {
        const data = await request('/imports/' + id)
        this.batch = data.batch
        this.detailRows = data.rows
        this.summary = data.summary
      } catch (e) {
        this.fail(e)
      } finally {
        this.detailLoading = false
      }
    },
    issues(value) {
      if (!value) return []
      if (Array.isArray(value)) return value
      if (typeof value === 'string') {
        try {
          const parsed = JSON.parse(value)
          return Array.isArray(parsed) ? parsed : [parsed]
        } catch (e) {
          return [value]
        }
      }
      return [value]
    },
    issueText(value) {
      return typeof value === 'string' ? value : value.message || value.msg || JSON.stringify(value)
    },
    pretty(value) {
      try {
        return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2)
      } catch (e) {
        return value
      }
    },
    async confirmRow(row) {
      try {
        await this.$confirm(
          '请核实此行确为独立开课。确认后会新增教学任务，不覆盖已有任务；疑似重复的同一开课请勿确认。',
          '确认第 ' + row.source_row + ' 行为独立开课',
          { type: 'warning', confirmButtonText: '已核实，确认独立开课', cancelButtonText: '保留待核对' }
        )
      } catch (e) {
        return
      }
      this.saving = true
      const id = this.batch.id
      try {
        await request('/imports/' + id + '/rows/' + row.source_row + '/confirm', {
          method: 'post',
          params: { sheet: row.sheet_name },
          data: { confirmDistinctTask: true }
        })
        this.$message.success('已确认并生成独立教学任务')
        await this.detail(id)
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
<style scoped>
.import-result {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid #e4ebf3;
}
.summary-line {
  margin: 15px 0;
}
</style>
