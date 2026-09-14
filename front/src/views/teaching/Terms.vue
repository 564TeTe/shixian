<template><div class="teaching-page" v-loading="loading">
<page-heading title="学年学期" eyebrow="ACADEMIC CALENDAR" description="教学日历与学期归档，让课程内容有迹可循。"><span class="badge gray">教学日历</span><button v-if="isAdmin" class="btn" :disabled="saving" @click="generate">补齐当前学期</button></page-heading>
<div class="two-columns"><section class="panel calendar-feature"><span class="feature-icon"><sf-icon name="calendar"/></span><div class="eyebrow">CURRENT SEMESTER</div><h2>{{ activeTerm ? activeTerm.academic_year_name : '当前学期待生成' }}</h2><p>{{ activeTerm ? activeTerm.name : '请由管理员补齐当前学期' }}</p><span class="badge green">当前学期</span><hr/><p>{{ activeTerm ? activeTerm.starts_on+' — '+activeTerm.ends_on : '暂无日期' }}</p><div class="progress"><i :style="{width: termProgress+'%'}"></i></div><div class="between muted"><span>按学期起止日期计算</span><span>{{ termProgress }}%</span></div></section>
<section class="panel"><div class="panel-title"><h2>学期生成规则</h2></div><div class="timeline"><div><strong>每年 8 月 31 日</strong><p>生成新学年及第一学期，开始新的教学周期。</p></div><div><strong>次年 2 月 1 日</strong><p>生成当前学年的第二学期。</p></div><div><strong>历史学期归档</strong><p>实验项目只读保留，可复制到同课程的当前任务。</p></div></div><div class="notice"><sf-icon name="info"/><span>学期状态由服务端教学日历确定。</span></div></section></div>
<section class="panel"><div class="panel-title"><h2>学期记录</h2></div><div class="table-wrap" v-if="rows.length"><table><thead><tr><th>学年</th><th>学期</th><th>开始日期</th><th>结束日期</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="term in rows" :key="term.id"><td>{{ term.academic_year_name }}</td><td>{{ term.name }}</td><td>{{ term.starts_on }}</td><td>{{ term.ends_on }}</td><td><span class="badge" :class="['CURRENT','OPEN'].includes(term.status)?'green':'gray'">{{ status(term.status) }}</span></td><td><button class="btn text" @click="$router.push({path:'/teaching/tasks',query:{termId:term.id}})">查看课程</button></td></tr></tbody></table></div><div v-else class="empty"><strong>尚无学期记录</strong></div></section></div></template><script>
import PageHeading from './PageHeading'
import { request, shared } from './api'
export default {
  components: { PageHeading },
  mixins: [shared],
  data: () => ({ rows: [] }),
  computed: {
    activeTerm(){return this.rows.find(t=>['CURRENT','OPEN'].includes(t.status))},
    termProgress(){if(!this.activeTerm)return 0;return Math.round(Math.min(100,Math.max(0,(Date.now()-new Date(this.activeTerm.starts_on))/(new Date(this.activeTerm.ends_on)-new Date(this.activeTerm.starts_on))*100)))}
  },
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
