<template>
  <div class="app-shell">
    <index-aside />
    <div class="workspace">
      <index-header />
      <index-main />
      <footer class="page-footer">
        <span>实验教学项目管理系统</span>
        <span>后端数据 <i>·</i> {{ isAdmin ? '管理员视角' : '教师视角' }}</span>
      </footer>
    </div>
  </div>
</template>
<script>
	import IndexHeader from '@/components/index/IndexHeader'
	import IndexAside from '@/components/index/IndexAsideStatic'
	import IndexMain from '@/components/index/IndexMain'
	export default {
		components: {
			IndexHeader,
			IndexAside,
			IndexMain
		},
    computed: {
      isAdmin() {
        return this.$storage.get('sessionTable') === 'users'
      }
    }
	}
</script>
