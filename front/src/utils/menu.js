export const navigation = [
  { path: '/index', title: '教学概览', icon: 'el-icon-data-board' },
  { path: '/teaching/tasks', title: '课程与课表', icon: 'el-icon-date' },
  { path: '/teaching/projects', title: '实验项目', icon: 'el-icon-notebook-2' },
  { path: '/teaching/terms', title: '学年学期', icon: 'el-icon-calendar' },
  { path: '/teaching/labs', title: '实验室', icon: 'el-icon-office-building' },
  { path: '/teaching/teachers', title: '教师账号', icon: 'el-icon-user', admin: true },
  { path: '/teaching/imports', title: '导入中心', icon: 'el-icon-upload', admin: true },
  { path: '/teaching/reports', title: '统计报表', icon: 'el-icon-pie-chart' },
  { path: '/teaching/ai', title: '智能查询', icon: 'el-icon-chat-dot-round', admin: true }
]
export default {
  list() {
    return [
      { roleName: '管理员', tableName: 'users' },
      { roleName: '教师', tableName: 'jiaoshi' }
    ].map(role =>
      Object.assign(role, {
        hasBackLogin: '是',
        hasBackRegister: '否',
        frontMenu: [],
        backMenu: navigation
          .filter(item => !item.admin || role.tableName === 'users')
          .map(item => ({
            menu: item.title,
            child: [{ menu: item.title, tableName: item.path.substring(1), buttons: ['查看'] }]
          }))
      })
    )
  }
}
