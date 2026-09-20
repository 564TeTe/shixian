<template>
<div class="app-shell" :class="{'menu-open':mobile}">
<aside class="sidebar"><router-link class="brand" to="/index"><span class="brand-mark"><sf-icon name="flask"/></span><span>实验教学<span class="brand-sub">TEACHING WORKSPACE</span></span></router-link>
<nav aria-label="主导航"><template v-for="r in navigation"><div v-if="r.group" :key="r.path+'group'" class="nav-group">{{ r.group }}</div><router-link :key="r.path" :to="r.path" class="nav-link" active-class="selected" exact @click.native="mobile=false"><sf-icon :name="r.icon"/><span>{{ r.title }}</span><span v-if="r.icon==='spark'" class="ai-label">AI</span></router-link></template></nav>
<div class="sidebar-footer"><div class="demo-status"><sf-icon name="shield"/>{{ isAdmin?'管理员工作空间':'教师工作空间' }}</div><p>实验教学项目管理系统</p><a href="#/login" @click.prevent="logout"><sf-icon name="logout"/>退出登录</a></div></aside>
<button class="menu-scrim" aria-label="关闭导航" @click="mobile=false"></button>
<div class="workspace"><header class="topbar"><div class="breadcrumb"><button class="btn icon-btn mobile-menu" aria-label="展开导航" @click="mobile=!mobile"><sf-icon name="menu"/></button><span>{{ isAdmin?'管理员端':'教师端' }}</span><b>/</b><strong>{{ $route.meta.title || '教学概览' }}</strong></div><div class="topbar-actions"><label class="term-control"><sf-icon name="calendar"/><select v-model="termId" aria-label="选择学期" @change="changeTerm"><option value="">全部学期</option><option v-for="t in terms" :key="t.id" :value="t.id">{{ t.name }}</option></select></label><span class="topbar-divider"></span><router-link class="role-control" to="/teaching/account"><span class="avatar tiny">{{ isAdmin?'管':username[0] }}</span><span>{{ isAdmin?'管理员端':username }}</span></router-link></div></header>
<main id="main" tabindex="-1"><router-view :key="$route.path" /></main><footer class="page-footer"><span>实验教学项目管理系统</span><span>{{ isAdmin?'管理员视角':'教师视角' }} · {{ username }}</span></footer></div></div>
</template>
<script>
import {request,errorMessage,shared} from './teaching/api'
export default {
mixins:[shared],
data:()=>({mobile:false,terms:[],termId:''}),
computed:{ isAdmin(){return this.$storage.get('sessionTable')==='users'}, username(){return this.$storage.get('adminName')||'用户'}, navigation(){return [
['/index','教学概览','grid','工作空间'],['/teaching/tasks','课程与课表','calendar','教学管理'],['/teaching/projects','实验项目','flask',''],['/teaching/terms','学年学期','layers',''],['/teaching/labs','实验室','building','基础资料'],['/teaching/teachers','教师账号','users','',true],['/teaching/imports','导入中心','upload','',true],['/teaching/reports','统计报表','chart','数据洞察',true],['/teaching/ai','智能查询','spark','智能助手',true],['/teaching/account','账号与安全','shield','个人设置']
].filter(r=>!r[4]||this.isAdmin).map(r=>({path:r[0],title:r[1],icon:r[2],group:r[3]}))}},
watch:{'$route.query.termId'(v){this.termId=v || ''}},
async mounted(){try{this.terms=await request('/terms');this.termId=this.$route.query.termId || ''; }catch(e){errorMessage(e)}},
methods:{changeTerm(){this.selectTerm(this.termId)},async logout(){try{await this.$http.post('/'+this.$storage.get('sessionTable')+'/logout')}catch(e){errorMessage(e)}finally{this.$storage.clear();this.$router.replace('/login')}}}
}
</script>
