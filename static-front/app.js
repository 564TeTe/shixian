/* 静态交互原型：所有操作仅作用于本页内存，刷新恢复演示数据。 */
'use strict';
(() => {
  const db = window.PrototypeData;
  const app = document.getElementById('app');
  const modal = document.getElementById('modal');
  const state = { role: 'admin', term: 'current', page: 'overview', query: '', task: '', type: '', tab: 'list', week: 2, report: 'labs', importKind: '课程课表', ai: '', aiKind: '', mobile: false };
  const routes = [
    ['overview', '教学概览', 'grid', '工作空间'],
    ['tasks', '课程与课表', 'calendar', '教学管理'],
    ['projects', '实验项目', 'flask', ''],
    ['terms', '学年学期', 'layers', ''],
    ['labs', '实验室', 'building', '基础资料'],
    ['teachers', '教师账号', 'users', '', true],
    ['imports', '导入中心', 'upload', '', true],
    ['reports', '统计报表', 'chart', '数据洞察', true],
    ['ai', '智能查询', 'spark', '智能助手'],
    ['account', '账号与安全', 'shield', '个人设置']
  ];
  const paths = {
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2m-8 3h2"/>',
    flask: '<path d="M9 3h6m-5 0v7L4 19a1.3 1.3 0 0 0 1 2h14a1.3 1.3 0 0 0 1-2l-6-9V3M7 15h10"/>',
    layers: '<path d="m12 3 10 5-10 5L2 8l10-5Zm-10 9 10 5 10-5M2 16l10 5 10-5"/>',
    building: '<path d="M4 21V5h12v16M16 11h4v10M2 21h20M8 9h4m-4 4h4m-4 4h4M8 5V2h4v3"/>',
    users: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 5v2"/>',
    upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5"/>',
    download: '<path d="M12 3v13m-5-5 5 5 5-5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/>',
    chart: '<path d="M4 3v18h17M9 16v-4m5 4V7m5 9v-6"/>',
    spark: '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3ZM20 2v4m-2-2h4"/>',
    shield: '<path d="M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6l-9-4Z"/><path d="m8 12 3 3 5-6"/>',
    search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
    arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
    book: '<path d="M12 5v16M3 3c4 0 6 0 9 2 3-2 5-2 9-2v16c-4 0-6 0-9 2-3-2-5-2-9-2V3Z"/>',
    copy: '<rect x="8" y="8" width="12" height="13" rx="2"/><path d="M15 8V3H3v12h5"/>',
    logout: '<path d="M9 3H4v18h5m5-14 5 5-5 5m-5-5h12"/>',
    close: '<path d="m6 6 12 12M6 18 18 6"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    bell: '<path d="M6 9a6 6 0 0 1 12 0c0 7 3 7 3 9H3c0-2 3-2 3-9Zm4 12h4"/>'
  };
  const icon = (name, cls = '') => `<svg class="icon ${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.book}</svg>`;
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const admin = () => state.role === 'admin';
  const teacherName = () => db.teachers.find(t => t.id === 1).name;
  const current = () => state.term === 'current';
  const termName = () => db.terms.find(t => t.id === state.term).name;
  const lab = id => db.labs.find(l => l.id === Number(id)) || { name: '未指定', location: '待安排' };
  const taskById = id => db.tasks.find(t => t.id === Number(id));
  const tasks = () => db.tasks.filter(t => t.term === state.term && (admin() || t.teacher === teacherName()));
  const projects = () => db.projects.filter(p => tasks().some(t => t.id === p.task));
  const canManageProjects = () => state.role === 'teacher' && current();
  const canEditTask = id => canManageProjects() && tasks().some(t => t.id === Number(id));
  const canEditProject = id => {
    const project = db.projects.find(p => p.id === Number(id));
    return !!project && canEditTask(project.task);
  };
  function projectCodes(taskId, count = 1) {
    const task = taskById(taskId);
    if (!task) return [];
    const prefix = `${lab(task.lab).code}-`;
    const suffixes = db.projects.filter(p => p.code.startsWith(prefix)).map(p => Number(p.code.slice(prefix.length)));
    const next = Math.max(0, ...suffixes) + 1;
    if (next + count - 1 > 999) return [];
    return Array.from({ length: count }, (_, i) => `${prefix}${String(next + i).padStart(3, '0')}`);
  }
  const sum = (rows, key) => rows.reduce((n, r) => n + Number(r[key] || 0), 0);
  const number = n => Number(n).toLocaleString('zh-CN');
  const badge = (text, tone = 'blue') => `<span class="badge ${tone}">${esc(text)}</span>`;
  const button = (text, action, ico = '', kind = '', extra = '') => `<button type="button" class="btn ${kind}" data-action="${action}" ${extra}>${ico ? icon(ico) : ''}${text}</button>`;
  const link = (text, page, ico = '') => `<a class="btn" href="#${page}">${ico ? icon(ico) : ''}${text}</a>`;
  const empty = (text = '没有找到符合条件的记录') => `<div class="empty">${icon('search')}<strong>${text}</strong><span>试试调整筛选条件，或添加一条演示数据。</span></div>`;
  const table = (headers, rows) => rows.length ? `<div class="table-wrap"><table><thead><tr>${headers.map(h => `<th scope="col">${h}</th>`).join('')}</tr></thead><tbody>${rows.map(row => `<tr>${row.map(cell => `<td>${cell}</td>`).join('')}</tr>`).join('')}</tbody></table></div>` : empty();
  const panelTitle = (title, right = '', sub = '') => `<div class="panel-title"><div><h2>${title}</h2>${sub ? `<p>${sub}</p>` : ''}</div>${right}</div>`;
  const notice = (text, tone = '') => `<div class="notice ${tone}">${icon('info')}<span>${text}</span></div>`;
  const heading = (en, title, description, actions = '') => `<div class="page-heading"><div><div class="eyebrow">${en}</div><h1>${title}</h1><p>${description}</p></div><div class="actions">${actions}</div></div>`;
  const options = (rows, value) => rows.map(r => `<option value="${esc(r.id)}" ${String(r.id) === String(value) ? 'selected' : ''}>${esc(r.name)}</option>`).join('');
  const searchbar = (placeholder, extra = '') => `<form class="filter-bar" data-form="search"><label class="search-field">${icon('search')}<input name="query" aria-label="${placeholder}" value="${esc(state.query)}" placeholder="${placeholder}"></label>${extra}<button class="btn primary" type="submit">查询</button>${state.query || state.type ? button('重置', 'reset-filter') : ''}<span class="filter-hint">${badge(current() ? '当前学期' : '历史只读', current() ? 'green' : 'gray')}</span></form>`;
  const matches = (...values) => values.join(' ').toLowerCase().includes(state.query.toLowerCase());
  function stats(items) {
    return `<div class="stat-grid">${items.map(([label, value, unit, ico, foot], i) => `<div class="stat-card"><div class="stat-top"><span>${label}</span><span class="stat-icon tone-${i}">${icon(ico)}</span></div><div class="stat-value">${number(value)}<small>${unit}</small></div><div class="stat-foot">${foot}</div></div>`).join('')}</div>`;
  }
  function labReports() {
    return db.labs.map(l => {
      const rows = tasks().filter(t => t.lab === l.id);
      return { ...l, courses: new Set(rows.map(t => t.code)).size, count: rows.length, hours: rows.reduce((n, t) => n + t.weeks * 2, 0), person: rows.reduce((n, t) => n + t.weeks * 2 * t.people, 0) };
    }).filter(l => l.count);
  }
  function chart() {
    const rows = labReports().sort((a, b) => b.person - a.person);
    const max = Math.max(...rows.map(l => l.person), 1);
    return `<div class="bar-chart">${rows.map((l, i) => `<div class="bar-row"><span title="${esc(l.name)}">${esc(l.name)}</span><div class="bar-track"><div class="bar-fill color-${i % 3}" style="width:${Math.round(l.person / max * 100)}%"></div></div><strong>${number(l.person)}</strong></div>`).join('') || empty('当前学期暂无排课')}<div class="chart-axis"><span>按实际排课统计</span><span>单位：人时</span></div></div>`;
  }
  function overview() {
    const rows = tasks();
    const missing = rows.filter(t => !projects().some(p => p.task === t.id)).length;
    return heading('TEACHING WORKSPACE', `${admin() ? '管理员' : esc(teacherName())}，欢迎回来`, '把教学安排得井井有条，让每一次实验都有所收获。', `${button('查看本周课表', 'open-week', 'calendar')}${canManageProjects() ? button('新增实验项目', 'new-project', 'plus', 'primary') : ''}`) +
      `<section class="semester-banner"><div><span class="banner-label"><span class="status-dot"></span>${current() ? '新学期 · 教学进行中' : '历史学期 · 已归档'}</span><h2>${termName()}</h2><p>从课程计划到实验项目，在这里连接教学的每一步。</p><a href="#projects">进入实验项目 ${icon('arrow')}</a></div><div class="banner-art" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="art-card art-back">${icon('book')}<i></i><i></i></div><div class="art-card art-front">${icon('flask')}<span>LAB</span><div class="art-lines"><i></i><i></i></div></div><span class="art-spark">✦</span><span class="art-dot"></span></div><div class="banner-week"><strong>${current() ? '02' : '16'}</strong><span>教学周 / 16 周</span><div class="mini-progress"><i style="width:${current() ? 12.5 : 100}%"></i></div></div></section>` +
      stats([
        ['教学任务', rows.length, '个', 'book', `${new Set(rows.map(t => t.code)).size} 门课程 · ${current() ? '本学期' : '历史学期'}`],
        ['实验项目', projects().length, '项', 'flask', `${sum(projects(), 'hours')} 项目学时 · 持续完善教学内容`],
        ['使用实验室', new Set(rows.map(t => t.lab)).size, '间', 'building', '覆盖当前教学任务的实验场地'],
        admin() ? ['教学人时', sum(labReports(), 'person'), '人时', 'chart', '实际排课学时 × 课程选课人数'] : ['待完善课程', missing, '门', 'book', '为本人课程补充实验内容']
      ]) +
      `<div class="dashboard-grid"><section class="panel">${admin() ? `${panelTitle('实验室教学分布', '<a class="text-link" href="#reports">查看报表 →</a>', '本学期各实验室教学人时')}${chart()}` : `${panelTitle('我的教学安排', '<a class="text-link" href="#tasks">查看课表 →</a>', '仅展示本人当前所选学期课程')}${rows.map(t => `<a class="todo" href="#tasks"><span class="todo-icon blue">${icon('book')}</span><div><strong>${esc(t.name)}</strong><p>${esc(t.className)} · ${esc(lab(t.lab).location)}</p></div>${icon('arrow')}</a>`).join('')}<a class="text-link" href="#ai">使用智能查询探索本人教学数据 →</a>`}</section><section class="panel">${panelTitle('待办与提醒', '<span class="small-dot"></span>')}<a class="todo" href="#projects"><span class="todo-icon amber">${icon('flask')}</span><div><strong>${missing} 门课程待完善实验项目</strong><p>让教学计划与实验内容保持同步</p></div>${icon('arrow')}</a>${admin() ? `<a class="todo" href="#imports"><span class="todo-icon blue">${icon('upload')}</span><div><strong>${sum(db.batches, 'review')} 条导入记录待核对</strong><p>检查疑似重复的开课信息</p></div>${icon('arrow')}</a>` : ''}<a class="todo" href="#labs"><span class="todo-icon purple">${icon('building')}</span><div><strong>${db.labs.filter(l => l.equipment === '').length} 间实验室资料待补充</strong><p>完善负责人及设备数量</p></div>${icon('arrow')}</a><div class="note-box">${icon('shield')}历史项目保留独立版本，可复用至当前学期。</div></section></div>` +
      `<section class="panel">${panelTitle('本学期教学任务', '<a class="text-link" href="#tasks">全部任务 →</a>', '课程、教师与实验安排，一目了然')}${taskTable(rows.slice(0, 4))}</section>`;
  }
  function taskTable(rows) {
    return table(['课程 / 任务编号', '授课教师', '授课班级', '计划 / 排课学时', '项目进度', '操作'], rows.map(t => {
      const ps = projects().filter(p => p.task === t.id);
      return [`<div class="course-cell"><span class="course-icon">${icon('book')}</span><div><strong>${esc(t.name)}</strong><small>${esc(t.code)} · TASK-${String(t.id).padStart(3, '0')}</small></div></div>`, `<span class="person-cell"><span class="avatar tiny">${esc(t.teacher[0])}</span>${esc(t.teacher)}</span>`, esc(t.className), `<strong>${t.hours}</strong><span class="muted"> / ${t.weeks * 2} 学时</span>`, badge(ps.length ? `${ps.length} 个项目` : '待录入', ps.length ? 'green' : 'amber'), `<div class="row-actions">${button('课表', 'task-detail', '', 'text', `data-id="${t.id}"`)}${button('实验项目', 'task-projects', '', 'text', `data-id="${t.id}"`)}</div>`];
    }));
  }
  function tasksPage() {
    const rows = tasks().filter(t => matches(t.name, t.code, t.teacher, t.className));
    return heading('COURSES & TIMETABLE', '课程与课表', '以学期为单位组织课程，清晰掌握每一周的实验教学安排。', admin() ? link('导入课表', 'imports', 'upload') : '') +
      `<section class="panel">${searchbar('搜索课程、教师或班级')}<div class="section-toolbar"><div class="tabs">${button('教学任务', 'task-tab-list', 'book', state.tab === 'list' ? 'active' : '')}${button('周课表', 'task-tab-week', 'calendar', state.tab === 'week' ? 'active' : '')}</div><span class="muted">共 ${rows.length} 个教学任务</span></div>${state.tab === 'week' ? timetable(rows) : taskTable(rows)}<div class="table-footer"><span>所有数据均为功能演示示例</span><span>共 ${rows.length} 条</span></div></section>`;
  }
  function timetable(rows) {
    const days = ['星期一', '星期二', '星期三', '星期四', '星期五'];
    return `<div class="week-toolbar">${button('上一周', 'prev-week', '', '', state.week === 1 ? 'disabled' : '')}<strong>第 ${state.week} 周</strong>${button('下一周', 'next-week', '', '', state.week === 16 ? 'disabled' : '')}</div><div class="table-wrap"><div class="week-grid"><div class="week-head">节次</div>${days.map(d => `<div class="week-head">${d}</div>`).join('')}${[1, 2, 3, 4].map(p => `<div class="period"><strong>${p * 2 - 1}–${p * 2} 节</strong><small>${['08:00–09:40', '10:00–11:40', '14:00–15:40', '16:00–17:40'][p - 1]}</small></div>${days.map((d, i) => `<div class="week-cell">${rows.filter(t => t.day === i + 1 && t.period === p && state.week <= t.weeks).map(t => `<button class="lesson lesson-${t.lab % 3}" data-action="task-detail" data-id="${t.id}"><strong>${esc(t.name)}</strong><span>${esc(t.teacher)} · ${esc(lab(t.lab).location)}</span><small>${esc(t.className)}</small></button>`).join('')}</div>`).join('')}`).join('')}</div></div>`;
  }
  function projectPage() {
    const rows = projects().filter(p => (!state.task || p.task === Number(state.task)) && (!state.type || p.type === state.type) && matches(p.name, p.code));
    const selectedTask = taskById(state.task);
    return heading('EXPERIMENT PROJECTS', '实验项目', '围绕课程构建实验内容，记录每一学期的教学探索。', `${canManageProjects() ? button('项目模板', 'project-template', 'download') : ''}${canManageProjects() ? button('新增实验项目', 'new-project', 'plus', 'primary') : ''}`) +
      `${admin() ? notice('管理员可查看教师创建的项目，无新增、编辑、删除、导入或复制权限。') : ''}${!current() ? notice('历史学期已归档，仅可查阅。切换至当前学期后，可从同课程历史任务复制项目。') : ''}` +
      `<section class="panel">${searchbar('搜索实验名称或编号', `<select aria-label="课程教学任务" data-change="task"><option value="">全部课程任务</option>${options(tasks().map(t => ({ id: t.id, name: `${t.name} · ${t.teacher}` })), state.task)}</select><select aria-label="实验类型" data-change="type"><option value="">全部实验类型</option>${options(['验证性', '综合性', '设计研究', '演示性', '其它'].map(n => ({ id: n, name: n })), state.type)}</select>`)}${selectedTask ? `<div class="task-summary"><div><span>当前课程</span><strong>${esc(selectedTask.name)}</strong></div><div><span>授课班级 / 教师</span><strong>${esc(selectedTask.className)} · ${esc(selectedTask.teacher)}</strong></div><div><span>已设置项目 / 计划学时</span><strong>${sum(projects().filter(p => p.task === selectedTask.id), 'hours')} / ${selectedTask.hours} 学时</strong></div></div>` : ''}<div class="section-toolbar"><h2>项目清单 <span class="count">${rows.length}</span></h2><div class="actions">${canManageProjects() ? `${button('Excel 导入', 'project-import', 'upload')}${button('从历史任务复制', 'copy-projects', 'copy')}` : badge(admin() ? '管理员只读' : '历史只读', 'gray')}</div></div>${table(['实验项目', '所属课程', '实验类型', '实验要求', '每组人数', '学时', '操作'], rows.map(p => [`<strong>${esc(p.name)}</strong><small>${esc(p.code)}</small>`, esc(taskById(p.task).name), badge(p.type, p.type === '设计研究' ? 'purple' : 'blue'), esc(p.requirement), `${p.group} 人`, `<strong>${p.hours}</strong>`, `<div class="row-actions">${button('详情', 'project-detail', '', 'text', `data-id="${p.id}"`)}${canManageProjects() ? `${button('编辑', 'edit-project', '', 'text', `data-id="${p.id}"`)}${button('删除', 'delete-project', '', 'text danger', `data-id="${p.id}"`)}` : ''}</div>`]))}<div class="table-footer"><span>选课人数不等同于选做项目实际参与人数</span><span>共 ${rows.length} 项 · ${sum(rows, 'hours')} 学时</span></div></section>`;
  }
  function termsPage() {
    return heading('ACADEMIC CALENDAR', '学年学期', '教学日历与学期归档，让课程内容有迹可循。', badge('教学日历', 'gray')) +
      `<div class="two-columns"><section class="panel calendar-feature"><span class="feature-icon">${icon('calendar')}</span><div class="eyebrow">CURRENT SEMESTER</div><h2>2026–2027 学年</h2><p>第一学期</p>${badge(admin() ? '当前学期 · 项目只读' : '当前学期 · 可维护本人项目', 'green')}<hr><p>2026 年 8 月 31 日 — 2027 年 1 月 31 日</p><div class="progress"><i style="width:12.5%"></i></div><div class="between muted"><span>第 2 教学周</span><span>共 16 教学周</span></div></section><section class="panel">${panelTitle('学期生成规则')}<div class="timeline"><div><strong>每年 8 月 31 日</strong><p>生成新学年及第一学期，开始新的教学周期。</p></div><div><strong>次年 2 月 1 日</strong><p>生成当前学年的第二学期。</p></div><div><strong>历史学期归档</strong><p>实验项目只读保留，可复制到同课程的当前任务。</p></div></div>${notice('本原型展示学期规则与操作结果，不运行定时任务。')}</section></div><section class="panel">${panelTitle('学期记录')}${table(['学年', '学期', '开始日期', '结束日期', '状态', '操作'], db.terms.map(t => [t.year, t.name, t.start, t.end, badge(t.current ? '当前学期' : '已归档', t.current ? 'green' : 'gray'), button('查看课程', 'term-tasks', '', 'text', `data-id="${t.id}"`)]))}</section>`;
  }
  function labsPage() {
    const rows = db.labs.filter(l => matches(l.name, l.code, l.location, l.manager));
    return heading('LABORATORY RESOURCES', '实验室', '查看实验空间与基础信息，为教学安排提供参考。', badge('实验室资料只读', 'gray')) +
      `<section class="panel">${searchbar('搜索实验室、编号或负责人')}<div class="lab-grid">${rows.map((l, i) => `<article class="lab-card"><div class="lab-visual lab-visual-${i % 3}"><span class="room-label">${esc(l.location)}</span><div class="room-art" aria-hidden="true">${icon('building')}<span></span><i></i><i></i><i></i></div>${badge(l.equipment === '' ? '资料待补充' : '资料完整', l.equipment === '' ? 'amber' : 'green')}</div><div class="lab-body"><small>${esc(l.code)}</small><h2>${esc(l.name)}</h2><div class="lab-meta"><span>${icon('users')}负责人 <b>${esc(l.manager)}</b></span><span>${icon('grid')}设备数 <b>${l.equipment === '' ? '待补充' : `${l.equipment} 台`}</b></span></div><div class="lab-card-footer"><span>${tasks().filter(t => t.lab === l.id).length} 个本学期教学任务</span>${button('查看详情 →', 'lab-detail', '', 'text', `data-id="${l.id}"`)}</div></div></article>`).join('') || empty()}</div></section>`;
  }
  function teachersPage() {
    const rows = db.teachers.filter(t => matches(t.name, t.code, t.college));
    return heading('FACULTY MANAGEMENT', '教师账号', '查看教师信息与教学任务，管理教师登录密码。', badge('管理员端', 'blue')) +
      `<section class="panel">${searchbar('搜索教师姓名、工号或学院')}${notice('管理员可重置教师密码；教师资料与实验项目仅可查看。')}${table(['教师', '工号 / 登录账号', '所属学院', '账号类型', '本学期教学任务', '操作'], rows.map(t => [`<span class="person-cell"><span class="avatar">${esc(t.name[0])}</span><strong>${esc(t.name)}</strong></span>`, esc(t.code), esc(t.college), badge(t.temporary ? '临时账号' : '正式账号', t.temporary ? 'amber' : 'green'), tasks().filter(x => x.teacher === t.name).length, button('重置密码', 'reset-password', '', 'text', `data-id="${t.id}"`)]))}</section>`;
  }
  function importsPage() {
    return heading('DATA IMPORT CENTER', '导入中心', '导入学期课表并核对原始记录，实验项目由授课教师创建。') +
      `<section class="panel">${panelTitle('导入教学资料', badge('流程演示', 'blue'))}<div class="tabs import-tabs">${['课程课表'].map(k => button(k, 'import-kind', '', state.importKind === k ? 'active' : '', `data-value="${k}"`)).join('')}</div><div class="import-steps"><span class="active"><b>1</b>选择资料</span><i></i><span><b>2</b>预览与校验</span><i></i><span><b>3</b>核对导入结果</span></div><div class="upload-zone">${icon('upload')}<h3>选择${state.importKind} Excel 文件</h3><p>支持 .xlsx，最大 10 MB；本原型不读取或上传文件内容。</p><label class="btn primary file-label">选择文件<input type="file" accept=".xlsx" id="import-file" data-file="import" aria-label="选择 Excel 文件"></label><span id="selected-file" class="muted">也可以直接体验下方示例流程</span></div><div class="between wrap"><span class="muted">重复文件校验 · 原始行保留 · 疑似重复逐行确认</span><div class="actions">${button('下载字段示例', 'import-template', 'download')}${button('演示导入流程', 'demo-import', 'arrow', 'primary')}</div></div></section><section class="panel">${panelTitle('导入批次记录', `<span class="muted">共 ${db.batches.length} 批</span>`)}${table(['文件名称', '资料类型', '来源 / 已处理', '导入时间', '状态', '操作'], db.batches.map(b => [`<span class="file-name">${icon('layers')}${esc(b.name)}</span>`, b.kind, `${b.rows} / ${b.done}`, b.date, badge(b.review ? `${b.review} 行待核对` : '处理完成', b.review ? 'amber' : 'green'), button('查看批次', 'batch-detail', '', 'text', `data-id="${b.id}"`)]))}</section>`;
  }
  function reportRows() {
    return state.report === 'labs' ? labReports().map(l => [l.code, l.name, l.courses, l.count, l.hours, l.person]) : projects().map(p => { const t = taskById(p.task); return [t.name, lab(t.lab).name, p.name, p.hours, t.people, p.requirement === '选做' ? '待确认' : '未采集']; });
  }
  function reportsPage() {
    return heading('TEACHING ANALYTICS', '统计报表', '从教学数据中看见全貌，清晰呈现实验资源与教学投入。', button('导出当前报表', 'export-report', 'download', 'primary')) +
      stats([['开设课程', new Set(tasks().map(t => t.code)).size, '门', 'book', termName()], ['实际排课学时', sum(labReports(), 'hours'), '学时', 'clock', '根据逐周课表汇总'], ['教学人时', sum(labReports(), 'person'), '人时', 'chart', '排课学时 × 课程选课人数'], ['实验项目', projects().length, '项', 'flask', `${sum(projects(), 'hours')} 个项目学时`]]) +
      `<section class="panel">${panelTitle('实验室使用分析', badge(termName(), 'gray'))}${chart()}</section><section class="panel"><div class="section-toolbar"><div class="tabs">${button('实验室使用统计', 'report-labs', '', state.report === 'labs' ? 'active' : '')}${button('实验项目清单', 'report-projects', '', state.report === 'projects' ? 'active' : '')}</div><span class="muted">${termName()}</span></div>${notice(state.report === 'labs' ? '教学人时 = 实际排课学时 × 对应课程选课人数；计划学时与实际排课学时分开统计。' : '课程选课人数仅表示任务规模。项目实际参与人数未采集时保留空缺，不以选课人数推断。')}${table(state.report === 'labs' ? ['实验室编号', '实验室名称', '课程数', '任务数', '实际排课学时', '教学人时'] : ['课程名称', '实验室', '实验项目', '项目学时', '课程选课人数', '项目实际人数'], reportRows().map(row => row.map(esc)))}<div class="table-footer"><span>静态原型导出 CSV，可使用 Excel 打开</span><span>共 ${reportRows().length} 条</span></div></section>`;
  }
  const suggestions = ['统计本学期各实验室的教学人时数', '查看本学期开设的实验课程', '列出本学期所有实验项目'];
  function aiPage() {
    return heading('AI TEACHING ASSISTANT', '智能查询', '用日常语言描述你的问题，让教学数据更容易理解。', badge('示例回答 · 未接入模型', 'purple')) +
      `<div class="ai-layout"><section class="panel ai-panel"><div class="ai-welcome"><span class="ai-emblem">${icon('spark')}</span><div class="eyebrow">EXPLORE YOUR TEACHING DATA</div><h2>关于实验教学，你想了解什么？</h2><p>从课程安排到实验人时，用一句话开始探索。</p></div><div class="suggestions">${suggestions.map((s, i) => button(`${icon(i === 0 ? 'chart' : i === 1 ? 'book' : 'flask')}<span>${s}</span>${icon('arrow')}`, 'ai-suggestion', '', '', `data-id="${i}"`)).join('')}</div><form data-form="ai" class="ai-form"><label class="sr-only" for="ai-question">输入教学数据问题</label><textarea id="ai-question" name="question" placeholder="例如：统计本学期各实验室的教学人时数" rows="3" maxlength="500" required>${esc(state.ai)}</textarea><div class="between"><span>${icon('shield')}仅展示本地示例，不执行 SQL</span><button type="submit" class="btn primary">开始查询 ${icon('arrow')}</button></div></form>${state.aiKind ? aiResult() : ''}</section><aside><section class="panel"><span class="feature-icon small">${icon('book')}</span><h2>提问小贴士</h2><p class="muted spaced">明确时间范围、查询对象和统计指标，可以得到更清晰的结果。</p><div class="prompt-tip"><span>01</span><div><strong>选择范围</strong><p>本学期 / 历史学期</p></div></div><div class="prompt-tip"><span>02</span><div><strong>描述对象</strong><p>实验室、课程、实验项目</p></div></div><div class="prompt-tip"><span>03</span><div><strong>指定指标</strong><p>教学人时、学时、项目清单</p></div></div></section><div class="note-box">${icon('info')}当前展示预设问题的回答样式。自定义问题会提示演示范围。</div></aside></div>`;
  }
  function aiResult() {
    if (state.aiKind === 'unsupported') return notice('此静态原型仅支持上方三个示例问题。请选择示例查看表格、SQL 展示与导出效果。', 'warning');
    const result = aiData();
    return `<section class="ai-result"><div class="between"><h2>${icon('spark')}查询结果</h2>${button('导出结果', 'export-ai', 'download')}</div><p class="muted">${termName()} · 本地预设示例 · 共 ${result.rows.length} 条</p>${table(result.headers, result.rows.map(row => row.map(esc)))}<details><summary>查看示例 SQL（不执行）</summary><pre>${esc(result.sql)}</pre></details></section>`;
  }
  function aiData() {
    if (state.aiKind === '0') return { headers: ['实验室', '排课学时', '教学人时'], rows: labReports().map(l => [l.name, l.hours, l.person]), sql: 'SELECT l.name, SUM(s.hours) AS scheduled_hours,\n       SUM(s.hours * t.enrollment_count) AS person_hours\nFROM laboratory l\nJOIN teaching_schedule s ON s.lab_id = l.id\nJOIN teaching_task t ON t.id = s.task_id\nWHERE t.term_id = :selected_term\nGROUP BY l.id, l.name;' };
    if (state.aiKind === '1') return { headers: ['课程号', '课程名称', '教师', '选课人数'], rows: tasks().map(t => [t.code, t.name, t.teacher, t.people]), sql: 'SELECT c.course_code, c.course_name, t.enrollment_count\nFROM teaching_task t\nJOIN teaching_course c ON c.id = t.course_id\nWHERE t.term_id = :selected_term;' };
    return { headers: ['实验项目', '课程名称', '实验类型', '学时'], rows: projects().map(p => [p.name, taskById(p.task).name, p.type, p.hours]), sql: 'SELECT p.name, p.type_code, p.hours\nFROM experiment_project p\nJOIN teaching_task t ON t.id = p.task_id\nWHERE t.term_id = :selected_term;' };
  }
  function accountPage() {
    return heading('ACCOUNT & SECURITY', '账号与安全', '管理个人信息与登录安全。') + `<div class="two-columns"><section class="panel profile"><span class="avatar huge">${admin() ? '管' : esc(teacherName()[0])}</span><h2>${admin() ? '系统管理员' : esc(teacherName())}</h2><p>${admin() ? '教务管理中心' : esc(db.teachers.find(t => t.id === 1).college)}</p>${badge(admin() ? '管理员' : '授课教师', 'blue')}<dl class="detail-grid"><div><dt>登录账号</dt><dd>${admin() ? 'admin-demo' : esc(db.teachers.find(t => t.id === 1).code)}</dd></div><div><dt>访问范围</dt><dd>${admin() ? '全部教学任务' : '本人授课任务'}</dd></div></dl>${button('退出并查看登录页', 'logout', 'logout')}</section><section class="panel">${panelTitle('修改登录密码', icon('shield'))}${notice('仅演示密码表单校验；不保存密码，也不修改真实账号。')}<form data-form="password" class="password-form"><label>原密码<input type="password" name="old" required autocomplete="current-password" placeholder="填写任意演示原密码"></label><label>新密码<input type="password" name="password" required minlength="8" autocomplete="new-password" placeholder="至少 8 位，包含字母和数字"></label><label>确认新密码<input type="password" name="confirm" required minlength="8" autocomplete="new-password" placeholder="再次输入新密码"></label><p id="password-error" class="form-error" role="alert"></p><button type="submit" class="btn primary">保存修改</button></form></section></div>`;
  }
  function loginPage() {
    return `<main class="login-screen"><section class="login-story"><a class="brand" href="#overview"><span class="brand-mark">${icon('flask')}</span><span>实验教学<span class="brand-sub">LAB TEACHING WORKSPACE</span></span></a><div><div class="eyebrow">A BETTER SPACE FOR TEACHING</div><h1>连接实验与教学，<br>让探索更有方向。</h1><p>课程管理 · 实验项目 · 资源统计 · 智能查询</p><div class="login-art" aria-hidden="true">${icon('flask')}<span class="orbit"></span><span class="orbit orbit-two"></span></div></div><span class="muted">实验教学项目管理系统 · 静态交互原型</span></section><section class="login-form-wrap"><form data-form="login" class="login-form"><span class="eyebrow">WELCOME BACK</span><h2>欢迎来到教学工作台</h2><p>选择一个角色，开始浏览功能原型。</p><label>演示角色<select name="role"><option value="admin">管理员端 · 课表导入与查看统计</option><option value="teacher">教师端 · 项目维护与智能查询</option></select></label><label>演示账号<input name="account" placeholder="请输入演示名称" value="体验用户" required maxlength="30"></label><button class="btn primary" type="submit">进入工作台 ${icon('arrow')}</button><div class="note-box">${icon('info')}无需真实账号或密码，所有内容均为演示数据。</div></form></section></main>`;
  }
  function render() {
    if (state.page === 'login') { app.innerHTML = loginPage(); document.title = '登录 · 实验教学项目管理系统'; return; }
    const route = routes.find(r => r[0] === state.page) || routes[0];
    const pages = { overview, tasks: tasksPage, projects: projectPage, terms: termsPage, labs: labsPage, teachers: teachersPage, imports: importsPage, reports: reportsPage, ai: aiPage, account: accountPage };
    document.title = `${route[1]} · 实验教学项目管理系统`;
    app.innerHTML = `<div class="app-shell ${state.mobile ? 'menu-open' : ''}"><aside class="sidebar"><a class="brand" href="#overview"><span class="brand-mark">${icon('flask')}</span><span>实验教学<span class="brand-sub">TEACHING WORKSPACE</span></span></a><nav aria-label="主导航">${routes.filter(r => !r[4] || admin()).map(r => `${r[3] ? `<div class="nav-group">${r[3]}</div>` : ''}<a href="#${r[0]}" class="nav-link ${state.page === r[0] ? 'selected' : ''}" ${state.page === r[0] ? 'aria-current="page"' : ''}>${icon(r[2])}<span>${r[1]}</span>${r[0] === 'ai' ? '<span class="ai-label">AI</span>' : r[0] === 'imports' && sum(db.batches, 'review') ? `<span class="nav-count">${sum(db.batches, 'review')}</span>` : ''}</a>`).join('')}</nav><div class="sidebar-footer"><div class="demo-status"><span class="status-dot"></span>静态演示模式</div><p>数据仅供展示，刷新即可还原</p><a href="#login">${icon('logout')}查看登录页</a></div></aside><button class="menu-scrim" data-action="toggle-menu" aria-label="关闭导航"></button><div class="workspace"><header class="topbar"><div class="breadcrumb">${button('', 'toggle-menu', 'menu', 'icon-btn mobile-menu', 'aria-label="展开导航"')}<span>${admin() ? '管理员端' : '教师端'}</span><b>/</b><strong>${route[1]}</strong></div><div class="topbar-actions"><label class="term-control">${icon('calendar')}<select aria-label="选择学期" data-change="term">${options(db.terms, state.term)}</select></label><span class="topbar-divider"></span><label class="role-control"><span class="avatar tiny">${admin() ? '管' : esc(teacherName()[0])}</span><select aria-label="切换演示角色" data-change="role"><option value="admin" ${admin() ? 'selected' : ''}>管理员端</option><option value="teacher" ${!admin() ? 'selected' : ''}>教师端 · ${esc(teacherName())}</option></select></label></div></header><main id="main" tabindex="-1">${pages[state.page]()}</main><footer class="page-footer"><span>实验教学项目管理系统</span><span>交互原型 <i>·</i> 示例数据 <i>·</i> ${admin() ? '管理员视角' : '教师视角'}</span></footer></div></div>`;
  }
  function navigate(page) {
    if (location.hash === `#${page}`) routeChange();
    else location.hash = page;
  }
  function routeChange() {
    let page = location.hash.slice(1) || 'overview';
    if (!routes.some(r => r[0] === page) && page !== 'login') page = 'overview';
    if (!admin() && routes.some(r => r[0] === page && r[4])) page = 'overview';
    if (state.page !== page) { state.query = ''; state.type = ''; }
    state.page = page;
    state.mobile = false;
    modal.close();
    render();
    window.scrollTo(0, 0);
  }
  let toastTimer;
  function toast(message) {
    const target = document.getElementById('toast');
    target.textContent = message;
    target.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => target.classList.remove('show'), 3600);
  }
  function openModal(title, content, wide = false) {
    modal.className = wide ? 'wide' : '';
    modal.innerHTML = `<div class="modal-heading"><div><span class="eyebrow">TEACHING WORKSPACE</span><h2 id="modal-title">${title}</h2></div>${button('', 'close', 'close', 'icon-btn', 'aria-label="关闭弹窗"')}</div><div class="modal-body">${content}</div>`;
    if (!modal.open) modal.showModal();
  }
  const field = (name, label, value = '', type = 'text', extra = '') => `<label>${label}<input name="${name}" value="${esc(value)}" type="${type}" ${extra}></label>`;
  const selectField = (name, label, items, value) => `<label>${label}<select name="${name}" required>${options(items.map(i => typeof i === 'string' ? { id: i, name: i } : i), value)}</select></label>`;
  const formActions = (label = '保存') => `<p class="form-error" role="alert" id="form-error"></p><div class="modal-actions">${button('取消', 'close')}<button type="submit" class="btn primary">${label}</button></div>`;
  function projectForm(id) {
    if (!canManageProjects() || (id && !canEditProject(id))) return;
    const p = projects().find(x => x.id === Number(id)) || { task: state.task || tasks()[0]?.id, name: '', hours: 4, group: 1, type: '验证性', requirement: '必做', category: '专业基础', participant: '本科', discipline: '0809', school: 'DEMO' };
    if (!tasks().length) return toast('暂无本人教学任务，请联系管理员导入课表');
    openModal(id ? '编辑实验项目' : '新增实验项目', `<form data-form="project" data-id="${id || ''}"><div class="form-grid">${selectField('task', '所属教学任务', tasks().map(t => ({ id: t.id, name: `${t.name} · ${t.teacher}` })), p.task)}${field('code', '实验编号（实验室编号 + 三位后缀）', p.code || projectCodes(p.task)[0] || '', 'text', 'readonly aria-describedby="project-code-help"')}<p id="project-code-help" class="muted">编号由所属实验室自动分配，如 36-606-001，不可手动修改。</p>${field('name', '实验名称', p.name, 'text', 'required maxlength="50"')}${field('school', '学校代码（演示）', p.school, 'text', 'required maxlength="32"')}${selectField('category', '实验类别', ['基础', '专业基础', '专业', '其它'], p.category)}${selectField('type', '实验类型', ['演示性', '验证性', '综合性', '设计研究', '其它'], p.type)}${field('discipline', '实验所属学科代码', p.discipline, 'text', 'required maxlength="16"')}${selectField('requirement', '实验要求', ['必做', '选做', '其它'], p.requirement)}${selectField('participant', '实验者类别', ['博士', '硕士', '本科', '专科', '其他'], p.participant)}${field('group', '每组人数', p.group, 'number', 'min="1" max="99" required')}${field('hours', '实验学时', p.hours, 'number', 'min="0.01" max="9999" step="0.01" required')}</div>${formActions('保存项目')}</form>`, true);
  }
  function projectDetail(id) {
    const p = projects().find(x => x.id === Number(id));
    if (!p) return;
    openModal('实验项目详情', `<div class="detail-hero"><span class="feature-icon">${icon('flask')}</span><div><h3>${esc(p.name)}</h3><p>${esc(p.code)} · ${esc(taskById(p.task).name)}</p></div></div><dl class="detail-grid">${[['实验类别', p.category], ['实验类型', p.type], ['实验要求', p.requirement], ['所属学科代码', p.discipline], ['实验者类别', p.participant], ['每组人数', `${p.group} 人`], ['实验学时', `${p.hours} 学时`], ['学校代码', p.school], ['所属学期', termName()]].map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>${notice(admin() ? '管理员仅可查看此项目，项目由授课教师维护。' : current() ? '这是本人当前学期项目，可在项目列表中编辑。' : '历史归档项目，仅供查阅与复用。')}<div class="modal-actions">${button('关闭', 'close')}</div>`);
  }
  function taskDetail(id) {
    const t = tasks().find(x => x.id === Number(id));
    if (!t) return;
    openModal('教学任务与排课详情', `<div class="detail-hero"><span class="feature-icon">${icon('book')}</span><div><h3>${esc(t.name)}</h3><p>${esc(t.code)} · ${termName()}</p></div></div><dl class="detail-grid">${[['任课教师', t.teacher], ['授课班级', t.className], ['选课人数', `${t.people} 人`], ['计划学时', t.hours], ['排课学时', t.weeks * 2], ['实验室', lab(t.lab).name]].map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>${t.weeks ? table(['教学周', '星期', '节次', '学时', '上课地点'], Array.from({ length: t.weeks }, (_, i) => [`第 ${i + 1} 周`, `星期${'一二三四五'[t.day - 1]}`, `${t.period * 2 - 1}–${t.period * 2} 节`, '2', esc(lab(t.lab).location)])) : notice('此任务尚无排课明细，可通过课表导入流程补充。')}<div class="modal-actions">${button('关闭', 'close')}${button('进入实验项目', 'task-projects', '', 'primary', `data-id="${t.id}"`)}</div>`, true);
  }
  function labDetail(id) {
    const l = db.labs.find(x => x.id === Number(id));
    openModal('实验室详情', `<div class="detail-hero"><span class="feature-icon">${icon('building')}</span><div><h3>${esc(l.name)}</h3><p>${esc(l.code)} · ${esc(l.location)}</p></div></div><dl class="detail-grid"><div><dt>负责人</dt><dd>${esc(l.manager)}</dd></div><div><dt>设备数量</dt><dd>${l.equipment === '' ? '待补充' : `${l.equipment} 台`}</dd></div><div><dt>本学期教学任务</dt><dd>${tasks().filter(t => t.lab === l.id).length} 个</dd></div></dl><div class="modal-actions">${button('关闭', 'close')}</div>`);
  }
  function resetTeacherPassword(id, confirmed = false) {
    if (!admin()) return toast('仅管理员可重置教师密码');
    const teacher = db.teachers.find(t => t.id === Number(id));
    if (!teacher) return toast('未找到该教师账号');
    const account = `<p>教师：${esc(teacher.name)} · 账号：${esc(teacher.code)}</p>`;
    if (!confirmed) {
      openModal('重置教师密码', `${account}<p>确认重置此教师的登录密码？</p>${notice('静态原型仅演示操作流程，不修改真实账号密码。')}<div class="modal-actions">${button('取消', 'close')}${button('确认重置', 'commit-password', '', 'primary', `data-id="${teacher.id}"`)}</div>`);
      return;
    }
    openModal('教师密码重置成功（演示）', `${account}${notice('演示已完成。正式系统在此一次性展示新的临时密码，教师登录后应立即修改。')}<div class="credential">［临时密码展示区］</div><div class="modal-actions">${button('完成', 'close', '', 'primary')}</div>`);
  }
  function copyProjects() {
    if (!canManageProjects() || !tasks().length) return toast('仅教师可向本人当前任务复制项目');
    const target = Number(state.task || tasks()[0].id);
    const sources = db.tasks.filter(t => t.term === 'history' && (admin() || t.teacher === teacherName()) && db.projects.some(p => p.task === t.id));
    openModal('从历史任务复制实验项目', `${notice('仅支持同课程复制。历史原项目保留，当前学期创建独立版本。')}<form data-form="copy"><div class="form-grid">${selectField('target', '当前学期目标任务', tasks().map(t => ({ id: t.id, name: t.name })), target)}${selectField('source', '历史来源任务', sources.map(t => ({ id: t.id, name: `${t.name} · 2025–2026 第二学期` })), sources[0]?.id)}</div>${formActions('复制项目')}</form>`);
  }
  function projectImport() {
    if (!canManageProjects() || !tasks().length) return toast('仅教师可向本人当前任务导入项目');
    openModal('导入实验项目', `${notice('演示选择目标课程和文件的流程；不会读取文件内容。确认后将添加一条固定示例项目。')}<form data-form="project-import">${selectField('task', '目标教学任务', tasks().map(t => ({ id: t.id, name: t.name })), state.task || tasks()[0].id)}<div class="file-pick"><label>选择 Excel 文件（可选）<input type="file" accept=".xlsx" data-file="project"></label></div>${formActions('添加示例项目')}</form>`);
  }
  function batchDetail(id) {
    const b = db.batches.find(x => x.id === Number(id));
    openModal('导入批次与逐行核对', `<p class="file-name">${icon('layers')}${esc(b.name)}</p>${notice('以下是固定的原始行示例。核对操作只更新此原型批次状态，不创建真实教学数据。')}<div class="batch-stats"><span>来源行数 <b>${b.rows}</b></span><span>已处理 <b>${b.done}</b></span><span>待核对 <b>${b.review}</b></span></div>${table(['原始行号', '示例课程 / 数据', '数据疑点', '状态', '操作'], Array.from({ length: b.review || 1 }, (_, i) => [b.done + i + 1, b.kind === '课程课表' ? '数据库原理与应用 · 软件工程 2401' : esc(b.kind), b.review ? '课程与教师相同，请核实是否为另一独立开课' : '无数据疑点', badge(b.review ? '待核对' : '处理完成', b.review ? 'amber' : 'green'), b.review ? button('确认独立开课', 'confirm-row', '', 'text', `data-id="${b.id}"`) : '已完成']))}<details><summary>展开原始行示例</summary><pre>${esc(JSON.stringify({ 工作表: '教学资料', 课程: '数据库原理与应用', 教师: '陈老师', 班级: '软件工程2401', 备注: '固定演示数据，非所选文件内容' }, null, 2))}</pre></details><div class="modal-actions">${button('关闭', 'close')}</div>`, true);
  }
  function demoImport() {
    openModal('导入预览', `${notice('下表为固定示例，不是文件解析结果。演示校验与导入结果，不新增真实业务资料。')}<div class="import-steps"><span><b>1</b>选择资料</span><i></i><span class="active"><b>2</b>预览与校验</span><i></i><span><b>3</b>核对结果</span></div>${table(['行号', '资料类型', '校验结果'], [['1', state.importKind, badge('校验通过', 'green')], ['2', state.importKind, badge('校验通过', 'green')], ['3', state.importKind, badge('疑似重复，待核对', 'amber')]])}<div class="modal-actions">${button('取消', 'close')}${button('确认演示导入', 'commit-import', '', 'primary')}</div>`);
  }
  function confirmDelete(id) {
    if (!canEditProject(id)) return;
    const row = db.projects.find(p => p.id === Number(id));
    openModal('删除演示记录', `<p>确认删除“${esc(row.name)}”？仅移除当前页面内的演示数据，刷新后恢复。</p><div class="modal-actions">${button('取消', 'close')}${button('确认删除', 'commit-delete', '', 'danger solid', `data-kind="project" data-id="${id}"`)}</div>`);
  }
  function downloadCsv(name, headers, rows) {
    // 防止表格应用将用户输入当作公式执行。
    const cell = value => { let s = String(value == null ? '' : value); if (/^[\s]*[=+@-]/.test(s)) s = `'${s}`; return `"${s.replace(/"/g, '""')}"`; };
    const csv = '\uFEFF' + [headers, ...rows].map(row => row.map(cell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const a = document.createElement('a');
    a.href = url; a.download = `${name}.csv`; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    toast('已导出 CSV，可使用 Excel 打开');
  }
  document.addEventListener('click', event => {
    const target = event.target.closest('[data-action]');
    if (!target || target.disabled) return;
    const action = target.dataset.action;
    const id = target.dataset.id;
    if (['new-project', 'edit-project', 'delete-project', 'copy-projects', 'project-import', 'project-template'].includes(action) && !canManageProjects()) return toast('只有教师可维护本人当前学期实验项目');
    if (['demo-import', 'commit-import', 'confirm-row', 'batch-detail', 'import-template', 'export-report', 'report-labs', 'report-projects'].includes(action) && !admin()) return toast('此功能仅对管理员开放');
    const localRender = fn => { fn(); render(); };
    const handlers = {
      close: () => modal.close(),
      'toggle-menu': () => localRender(() => { state.mobile = !state.mobile; }),
      'reset-filter': () => localRender(() => { state.query = ''; state.type = ''; state.task = ''; }),
      'new-project': () => projectForm(), 'edit-project': () => projectForm(id), 'project-detail': () => projectDetail(id),
      'task-detail': () => taskDetail(id),
      'task-projects': () => { state.task = id; modal.close(); navigate('projects'); },
      'task-tab-list': () => localRender(() => { state.tab = 'list'; }),
      'task-tab-week': () => localRender(() => { state.tab = 'week'; }),
      'open-week': () => { state.tab = 'week'; navigate('tasks'); },
      'prev-week': () => localRender(() => { state.week = Math.max(1, state.week - 1); }),
      'next-week': () => localRender(() => { state.week = Math.min(16, state.week + 1); }),
      'term-tasks': () => { state.term = id; state.task = ''; navigate('tasks'); },
      'lab-detail': () => labDetail(id),
      'reset-password': () => resetTeacherPassword(id),
      'commit-password': () => resetTeacherPassword(id, true),
      'delete-project': () => confirmDelete(id),
      'commit-delete': () => { if (target.dataset.kind !== 'project' || !canEditProject(id)) return toast('无权删除此记录'); db.projects = db.projects.filter(p => p.id !== Number(id)); modal.close(); render(); toast('演示项目已删除，刷新可恢复'); },
      'copy-projects': copyProjects, 'project-import': projectImport,
      'project-template': () => downloadCsv('实验项目字段示例', ['实验名称', '实验编号', '学校代码', '实验类别', '实验类型', '实验所属学科', '实验要求', '实验者类别', '每组人数', '实验学时'], [['SQL 基础实验', '36-606-001', 'DEMO', '专业基础', '验证性', '0809', '必做', '本科', 1, 4]]),

      'import-template': () => { const templates = { '课程课表': ['学年', '学期', '课程号', '课程名', '实验总学时', '授课教师', '上课地点', '上课班级', '上课时间', '选课人数'], '教师账号': ['工号', '姓名', '学院'], '实验室资料': ['实验室编号', '实验室名称', '位置', '负责人', '设备数'] }; downloadCsv(`${state.importKind}字段示例`, templates[state.importKind], []); },
      'demo-import': demoImport,
      'commit-import': () => { const b = { id: Date.now(), name: `${state.importKind}流程演示.xlsx`, kind: state.importKind, date: '2026-09-11 10:00', rows: 3, done: 2, review: 1 }; db.batches.unshift(b); render(); batchDetail(b.id); toast('示例批次已生成，请核对待确认行'); },
      'batch-detail': () => batchDetail(id),
      'confirm-row': () => { const b = db.batches.find(x => x.id === Number(id)); if (b.review) { b.review--; b.done++; } render(); batchDetail(id); toast('已完成本行的核对演示'); },
      'report-labs': () => localRender(() => { state.report = 'labs'; }),
      'report-projects': () => localRender(() => { state.report = 'projects'; }),
      'export-report': () => downloadCsv(state.report === 'labs' ? '实验室教学统计' : '实验项目清单', state.report === 'labs' ? ['实验室编号', '实验室名称', '课程数', '任务数', '实际排课学时', '教学人时'] : ['课程名称', '实验室', '实验项目', '项目学时', '课程选课人数', '项目实际人数'], reportRows()),
      'ai-suggestion': () => localRender(() => { state.ai = suggestions[Number(id)]; state.aiKind = id; }),
      'export-ai': () => { const result = aiData(); downloadCsv('智能查询示例结果', result.headers, result.rows); },
      logout: () => navigate('login')
    };
    if (handlers[action]) handlers[action]();
  });
  document.addEventListener('change', event => {
    const el = event.target;
    if (el.name === 'task' && el.form && el.form.dataset.form === 'project') {
      const existing = db.projects.find(p => p.id === Number(el.form.dataset.id));
      const task = taskById(el.value);
      const keepCode = existing && task && taskById(existing.task).lab === task.lab;
      el.form.elements.code.value = keepCode ? existing.code : projectCodes(el.value)[0] || '';
    }
    if (el.dataset.change) {
      state[el.dataset.change] = el.value;
      if (['term', 'role'].includes(el.dataset.change)) { state.task = ''; state.query = ''; state.type = ''; state.ai = ''; state.aiKind = ''; routeChange(); }
      else render();
    }
    if (el.dataset.file && el.files[0]) {
      const file = el.files[0];
      if (!/\.xlsx$/i.test(file.name) || file.size > 10 * 1024 * 1024) { el.value = ''; toast('请选择小于 10 MB 的 .xlsx 文件'); return; }
      if (el.dataset.file === 'import') document.getElementById('selected-file').textContent = `已选择：${file.name}（仅展示文件名）`;
      toast('已选择文件，可继续体验示例流程');
    }
  });
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!form.dataset.form) return;
    event.preventDefault();
    const values = Object.fromEntries(new FormData(form));
    Object.keys(values).forEach(k => { if (typeof values[k] === 'string') values[k] = values[k].trim(); });
    const kind = form.dataset.form;
    const fail = message => { const node = form.querySelector('.form-error'); if (node) node.textContent = message; else toast(message); };
    if (kind === 'search') { state.query = values.query; render(); return; }
    if (kind === 'login') { state.role = values.role; state.task = ''; navigate('overview'); return; }
    if (kind === 'ai') { state.ai = values.question; const index = suggestions.indexOf(values.question); state.aiKind = index < 0 ? 'unsupported' : String(index); render(); return; }
    if (kind === 'password') {
      if (!/[a-zA-Z]/.test(values.password) || !/[0-9]/.test(values.password) || values.password.length < 8) return fail('新密码至少 8 位，并包含字母和数字。');
      if (values.password !== values.confirm) return fail('两次输入的新密码不一致。');
      if (values.password === values.old) return fail('新密码不能与原密码相同。');
      form.reset(); form.querySelector('.form-error').textContent = ''; toast('密码校验通过，演示完成；真实密码未修改'); return;
    }
    if (['lab', 'teacher', 'task'].includes(kind)) return fail('基础资料仅可查看，请通过课表导入流程管理教学安排。');
    if (['project', 'copy', 'project-import'].includes(kind) && !canManageProjects()) return fail('仅教师可维护本人当前学期实验项目。');
    if (kind === 'project') {
      if (!values.name || !values.school || !values.discipline) return fail('请完整填写实验名称、学校代码和学科代码。');
      if (!tasks().some(t => t.id === Number(values.task))) return fail('请选择有效教学任务。');
      if (form.dataset.id && !canEditProject(form.dataset.id)) return fail('无权修改其他教师或历史学期的项目。');
      const id = Number(form.dataset.id) || Date.now();
      const original = db.projects.find(p => p.id === id);
      const sameLab = original && taskById(original.task).lab === taskById(values.task).lab;
      const code = sameLab ? original.code : projectCodes(values.task)[0];
      if (!code) return fail('该实验室三位编号已用完，无法继续新增项目。');
      const record = { ...values, id, code, task: Number(values.task), hours: Number(values.hours), group: Number(values.group) };
      const index = db.projects.findIndex(p => p.id === id);
      if (index < 0) db.projects.push(record); else db.projects[index] = record;
      state.task = String(record.task); state.query = ''; state.type = '';
    }
    if (kind === 'copy') {
      const target = tasks().find(t => t.id === Number(values.target));
      const source = taskById(values.source);
      if (!source || !target || source.term !== 'history' || source.teacher !== teacherName() || source.code !== target.code) return fail('来源与目标必须属于同一课程，请重新选择。');
      const existing = db.projects.filter(p => p.task === target.id).map(p => p.name);
      const copies = db.projects.filter(p => p.task === source.id && !existing.includes(p.name));
      if (!copies.length) return fail('来源项目已复制到该任务，无需重复复制。');
      const codes = projectCodes(target.id, copies.length);
      if (codes.length !== copies.length) return fail('该实验室可用编号不足，未复制任何项目。');
      copies.forEach((p, i) => { const id = Date.now() + i; db.projects.push({ ...p, id, task: target.id, code: codes[i] }); });
      state.task = String(target.id);
    }
    if (kind === 'project-import') {
      if (!canEditTask(values.task)) return fail('只能向本人当前学期教学任务导入项目。');
      const code = projectCodes(values.task)[0];
      if (!code) return fail('该实验室三位编号已用完，无法导入项目。');
      const id = Date.now();
      db.projects.push({ id, task: Number(values.task), code, name: 'Excel 导入流程示例项目', type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 4 });
      state.task = values.task;
    }
    modal.close();
    if (['project', 'copy', 'project-import'].includes(kind) && state.page !== 'projects') navigate('projects'); else render();
    toast('演示数据已保存，刷新页面可恢复初始状态');
  });
  modal.addEventListener('click', event => { if (event.target === modal) { const r = modal.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) modal.close(); } });
  window.addEventListener('hashchange', routeChange);
  routeChange();
})();
