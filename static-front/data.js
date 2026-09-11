/* 全部为用于展示功能的虚构数据，不读取项目数据库。 */
'use strict';
window.PrototypeData = {
  terms: [
    { id: 'current', name: '2026–2027 第一学期', year: '2026–2027', start: '2026-08-31', end: '2027-01-31', current: true },
    { id: 'history', name: '2025–2026 第二学期', year: '2025–2026', start: '2026-02-01', end: '2026-08-30', current: false }
  ],
  teachers: [
    { id: 1, code: 'T2026001', name: '陈老师', college: '计算机与信息工程学院', temporary: false },
    { id: 2, code: 'T2026002', name: '李老师', college: '计算机与信息工程学院', temporary: false },
    { id: 3, code: 'T2026003', name: '王老师', college: '电子与电气工程学院', temporary: false },
    { id: 4, code: 'TMP004', name: '周老师', college: '计算机与信息工程学院', temporary: true }
  ],
  labs: [
    { id: 1, code: '36-606', name: '软件工程实验室', location: '36 栋 · 606', manager: '陈老师', equipment: 60 },
    { id: 2, code: '36-402', name: '网络技术实验室', location: '36 栋 · 402', manager: '李老师', equipment: 48 },
    { id: 3, code: '36-405', name: '人工智能实验室', location: '36 栋 · 405', manager: '周老师', equipment: 40 },
    { id: 4, code: '35-302', name: '电子技术实验室', location: '35 栋 · 302', manager: '王老师', equipment: 56 },
    { id: 5, code: '35-305', name: '嵌入式系统实验室', location: '35 栋 · 305', manager: '待补充', equipment: '' },
    { id: 6, code: '36-501', name: '计算机基础实验室', location: '36 栋 · 501', manager: '陈老师', equipment: 64 }
  ],
  tasks: [
    { id: 1, code: 'CS26001', name: '数据库原理与应用', teacher: '陈老师', className: '软件工程 2401、2402', major: '软件工程', people: 56, hours: 32, lab: 1, day: 1, period: 1, term: 'current', weeks: 16 },
    { id: 2, code: 'CS26002', name: '计算机网络', teacher: '李老师', className: '计算机科学 2401', major: '计算机科学与技术', people: 42, hours: 32, lab: 2, day: 2, period: 2, term: 'current', weeks: 16 },
    { id: 3, code: 'CS26003', name: 'Python 程序设计', teacher: '陈老师', className: '软件工程 2501', major: '软件工程', people: 48, hours: 32, lab: 1, day: 3, period: 1, term: 'current', weeks: 16 },
    { id: 4, code: 'AI26001', name: '人工智能导论', teacher: '周老师', className: '人工智能 2401', major: '人工智能', people: 36, hours: 24, lab: 3, day: 4, period: 3, term: 'current', weeks: 12 },
    { id: 5, code: 'EE26001', name: '数字电子技术', teacher: '王老师', className: '电子信息 2402', major: '电子信息工程', people: 50, hours: 32, lab: 4, day: 5, period: 2, term: 'current', weeks: 16 },
    { id: 6, code: 'CS26004', name: '数据结构', teacher: '李老师', className: '计算机科学 2501', major: '计算机科学与技术', people: 44, hours: 32, lab: 6, day: 3, period: 3, term: 'current', weeks: 16 },
    { id: 7, code: 'CS26005', name: 'Web 应用开发', teacher: '陈老师', className: '软件工程 2301', major: '软件工程', people: 52, hours: 32, lab: 1, day: 5, period: 1, term: 'current', weeks: 16 },
    { id: 8, code: 'CS26001', name: '数据库原理与应用', teacher: '陈老师', className: '软件工程 2301', major: '软件工程', people: 50, hours: 32, lab: 1, day: 2, period: 1, term: 'history', weeks: 16 },
    { id: 9, code: 'CS26002', name: '计算机网络', teacher: '李老师', className: '计算机科学 2301', major: '计算机科学与技术', people: 46, hours: 32, lab: 2, day: 4, period: 2, term: 'history', weeks: 16 }
  ],
  projects: [
    { id: 1, task: 1, code: '36-606-001', name: '数据库环境搭建与 SQL 基础', type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 1, hours: 4 },
    { id: 2, task: 1, code: '36-606-002', name: '关系数据库设计与规范化', type: '设计研究', category: '专业', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 6 },
    { id: 3, task: 1, code: '36-606-003', name: '多表查询与视图应用', type: '综合性', category: '专业', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 1, hours: 4 },
    { id: 4, task: 2, code: '36-402-001', name: '交换机配置与 VLAN 划分', type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 4 },
    { id: 5, task: 3, code: '36-606-004', name: '数据分析与可视化', type: '综合性', category: '专业', requirement: '选做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 6 },
    { id: 6, task: 4, code: '36-405-001', name: '图像分类模型训练', type: '设计研究', category: '专业', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 3, hours: 8 },
    { id: 7, task: 5, code: '35-302-001', name: '组合逻辑电路设计', type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: '0807', school: 'DEMO', group: 2, hours: 4 },
    { id: 8, task: 8, code: '36-606-005', name: '数据库完整性与事务控制', type: '综合性', category: '专业', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 6 },
    { id: 9, task: 8, code: '36-606-006', name: '学生成绩管理数据库设计', type: '设计研究', category: '专业', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 3, hours: 8 }
  ],
  batches: [
    { id: 1, name: '2026–2027 第一学期实验课表.xlsx', kind: '课程课表', date: '2026-09-10 14:30', rows: 9, done: 7, review: 2 }
  ]
};
