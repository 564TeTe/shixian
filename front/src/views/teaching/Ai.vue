<template><div class="teaching-page">
<page-heading title="智能查询" eyebrow="AI TEACHING ASSISTANT" description="用日常语言描述你的问题，让教学数据更容易理解。"><span class="badge" :class="state.configured?'green':'purple'">{{ state.configured?'模型已配置':'模型未配置' }}</span><button class="btn" @click="loadStatus">检查配置</button></page-heading>
<div class="ai-layout"><section class="panel ai-panel" v-loading="loading"><div class="ai-welcome"><span class="ai-emblem"><sf-icon name="spark"/></span><div class="eyebrow">EXPLORE YOUR TEACHING DATA</div><h2>关于实验教学，你想了解什么？</h2><p>从课程安排到实验人时，用一句话开始探索。</p></div>
<div class="suggestions"><button v-for="(example,i) in (state.examples.length?state.examples:['统计本学期各实验室的教学人时数','查看本学期开设的实验课程','列出本学期所有实验项目'])" :key="i" class="btn" @click="question=example"><sf-icon :name="i===0?'chart':i===1?'book':'flask'"/><span>{{ example }}</span><sf-icon name="arrow"/></button></div>
<div v-if="!state.configured" class="notice warning"><sf-icon name="info"/><span>{{ state.message || '模型尚未配置，请完成后端 AI 配置后使用。' }}</span></div>
<form class="ai-form" @submit.prevent="query"><label class="sr-only" for="ai-question">输入教学数据问题</label><textarea id="ai-question" v-model.trim="question" placeholder="例如：统计本学期各实验室的教学人时数" rows="3" maxlength="1000" required></textarea><div class="between"><span><sf-icon name="shield"/>只读查询 · 结果来自数据库</span><button class="btn primary" :disabled="!canQuery || saving || !question">{{ saving?'查询中…':'开始查询' }}<sf-icon name="arrow"/></button></div></form>
<section v-if="result" class="ai-result"><div class="between"><h2><sf-icon name="spark"/>查询结果</h2><button class="btn" @click="exportResult">导出结果</button></div><p class="muted">共 {{ resultRows.length }} 条</p><div v-if="result.truncated" class="notice warning">结果已截断，请缩小查询范围。</div><div class="table-wrap"><table><thead><tr><th v-for="c in columns" :key="c">{{ c }}</th></tr></thead><tbody><tr v-for="(row,i) in resultRows" :key="i"><td v-for="c in columns" :key="c">{{ row[c] == null ? '—' : row[c] }}</td></tr></tbody></table></div><div v-if="!resultRows.length" class="empty">当前条件下没有匹配记录</div><details><summary>查看本次查询语句</summary><pre class="query-sql">{{ result.sql }}</pre></details></section></section>
<aside><section class="panel"><span class="feature-icon small"><sf-icon name="book"/></span><h2>提问小贴士</h2><p class="muted spaced">明确时间范围、查询对象和统计指标，可以得到更清晰的结果。</p><div v-for="(tip,i) in [['选择范围','本学期 / 历史学期'],['描述对象','实验室、课程、实验项目'],['指定指标','教学人时、学时、项目清单']]" :key="i" class="prompt-tip"><span>0{{ i+1 }}</span><div><strong>{{ tip[0] }}</strong><p>{{ tip[1] }}</p></div></div></section><div class="note-box"><sf-icon name="info"/>智能查询按当前账号权限执行。</div></aside></div></div></template><script>
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
    exportResult() { const cell=v=>'"'+String(v==null?'':v).replace(/^[=+@-]/,"'"+'  methods: {').replace(/"/g,'""')+'"'; const csv='\uFEFF'+[this.columns,...this.resultRows.map(r=>this.columns.map(c=>r[c]))].map(r=>r.map(cell).join(',')).join('\r\n'); const u=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=u;a.download='智能查询结果.csv';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000) },
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
