/* 原生静态前端：未登录时显示本地示例，登录后使用 Spring Boot 教学 API。 */
'use strict';
(() => {
  const db = window.PrototypeData;
  const api = window.TeachingApi;
  const app = document.getElementById('app');
  const modal = document.getElementById('modal');
  const state = { role: localStorage.getItem('teachingRole') || 'admin', term: 'current', page: 'overview', query: '', task: '', type: '', tab: 'list', week: 1, report: 'labs', importKind: 'timetable', importResult: null, ai: '', aiKind: '', mobile: false, loading: false, loadingSchedules: false };
  const importKinds = [
    { id: 'timetable', name: '课程课表', description: '上传教务课表，后端解析课程、教师、实验室与逐周排课。' },
    { id: 'teachers', name: '教师账号', description: '按真实工号维护教师资料；临时账号可在核实后更新工号。' },
    { id: 'labs', name: '实验室资料', description: '按实验室编号更新名称、位置、负责人和设备数量。' }
  ];
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
  const teacherName = () => (db.currentTeacher && db.currentTeacher.name) || db.teachers[0]?.name || '当前教师';
  const current = () => db.remote ? String(state.term) === String(db.currentTermId) : state.term === 'current';
  const termById = id => db.terms.find(t => String(t.id) === String(id));
  const termName = () => termById(state.term)?.name || db.terms[0]?.name || '当前学期';
  const lab = id => db.labs.find(l => l.id === Number(id)) || { name: '未指定', location: '待安排' };
  const taskById = id => db.tasks.find(t => t.id === Number(id));
  const tasks = () => db.tasks.filter(t => String(t.term) === String(state.term) && (db.remote || admin() || t.teacher === teacherName()));
  const taskUsesLab = (task, labId) => {
    const item = db.labs.find(l => l.id === Number(labId));
    if (!item) return Number(task.lab) === Number(labId);
    return Number(task.lab) === Number(labId)
      || String(task.labNames || '').split(/[、,，;；]/).map(value => value.trim()).includes(item.name);
  };
  const projects = () => db.projects.filter(p => tasks().some(t => t.id === p.task));
  const loadedProjectTasks = () => new Set((db.loadedProjectTasks || []).map(Number));
  const projectCountFor = task => loadedProjectTasks().has(Number(task.id))
    ? db.projects.filter(project => project.task === task.id).length
    : Number(task.projectCount || 0);
  const canManageProjects = () => state.role === 'teacher' && current();
  const canEditTask = id => canManageProjects() && tasks().some(t => t.id === Number(id));
  const canEditProject = id => {
    const project = db.projects.find(p => p.id === Number(id));
    return !!project && canEditTask(project.task);
  };
  function projectCodes(taskId, count = 1) {
    const task = taskById(taskId);
    if (!task || !task.lab) return [];
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
  const empty = (text = '没有找到符合条件的记录') => `<div class="empty">${icon('search')}<strong>${text}</strong><span>${db.remote ? '可以调整筛选条件，或先通过导入与维护入口补齐数据。' : '试试调整筛选条件，或添加一条演示数据。'}</span></div>`;
  const loadingBlock = text => `<div class="empty">${icon('clock')}<strong>${text}</strong><span>正在读取后端数据库，请稍候。</span></div>`;
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
    if (db.remote && db.report && Array.isArray(db.report.labs)) {
      return db.report.labs.map(row => ({ code: row.lab_code, name: row.lab_name, courses: Number(row.course_count || 0), count: Number(row.task_count || 0), hours: Number(row.scheduled_hours || 0), person: Number(row.person_hours || 0) }));
    }
    return db.labs.map(l => {
      const rows = tasks().filter(t => t.lab === l.id);
      return { ...l, courses: new Set(rows.map(t => t.courseCode || t.code)).size, count: rows.length, hours: rows.reduce((n, t) => n + t.weeks * 2, 0), person: rows.reduce((n, t) => n + t.weeks * 2 * t.people, 0) };
    }).filter(l => l.count);
  }
  function chart() {
    const rows = labReports().sort((a, b) => b.person - a.person);
    const max = Math.max(...rows.map(l => l.person), 1);
    return `<div class="bar-chart">${rows.map((l, i) => `<div class="bar-row"><span title="${esc(l.name)}">${esc(l.name)}</span><div class="bar-track"><div class="bar-fill color-${i % 3}" style="width:${Math.round(l.person / max * 100)}%"></div></div><strong>${number(l.person)}</strong></div>`).join('') || empty('当前学期暂无排课')}<div class="chart-axis"><span>按实际排课统计</span><span>单位：人时</span></div></div>`;
  }
  function overview() {
    const rows = tasks();
    const projectTotal = rows.reduce((total, task) => total + projectCountFor(task), 0);
    const missing = rows.filter(task => projectCountFor(task) === 0).length;
    const warnings = db.remote && db.dashboard && Array.isArray(db.dashboard.warnings) ? db.dashboard.warnings : [];
    return heading('TEACHING WORKSPACE', `${admin() ? '管理员' : esc(teacherName())}，欢迎回来`, '把教学安排得井井有条，让每一次实验都有所收获。', `${button('查看本周课表', 'open-week', 'calendar')}${canManageProjects() ? button('新增实验项目', 'new-project', 'plus', 'primary') : ''}`) +
      `<section class="semester-banner"><div><span class="banner-label"><span class="status-dot"></span>${current() ? '新学期 · 教学进行中' : '历史学期 · 已归档'}</span><h2>${termName()}</h2><p>从课程计划到实验项目，在这里连接教学的每一步。</p><a href="#projects">进入实验项目 ${icon('arrow')}</a></div><div class="banner-art" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="art-card art-back">${icon('book')}<i></i><i></i></div><div class="art-card art-front">${icon('flask')}<span>LAB</span><div class="art-lines"><i></i><i></i></div></div><span class="art-spark">✦</span><span class="art-dot"></span></div><div class="banner-week"><strong>${current() ? '02' : '16'}</strong><span>教学周 / 16 周</span><div class="mini-progress"><i style="width:${current() ? 12.5 : 100}%"></i></div></div></section>` +
      stats([
        ['教学任务', rows.length, '个', 'book', `${new Set(rows.map(t => t.courseCode)).size} 门课程 · ${current() ? '本学期' : '历史学期'}`],
        ['实验项目', projectTotal, '项', 'flask', `${sum(projects().filter(p => rows.some(t => t.id === p.task)), 'hours')} 项目学时 · 持续完善教学内容`],
        ['使用实验室', new Set(rows.map(t => t.lab)).size, '间', 'building', '覆盖当前教学任务的实验场地'],
        admin() ? ['教学人时', sum(labReports(), 'person'), '人时', 'chart', '实际排课学时 × 课程选课人数'] : ['待完善课程', missing, '门', 'book', '为本人课程补充实验内容']
      ]) +
      (warnings.length ? notice(esc(warnings.join(' ')), 'warning') : '') +
      `<div class="dashboard-grid"><section class="panel">${admin() ? `${panelTitle('实验室教学分布', '<a class="text-link" href="#reports">查看报表 →</a>', '本学期各实验室教学人时')}${chart()}` : `${panelTitle('我的教学安排', '<a class="text-link" href="#tasks">查看课表 →</a>', '仅展示本人当前所选学期课程')}${rows.map(t => `<a class="todo" href="#tasks"><span class="todo-icon blue">${icon('book')}</span><div><strong>${esc(t.name)}</strong><p>${esc(t.className)} · ${esc(lab(t.lab).location)}</p></div>${icon('arrow')}</a>`).join('')}<a class="text-link" href="#ai">使用智能查询探索本人教学数据 →</a>`}</section><section class="panel">${panelTitle('待办与提醒', '<span class="small-dot"></span>')}<a class="todo" href="#projects"><span class="todo-icon amber">${icon('flask')}</span><div><strong>${missing} 个教学任务待完善实验项目</strong><p>让教学计划与实验内容保持同步</p></div>${icon('arrow')}</a>${admin() ? `<a class="todo" href="#imports"><span class="todo-icon blue">${icon('upload')}</span><div><strong>${sum(db.batches, 'review')} 条导入记录待核对</strong><p>检查疑似重复的开课信息</p></div>${icon('arrow')}</a>` : ''}<a class="todo" href="#labs"><span class="todo-icon purple">${icon('building')}</span><div><strong>${db.labs.filter(l => l.equipment === '').length} 间实验室资料待补充</strong><p>完善负责人及设备数量</p></div>${icon('arrow')}</a><div class="note-box">${icon('shield')}历史项目保留独立版本，可复用至当前学期。</div></section></div>` +
      `<section class="panel">${panelTitle('本学期教学任务', '<a class="text-link" href="#tasks">全部任务 →</a>', '课程、教师与实验安排，一目了然')}${taskTable(rows.slice(0, 4))}</section>`;
  }
  function taskTable(rows) {
    return table(['课程 / 任务编号', '授课教师', '授课班级', '计划 / 排课学时', '项目进度', '操作'], rows.map(t => {
      const count = projectCountFor(t);
      return [`<div class="course-cell"><span class="course-icon">${icon('book')}</span><div><strong>${esc(t.name)}</strong><small>${esc(t.courseCode || t.code)} · ${esc(t.code)}</small></div></div>`, `<span class="person-cell"><span class="avatar tiny">${esc((t.teacher || '待')[0])}</span>${esc(t.teacher || '待确认')}</span>`, esc(t.className), `<strong>${t.hours}</strong><span class="muted"> / ${t.scheduledHours || 0} 学时</span>`, badge(count ? `${count} 个项目` : '待录入', count ? 'green' : 'amber'), `<div class="row-actions">${button('课表', 'task-detail', '', 'text', `data-id="${t.id}"`)}${button('实验项目', 'task-projects', '', 'text', `data-id="${t.id}"`)}</div>`];
    }));
  }
  function tasksPage() {
    const rows = tasks().filter(t => matches(t.name, t.courseCode, t.code, t.teacher, t.className));
    return heading('COURSES & TIMETABLE', '课程与课表', '以学期为单位组织课程，清晰掌握每一周的实验教学安排。', `${admin() ? button('创建教学任务', 'new-task', 'plus', 'primary') : ''}${admin() ? link('导入课表', 'imports', 'upload') : ''}`) +
      `<section class="panel">${searchbar('搜索课程、教师或班级')}<div class="section-toolbar"><div class="tabs">${button('教学任务', 'task-tab-list', 'book', state.tab === 'list' ? 'active' : '')}${button('周课表', 'task-tab-week', 'calendar', state.tab === 'week' ? 'active' : '')}</div><span class="muted">共 ${rows.length} 个教学任务</span></div>${state.tab === 'week' ? timetable(rows) : taskTable(rows)}<div class="table-footer"><span>${db.remote ? '数据来自后端数据库' : '所有数据均为功能演示示例'}</span><span>共 ${rows.length} 条</span></div></section>`;
  }
  function timetable(rows) {
    const days = ['星期一', '星期二', '星期三', '星期四', '星期五'];
    if (state.loadingSchedules) return loadingBlock('正在读取逐周排课');
    const slots = [];
    rows.forEach(task => {
      if (Array.isArray(task.schedule)) {
        task.schedule.forEach(slot => slots.push({ task, slot }));
        return;
      }
      const weeks = Math.max(1, Number(task.weeks || 1));
      for (let week = 1; week <= weeks; week += 1) {
        slots.push({
          task,
          slot: {
            teaching_week: week,
            weekday: task.day,
            period_start: task.period * 2 - 1,
            period_end: task.period * 2,
            lab_name: lab(task.lab).name
          }
        });
      }
    });
    if (db.remote && rows.length && !slots.length) return empty('当前学期任务尚未录入排课明细');
    return `<div class="week-toolbar">${button('上一周', 'prev-week', '', '', state.week === 1 ? 'disabled' : '')}<strong>第 ${state.week} 周</strong>${button('下一周', 'next-week', '', '', state.week === 20 ? 'disabled' : '')}</div><div class="table-wrap"><div class="week-grid"><div class="week-head">节次</div>${days.map(d => `<div class="week-head">${d}</div>`).join('')}${[1, 2, 3, 4].map(p => `<div class="period"><strong>${p * 2 - 1}–${p * 2} 节</strong><small>${['08:00–09:40', '10:00–11:40', '14:00–15:40', '16:00–17:40'][p - 1]}</small></div>${days.map((d, i) => `<div class="week-cell">${slots.filter(({ slot }) => Number(slot.teaching_week) === state.week && Number(slot.weekday) === i + 1 && Math.ceil(Number(slot.period_start) / 2) === p).map(({ task, slot }) => `<button class="lesson lesson-${task.lab % 3}" data-action="task-detail" data-id="${task.id}"><strong>${esc(task.name)}</strong><span>${esc(task.teacher)} · ${esc(slot.lab_name || lab(slot.lab_id).location)}</span><small>${esc(task.className)} · ${slot.period_start}–${slot.period_end} 节</small></button>`).join('')}</div>`).join('')}`).join('')}</div></div>`;
  }
  function projectPage() {
    const rows = projects().filter(p => (!state.task || p.task === Number(state.task)) && (!state.type || p.type === state.type) && matches(p.name, p.code));
    const selectedTask = taskById(state.task);
    return heading('EXPERIMENT PROJECTS', '实验项目', '围绕课程构建实验内容，记录每一学期的教学探索。', `${canManageProjects() ? button('项目模板', 'project-template', 'download') : ''}${canManageProjects() ? button('新增实验项目', 'new-project', 'plus', 'primary') : ''}`) +
      `${admin() ? notice('管理员可查看教师创建的项目，无新增、编辑、删除、导入或复制权限。') : ''}${!current() ? notice('历史学期已归档，仅可查阅。切换至当前学期后，可从同课程历史任务复制项目。') : ''}` +
      `<section class="panel">${searchbar('搜索实验名称或编号', `<select aria-label="课程教学任务" data-change="task"><option value="">全部课程任务</option>${options(tasks().map(t => ({ id: t.id, name: `${t.name} · ${t.teacher}` })), state.task)}</select><select aria-label="实验类型" data-change="type"><option value="">全部实验类型</option>${options(['验证性', '综合性', '设计研究', '演示性', '其它'].map(n => ({ id: n, name: n })), state.type)}</select>`)}${selectedTask ? `<div class="task-summary"><div><span>当前课程</span><strong>${esc(selectedTask.name)}</strong></div><div><span>授课班级 / 教师</span><strong>${esc(selectedTask.className)} · ${esc(selectedTask.teacher)}</strong></div><div><span>已设置项目 / 计划学时</span><strong>${projectCountFor(selectedTask)} 项 / ${selectedTask.hours} 计划学时</strong></div></div>` : ''}<div class="section-toolbar"><h2>项目清单 <span class="count">${rows.length}</span></h2><div class="actions">${canManageProjects() ? `${button('Excel 导入', 'project-import', 'upload')}${button('从历史任务复制', 'copy-projects', 'copy')}` : badge(admin() ? '管理员只读' : '历史只读', 'gray')}</div></div>${table(['实验项目', '所属课程', '实验类型', '实验要求', '每组人数', '学时', '操作'], rows.map(p => [`<strong>${esc(p.name)}</strong><small>${esc(p.code)}</small>`, esc((taskById(p.task) || {}).name || '未知任务'), badge(p.type, p.type === '设计研究' ? 'purple' : 'blue'), esc(p.requirement), `${p.group} 人`, `<strong>${p.hours}</strong>`, `<div class="row-actions">${button('详情', 'project-detail', '', 'text', `data-id="${p.id}"`)}${canManageProjects() ? `${button('编辑', 'edit-project', '', 'text', `data-id="${p.id}"`)}${button('删除', 'delete-project', '', 'text danger', `data-id="${p.id}"`)}` : ''}</div>`]))}<div class="table-footer"><span>选课人数不等同于选做项目实际参与人数</span><span>共 ${rows.length} 项 · ${sum(rows, 'hours')} 学时</span></div></section>`;
  }
  function termsPage() {
    const currentTerm = termById(db.currentTermId) || db.terms[0] || {};
    const termLabel = currentTerm.termNo === 2 ? '第二学期' : currentTerm.termNo === 1 ? '第一学期' : currentTerm.name || '';
    const statusText = term => term.current ? '当前开放' : term.status === 'DRAFT' ? '未来学期' : '历史归档';
    return heading('ACADEMIC CALENDAR', '学年学期', '教学日历与学期归档，让课程内容有迹可循。', `${admin() ? button('补齐当前学期', 'generate-term', 'plus', 'primary') : badge('教学日历', 'gray')}`) +
      `<div class="two-columns"><section class="panel calendar-feature"><span class="feature-icon">${icon('calendar')}</span><div class="eyebrow">CURRENT SEMESTER</div><h2>${esc(currentTerm.year || '尚未生成')} 学年</h2><p>${esc(termLabel)}</p>${badge(admin() ? '当前学期 · 项目按后台权限维护' : '当前学期 · 可维护本人项目', 'green')}<hr><p>${esc(currentTerm.start || '待生成')} — ${esc(currentTerm.end || '待生成')}</p><div class="progress"><i style="width:12.5%"></i></div><div class="between muted"><span>开放学期可维护教学任务与项目</span><span>后端自动判定状态</span></div></section><section class="panel">${panelTitle('学期生成规则')}<div class="timeline"><div><strong>每年 8 月 31 日</strong><p>生成新学年及第一学期，开始新的教学周期。</p></div><div><strong>次年 2 月 1 日</strong><p>生成当前学年的第二学期。</p></div><div><strong>历史学期归档</strong><p>实验项目只读保留，可复制到同课程的当前任务。</p></div></div>${notice(db.remote ? '状态与学期记录来自后端数据库；管理员可手动补齐缺失的当前学期。' : '本原型展示学期规则与操作结果，不运行定时任务。')}</section></div><section class="panel">${panelTitle('学期记录')}${table(['学年', '学期', '开始日期', '结束日期', '状态', '操作'], db.terms.map(t => [esc(t.year), esc(t.name), esc(t.start), esc(t.end), badge(statusText(t), t.current ? 'green' : 'gray'), button('查看课程', 'term-tasks', '', 'text', `data-id="${t.id}"`)]))}</section>`;
  }
  function labsPage() {
    const rows = db.labs.filter(l => matches(l.name, l.code, l.location, l.manager));
    return heading('LABORATORY RESOURCES', '实验室', '维护实验空间、负责人与设备数量，为教学安排提供参考。', admin() ? button('新增实验室', 'new-lab', 'plus', 'primary') : badge('实验室资料', 'gray')) +
      `<section class="panel">${admin() ? notice('实验室以编号唯一标识；已被课表引用的实验室不能删除。') : ''}${searchbar('搜索实验室、编号或负责人')}<div class="lab-grid">${rows.map((l, i) => `<article class="lab-card"><div class="lab-visual lab-visual-${i % 3}"><span class="room-label">${esc(l.location)}</span><div class="room-art" aria-hidden="true">${icon('building')}<span></span><i></i><i></i><i></i></div>${badge(l.equipment === '' ? '资料待补充' : '资料完整', l.equipment === '' ? 'amber' : 'green')}</div><div class="lab-body"><small>${esc(l.code)}</small><h2>${esc(l.name)}</h2><div class="lab-meta"><span>${icon('users')}负责人 <b>${esc(l.manager)}</b></span><span>${icon('grid')}设备数 <b>${l.equipment === '' ? '待补充' : `${l.equipment} 台`}</b></span></div><div class="lab-card-footer"><span>${tasks().filter(t => taskUsesLab(t, l.id)).length} 个本学期教学任务</span><div class="row-actions">${button('详情', 'lab-detail', '', 'text', `data-id="${l.id}"`)}${admin() ? `${button('编辑', 'edit-lab', '', 'text', `data-id="${l.id}"`)}${button('删除', 'delete-lab', '', 'text danger', `data-id="${l.id}"`)}` : ''}</div></div></div></article>`).join('') || empty()}</div></section>`;
  }
  function teachersPage() {
    const rows = db.teachers.filter(t => matches(t.name, t.code, t.college));
    return heading('FACULTY MANAGEMENT', '教师账号', '维护教师资料与登录账号，为教学任务提供准确的任课教师关联。', admin() ? button('新增教师', 'new-teacher', 'plus', 'primary') : badge('教师账号', 'blue')) +
      `<section class="panel">${searchbar('搜索教师姓名、工号或学院')}${admin() ? notice('新增教师与重置密码会生成一次性初始密码；临时账号使用 TMP 前缀。') : notice('教师可查看本人资料，账号由管理员统一维护。')}${table(['教师', '工号 / 登录账号', '所属学院', '账号类型', '本学期教学任务', '操作'], rows.map(t => [`<span class="person-cell"><span class="avatar">${esc((t.name || '教')[0])}</span><strong>${esc(t.name)}</strong></span>`, esc(t.code), esc(t.college), badge(t.temporary ? '临时账号' : '正式账号', t.temporary ? 'amber' : 'green'), tasks().filter(x => (x.teacher || '').split(/[、,，]/).includes(t.name)).length, admin() ? `<div class="row-actions">${button('编辑', 'edit-teacher', '', 'text', `data-id="${t.id}"`)}${button('重置密码', 'reset-password', '', 'text', `data-id="${t.id}"`)}${button('删除', 'delete-teacher', '', 'text danger', `data-id="${t.id}"`)}</div>` : badge('管理员维护', 'gray')]))}</section>`;
  }
  function importsPage() {
    const kind = importKinds.find(item => item.id === state.importKind) || importKinds[0];
    const result = state.importResult;
    const resultText = result
      ? result.rows != null
        ? `读取 ${result.rows} 行，入库 ${result.promoted || 0} 行，待核对 ${result.review_count || 0} 行，错误 ${result.errors || 0} 行`
        : `已导入 ${result.imported || 0} 条资料`
      : '';
    return heading('DATA IMPORT CENTER', '导入中心', '上传真实 Excel 资料，保留批次与逐行核对记录。') +
      `<section class="panel">${panelTitle('导入教学资料', badge(db.remote ? '真实入库' : '流程演示', db.remote ? 'green' : 'blue'))}<div class="tabs import-tabs">${importKinds.map(item => button(item.name, 'import-kind', '', state.importKind === item.id ? 'active' : '', `data-value="${item.id}"`)).join('')}</div><div class="import-steps"><span class="active"><b>1</b>选择资料</span><i></i><span><b>2</b>后端校验</span><i></i><span><b>3</b>核对导入结果</span></div><div class="upload-zone">${icon('upload')}<h3>选择${kind.name} Excel 文件</h3><p>${esc(kind.description)} 支持 .xlsx，最大 10 MB。</p><label class="btn primary file-label">选择文件<input type="file" accept=".xlsx" id="import-file" data-file="import" aria-label="选择 Excel 文件"></label><span id="selected-file" class="muted">尚未选择文件</span></div>${result ? notice(esc(resultText), result.errors || result.review_count ? 'warning' : '') : ''}<div class="between wrap"><span class="muted">重复文件校验 · 原始行保留 · 疑似重复逐行确认</span><div class="actions">${button('下载模板', 'import-template', 'download')}${button(db.remote ? '上传并导入' : '演示导入流程', 'upload-import', db.remote ? 'upload' : 'arrow', 'primary')}</div></div></section><section class="panel">${panelTitle('课表导入批次记录', `<span class="muted">共 ${db.batches.length} 批</span>`)}${table(['文件名称', '资料类型', '来源 / 已处理', '导入时间', '状态', '操作'], db.batches.map(b => [`<span class="file-name">${icon('layers')}${esc(b.name)}</span>`, b.kind, `${b.rows} / ${b.done}`, b.date, badge(b.errors ? `${b.errors} 行错误` : b.review ? `${b.review} 行待核对` : '处理完成', b.errors ? 'red' : b.review ? 'amber' : 'green'), button('查看批次', 'batch-detail', '', 'text', `data-id="${b.id}"`)]))}</section>`;
  }
  function reportRows() {
    if (state.report === 'labs') return labReports().map(l => [l.code, l.name, l.courses, l.count, l.hours, l.person]);
    if (db.remote && db.report && Array.isArray(db.report.projects)) return db.report.projects.map(p => [p.course_name, p.lab_names, p.project_name, p.hours, p.enrollment_count, '未采集']);
    return projects().map(p => { const t = taskById(p.task); return [t.name, lab(t.lab).name, p.name, p.hours, t.people, p.requirement === '选做' ? '待确认' : '未采集']; });
  }
  function reportsPage() {
    return heading('TEACHING ANALYTICS', '统计报表', '从教学数据中看见全貌，清晰呈现实验资源与教学投入。', button('导出当前报表', 'export-report', 'download', 'primary')) +
      stats([['开设课程', new Set(tasks().map(t => t.courseCode || t.code)).size, '门', 'book', termName()], ['实际排课学时', sum(labReports(), 'hours'), '学时', 'clock', '根据逐周课表汇总'], ['教学人时', sum(labReports(), 'person'), '人时', 'chart', '排课学时 × 课程选课人数'], ['实验项目', db.remote && db.report ? (db.report.projects || []).length : projects().length, '项', 'flask', `${sum(projects(), 'hours')} 个项目学时`]]) +
      `<section class="panel">${panelTitle('实验室使用分析', badge(termName(), 'gray'))}${chart()}</section><section class="panel"><div class="section-toolbar"><div class="tabs">${button('实验室使用统计', 'report-labs', '', state.report === 'labs' ? 'active' : '')}${button('实验项目清单', 'report-projects', '', state.report === 'projects' ? 'active' : '')}</div><span class="muted">${termName()}</span></div>${notice(state.report === 'labs' ? '教学人时 = 实际排课学时 × 对应课程选课人数；计划学时与实际排课学时分开统计。' : '课程选课人数仅表示任务规模。项目实际参与人数未采集时保留空缺，不以选课人数推断。')}${table(state.report === 'labs' ? ['实验室编号', '实验室名称', '课程数', '任务数', '实际排课学时', '教学人时'] : ['课程名称', '实验室', '实验项目', '项目学时', '课程选课人数', '项目实际人数'], reportRows().map(row => row.map(esc)))}<div class="table-footer"><span>静态原型导出 CSV，可使用 Excel 打开</span><span>共 ${reportRows().length} 条</span></div></section>`;
  }
  const fallbackSuggestions = ['统计本学期各实验室的教学人时数', '查看本学期开设的实验课程', '列出本学期所有实验项目'];
  const aiSuggestions = () => db.aiStatus && Array.isArray(db.aiStatus.examples) && db.aiStatus.examples.length
    ? db.aiStatus.examples
    : fallbackSuggestions;
  function aiPage() {
    const options = aiSuggestions();
    const backendReady = db.remote && admin() && db.aiStatus && db.aiStatus.configured === true;
    const queryDisabled = db.remote && !backendReady;
    const status = db.remote
      ? backendReady ? (db.aiStatus.model ? `模型：${db.aiStatus.model}` : '后端模型已配置') : (db.aiStatus && db.aiStatus.message) || '后端智能查询尚未配置'
      : '未登录状态仅展示本地示例，不执行 SQL';
    return heading('AI TEACHING ASSISTANT', '智能查询', '用日常语言描述你的问题，让教学数据更容易理解。', badge(backendReady ? '后端 AI 查询' : db.remote ? '后端未开放' : '示例回答', backendReady ? 'purple' : 'gray')) +
      `<div class="ai-layout"><section class="panel ai-panel"><div class="ai-welcome"><span class="ai-emblem">${icon('spark')}</span><div class="eyebrow">EXPLORE YOUR TEACHING DATA</div><h2>关于实验教学，你想了解什么？</h2><p>从课程安排到实验人时，用一句话开始探索。</p></div>${db.remote && !backendReady ? notice(esc(status), 'warning') : ''}<div class="suggestions">${options.map((s, i) => button(`${icon(i === 0 ? 'chart' : i === 1 ? 'book' : 'flask')}<span>${esc(s)}</span>${icon('arrow')}`, 'ai-suggestion', '', '', `data-id="${i}"`)).join('')}</div><form data-form="ai" class="ai-form"><label class="sr-only" for="ai-question">输入教学数据问题</label><textarea id="ai-question" name="question" placeholder="例如：统计本学期各实验室的教学人时数" rows="3" maxlength="1000" required>${esc(state.ai)}</textarea><div class="between"><span>${icon('shield')}${esc(status)}</span><button type="submit" class="btn primary" ${queryDisabled ? 'disabled' : ''}>开始查询 ${icon('arrow')}</button></div></form>${state.aiKind ? aiResult() : ''}</section><aside><section class="panel"><span class="feature-icon small">${icon('book')}</span><h2>提问小贴士</h2><p class="muted spaced">明确时间范围、查询对象和统计指标，可以得到更清晰的结果。</p><div class="prompt-tip"><span>01</span><div><strong>选择范围</strong><p>本学期 / 历史学期</p></div></div><div class="prompt-tip"><span>02</span><div><strong>描述对象</strong><p>实验室、课程、实验项目</p></div></div><div class="prompt-tip"><span>03</span><div><strong>指定指标</strong><p>教学人时、学时、项目清单</p></div></div></section><div class="note-box">${icon('info')}${esc(status)}</div></aside></div>`;
  }
  function aiResult() {
    if (state.aiKind === 'unsupported') return notice('此静态原型仅支持上方三个示例问题。请选择示例查看表格、SQL 展示与导出效果。', 'warning');
    const result = aiData();
    return `<section class="ai-result"><div class="between"><h2>${icon('spark')}查询结果</h2>${button('导出结果', 'export-ai', 'download')}</div><p class="muted">${termName()} · ${state.aiKind === 'remote' ? '后端查询' : '本地预设示例'} · 共 ${result.rows.length} 条</p>${table(result.headers, result.rows.map(row => row.map(esc)))}<details><summary>${state.aiKind === 'remote' ? '查看本次查询 SQL' : '查看示例 SQL（不执行）'}</summary><pre>${esc(result.sql)}</pre></details></section>`;
  }
  function aiData() {
    if (state.aiKind === 'remote' && db.aiResult) {
      const rows = db.aiResult.rows || [];
      return { headers: db.aiResult.columns || (rows[0] ? Object.keys(rows[0]) : []), rows: rows.map(row => (db.aiResult.columns || Object.keys(row)).map(key => row[key])), sql: db.aiResult.sql || '' };
    }
    if (state.aiKind === '0') return { headers: ['实验室', '排课学时', '教学人时'], rows: labReports().map(l => [l.name, l.hours, l.person]), sql: 'SELECT l.name, SUM(s.hours) AS scheduled_hours,\n       SUM(s.hours * t.enrollment_count) AS person_hours\nFROM laboratory l\nJOIN teaching_schedule s ON s.lab_id = l.id\nJOIN teaching_task t ON t.id = s.task_id\nWHERE t.term_id = :selected_term\nGROUP BY l.id, l.name;' };
    if (state.aiKind === '1') return { headers: ['课程号', '课程名称', '教师', '选课人数'], rows: tasks().map(t => [t.courseCode || t.code, t.name, t.teacher, t.people]), sql: 'SELECT c.course_code, c.course_name, t.enrollment_count\nFROM teaching_task t\nJOIN course c ON c.id = t.course_id\nWHERE t.term_id = :selected_term;' };
    return { headers: ['实验项目', '课程名称', '实验类型', '学时'], rows: projects().map(p => [p.name, taskById(p.task).name, p.type, p.hours]), sql: 'SELECT p.name, p.type_code, p.hours\nFROM experiment_project p\nJOIN teaching_task t ON t.id = p.task_id\nWHERE t.term_id = :selected_term;' };
  }
  function accountPage() {
    const teacher = db.currentTeacher || db.teachers[0] || {};
    return heading('ACCOUNT & SECURITY', '账号与安全', '管理个人信息与登录安全。') + `<div class="two-columns"><section class="panel profile"><span class="avatar huge">${admin() ? '管' : esc((teacher.name || '教')[0])}</span><h2>${admin() ? '系统管理员' : esc(teacher.name || '授课教师')}</h2><p>${admin() ? '教务管理中心' : esc(teacher.college || '')}</p>${badge(admin() ? '管理员' : '授课教师', 'blue')}<dl class="detail-grid"><div><dt>登录账号</dt><dd>${esc(localStorage.getItem('teachingUsername') || teacher.code || '')}</dd></div><div><dt>访问范围</dt><dd>${admin() ? '全部教学任务' : '本人授课任务'}</dd></div></dl>${button('退出并查看登录页', 'logout', 'logout')}</section><section class="panel">${panelTitle('修改登录密码', icon('shield'))}${notice(db.remote ? '密码将通过后端校验并更新。' : '仅演示密码表单校验。')}<form data-form="password" class="password-form"><label>原密码<input type="password" name="old" required autocomplete="current-password" placeholder="请输入当前密码"></label><label>新密码<input type="password" name="password" required minlength="8" autocomplete="new-password" placeholder="至少 8 位，包含字母和数字"></label><label>确认新密码<input type="password" name="confirm" required minlength="8" autocomplete="new-password" placeholder="再次输入新密码"></label><p id="password-error" class="form-error" role="alert"></p><button type="submit" class="btn primary">保存修改</button></form></section></div>`;
  }
  function loginPage() {
    return `<main class="login-screen"><section class="login-story"><a class="brand" href="#overview"><span class="brand-mark">${icon('flask')}</span><span>实验教学<span class="brand-sub">LAB TEACHING WORKSPACE</span></span></a><div><div class="eyebrow">A BETTER SPACE FOR TEACHING</div><h1>连接实验与教学，<br>让探索更有方向。</h1><p>课程管理 · 实验项目 · 资源统计 · 智能查询</p><div class="login-art" aria-hidden="true">${icon('flask')}<span class="orbit"></span><span class="orbit orbit-two"></span></div></div><span class="muted">实验教学项目管理系统</span></section><section class="login-form-wrap"><form data-form="login" class="login-form"><span class="eyebrow">WELCOME BACK</span><h2>登录教学工作台</h2><p>使用后端数据库中的账号登录。</p><label>登录身份<select name="role"><option value="admin">管理员端</option><option value="teacher">教师端</option></select></label><label>用户名<input name="username" placeholder="请输入账号" autocomplete="username" required maxlength="64"></label><label>密码<input name="password" type="password" placeholder="请输入密码" autocomplete="current-password" required></label><p class="form-error" id="login-error" role="alert"></p><button class="btn primary" type="submit">进入工作台 ${icon('arrow')}</button><div class="note-box">${icon('info')}账号和密码由后端数据库校验，登录令牌仅保存在当前浏览器。</div></form></section></main>`;
  }
  function render() {
    if (state.page === 'login') { app.innerHTML = loginPage(); document.title = '登录 · 实验教学项目管理系统'; return; }
    const route = routes.find(r => r[0] === state.page) || routes[0];
    const pages = { overview, tasks: tasksPage, projects: projectPage, terms: termsPage, labs: labsPage, teachers: teachersPage, imports: importsPage, reports: reportsPage, ai: aiPage, account: accountPage };
    document.title = `${route[1]} · 实验教学项目管理系统`;
    app.innerHTML = `<div class="app-shell ${state.mobile ? 'menu-open' : ''}"><aside class="sidebar"><a class="brand" href="#overview"><span class="brand-mark">${icon('flask')}</span><span>实验教学<span class="brand-sub">TEACHING WORKSPACE</span></span></a><nav aria-label="主导航">${routes.filter(r => !r[4] || admin()).map(r => `${r[3] ? `<div class="nav-group">${r[3]}</div>` : ''}<a href="#${r[0]}" class="nav-link ${state.page === r[0] ? 'selected' : ''}" ${state.page === r[0] ? 'aria-current="page"' : ''}>${icon(r[2])}<span>${r[1]}</span>${r[0] === 'ai' ? '<span class="ai-label">AI</span>' : r[0] === 'imports' && sum(db.batches, 'review') ? `<span class="nav-count">${sum(db.batches, 'review')}</span>` : ''}</a>`).join('')}</nav><div class="sidebar-footer"><div class="demo-status"><span class="status-dot"></span>${db.remote ? '已连接后端数据库' : '本地示例模式'}</div><p>${db.remote ? '数据来自 Spring Boot API' : '登录后连接真实数据'}</p><a href="#login">${icon('logout')}查看登录页</a></div></aside><button class="menu-scrim" data-action="toggle-menu" aria-label="关闭导航"></button><div class="workspace"><header class="topbar"><div class="breadcrumb">${button('', 'toggle-menu', 'menu', 'icon-btn mobile-menu', 'aria-label="展开导航"')}<span>${admin() ? '管理员端' : '教师端'}</span><b>/</b><strong>${route[1]}</strong></div><div class="topbar-actions"><label class="term-control">${icon('calendar')}<select aria-label="选择学期" data-change="term">${options(db.terms, state.term)}</select></label><span class="topbar-divider"></span><label class="role-control"><span class="avatar tiny">${admin() ? '管' : esc(teacherName()[0])}</span><select aria-label="切换演示角色" data-change="role"><option value="admin" ${admin() ? 'selected' : ''}>管理员端</option><option value="teacher" ${!admin() ? 'selected' : ''}>教师端 · ${esc(teacherName())}</option></select></label></div></header><main id="main" tabindex="-1">${pages[state.page]()}</main><footer class="page-footer"><span>实验教学项目管理系统</span><span>${db.remote ? '后端数据' : '本地示例'} <i>·</i> ${admin() ? '管理员视角' : '教师视角'}</span></footer></div></div>`;
  }
  function navigate(page) {
    if (location.hash === `#${page}`) routeChange();
    else location.hash = page;
  }
  async function routeChange() {
    let page = location.hash.slice(1) || 'overview';
    if (!routes.some(r => r[0] === page) && page !== 'login') page = 'overview';
    if (!db.remote && (!api || !api.isRemote())) {
      state.page = 'login';
      state.mobile = false;
      modal.close();
      if (location.hash !== '#login') {
        location.hash = 'login';
        return;
      }
      render();
      window.scrollTo(0, 0);
      return;
    }
    if (!admin() && routes.some(r => r[0] === page && r[4])) page = 'overview';
    if (state.page !== page) { state.query = ''; state.type = ''; }
    state.page = page;
    state.mobile = false;
    modal.close();
    render();
    window.scrollTo(0, 0);
    const activePage = state.page;
    if (db.remote && activePage === 'projects') {
      await ensureProjectsForCurrentTerm();
      if (state.page === activePage) render();
    }
    if (db.remote && activePage === 'tasks' && state.tab === 'week') {
      await ensureSchedulesForCurrentTerm();
      if (state.page === activePage) render();
    }
  }
  let toastTimer;
  function toast(message) {
    const target = document.getElementById('toast');
    target.textContent = message;
    target.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => target.classList.remove('show'), 3600);
  }
  async function refreshData() {
    if (!api || !api.isRemote()) return;
    state.loading = true;
    render();
    try {
      const remote = await api.loadAll(state.role, db.remote ? state.term : undefined);
      Object.keys(remote).forEach(key => { db[key] = remote[key]; });
      if (state.term === 'current' || !db.terms.some(t => String(t.id) === String(state.term))) state.term = db.currentTermId || db.terms[0]?.id;
      db.currentTeacher = state.role === 'teacher'
        ? (db.teachers.find(t => t.code === localStorage.getItem('teachingUsername')) || db.teachers[0])
        : null;
      toast('已连接后端数据库，数据已同步');
    } catch (error) {
      toast(error.message || '无法连接后端服务');
      if (!api.isRemote()) { state.page = 'login'; if (location.hash !== '#login') location.hash = 'login'; }
    } finally {
      state.loading = false;
      render();
    }
  }
  async function ensureProjectsForCurrentTerm() {
    if (!db.remote) return;
    const loaded = loadedProjectTasks();
    const pending = tasks().filter(task => !loaded.has(task.id) && Number(task.projectCount || 0) > 0);
    if (!pending.length) return;
    const results = await Promise.all(pending.map(async task => {
      try {
        const result = await api.loadProjects(task.id);
        return { task, result };
      } catch (_) {
        return null;
      }
    }));
    results.filter(Boolean).forEach(({ task, result }) => {
      db.projects = db.projects.filter(project => project.task !== task.id).concat(result.list);
      if (!loaded.has(task.id)) loaded.add(task.id);
    });
    db.loadedProjectTasks = Array.from(loaded);
  }
  async function ensureSchedulesForCurrentTerm() {
    if (!db.remote) return;
    const pending = tasks().filter(task => !Array.isArray(task.schedule)).slice(0, 100);
    if (!pending.length) return;
    state.loadingSchedules = true;
    render();
    try {
      const results = await Promise.all(pending.map(task => api.loadTask(task.id).catch(() => null)));
      results.forEach((row, index) => {
        if (!row) return;
        const task = api.mapTask(row, db.labs);
        api.mapSchedule(task, row.schedule || []);
        task.editable = row.editable === true;
        const target = taskById(task.id);
        if (target) Object.assign(target, task);
      });
    } finally {
      state.loadingSchedules = false;
      render();
    }
  }
  async function boot() {
    if (api && api.isRemote()) await refreshData();
    routeChange();
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
    openModal(id ? '编辑实验项目' : '新增实验项目', `<form data-form="project" data-id="${id || ''}"><div class="form-grid">${selectField('task', '所属教学任务', tasks().map(t => ({ id: t.id, name: `${t.name} · ${t.teacher}` })), p.task)}${field('code', '实验编号（实验室编号 + 三位后缀）', p.code || projectCodes(p.task)[0] || '', 'text', 'readonly aria-describedby="project-code-help"')}<p id="project-code-help" class="muted">编号由所属实验室自动分配，如 36-606-001；实验室无法确定时由后端生成唯一编号。</p>${field('name', '实验名称', p.name, 'text', 'required maxlength="50"')}${field('school', '学校代码', p.school, 'text', 'required maxlength="5"')}${selectField('category', '实验类别', ['基础', '专业基础', '专业', '其它'], p.category)}${selectField('type', '实验类型', ['演示性', '验证性', '综合性', '设计研究', '其它'], p.type)}${field('discipline', '实验所属学科代码', p.discipline, 'text', 'required maxlength="16"')}${selectField('requirement', '实验要求', ['必做', '选做', '其它'], p.requirement)}${selectField('participant', '实验者类别', ['博士', '硕士', '本科', '专科', '其他'], p.participant)}${field('group', '每组人数', p.group, 'number', 'min="1" max="99" required')}${field('hours', '实验学时', p.hours, 'number', 'min="0.01" max="9999" step="0.01" required')}</div>${formActions('保存项目')}</form>`, true);
  }
  function projectDetail(id) {
    const p = projects().find(x => x.id === Number(id));
    if (!p) return;
    openModal('实验项目详情', `<div class="detail-hero"><span class="feature-icon">${icon('flask')}</span><div><h3>${esc(p.name)}</h3><p>${esc(p.code)} · ${esc((taskById(p.task) || {}).name || '未知任务')}</p></div></div><dl class="detail-grid">${[['实验类别', p.category], ['实验类型', p.type], ['实验要求', p.requirement], ['所属学科代码', p.discipline], ['实验者类别', p.participant], ['每组人数', `${p.group} 人`], ['实验学时', `${p.hours} 学时`], ['学校代码', p.school], ['所属学期', termName()]].map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>${notice(admin() ? '管理员仅可查看此项目，项目由授课教师维护。' : current() ? '这是本人当前学期项目，可在项目列表中编辑。' : '历史归档项目，仅供查阅与复用。')}<div class="modal-actions">${button('关闭', 'close')}</div>`);
  }
  async function taskDetail(id) {
    let t = tasks().find(x => x.id === Number(id));
    if (!t) return;
    openModal('教学任务与排课详情', loadingBlock('正在读取教学任务'));
    if (db.remote) {
      try {
        const row = await api.loadTask(id);
        t = api.mapTask(row, db.labs);
        api.mapSchedule(t, row.schedule || []);
        t.editable = row.editable === true;
        const index = db.tasks.findIndex(task => task.id === t.id);
        if (index >= 0) db.tasks[index] = { ...db.tasks[index], ...t };
      } catch (error) {
        openModal('教学任务与排课详情', notice(esc(error.message || '教学任务读取失败'), 'warning'));
        return;
      }
    }
    const schedule = t.schedule && t.schedule.length
      ? t.schedule
      : Array.from({ length: db.remote ? 0 : Math.min(t.weeks || 0, 16) }, (_, index) => ({
        teaching_week: index + 1,
        weekday: t.day,
        period_start: t.period * 2 - 1,
        period_end: t.period * 2,
        hours: 2,
        lab_name: lab(t.lab).name
      }));
    openModal('教学任务与排课详情', `<div class="detail-hero"><span class="feature-icon">${icon('book')}</span><div><h3>${esc(t.name)}</h3><p>${esc(t.code)} · ${esc(t.termName || termName())}</p></div></div><dl class="detail-grid">${[['任课教师', t.teacher || '待确认'], ['授课班级', t.className], ['选课人数', `${t.people} 人`], ['计划学时', t.hours], ['排课学时', t.scheduledHours], ['实验室', t.labNames || lab(t.lab).name]].map(([k, v]) => `<div><dt>${k}</dt><dd>${esc(v)}</dd></div>`).join('')}</dl>${schedule.length ? table(['教学周', '星期', '节次', '学时', '上课地点'], schedule.map(slot => [`第 ${slot.teaching_week} 周`, `星期${'一二三四五六日'[Number(slot.weekday) - 1] || slot.weekday}`, `${slot.period_start}–${slot.period_end} 节`, `${slot.hours || 2}`, esc(slot.lab_name || lab(slot.lab_id).name)])) : notice('此任务尚无排课明细，可通过课表导入补充。')}<div class="modal-actions">${button('关闭', 'close')}${button('进入实验项目', 'task-projects', '', 'primary', `data-id="${t.id}"`)}</div>`, true);
  }
  function taskForm() {
    if (!admin()) return;
    if (!db.remote) return toast('请先登录后维护真实教学任务');
    const currentTerms = db.terms.filter(term => term.status === 'OPEN' || term.status === 'CURRENT');
    if (!currentTerms.length) return toast('当前没有开放学期，请先在学年学期页补齐');
    if (!db.courses.length) return toast('暂无课程资料，请先导入课表');
    if (!db.teachers.length) return toast('暂无教师账号，请先导入教师资料');
    openModal('创建当前教学任务', `${notice('仅可为当前开放学期创建任务；实际排课与实验室需通过课表导入登记。')}<form data-form="create-task"><div class="form-grid">${selectField('termId', '当前学期', currentTerms, db.currentTermId)}${selectField('courseId', '课程', db.courses.map(course => ({ id: course.id, name: `${course.code} · ${course.name}` })), db.courses[0].id)}<label class="span-two">任课教师（支持合授，按住 Ctrl 多选）<select name="teacherIds" multiple size="6" required>${db.teachers.map(teacher => `<option value="${teacher.id}">${esc(teacher.name)} · ${esc(teacher.code)}</option>`).join('')}</select></label>${field('classComposition', '授课班级', '', 'text', 'required maxlength="4000"')}${field('majorComposition', '专业构成', '', 'text', 'maxlength="4000"')}${field('enrollmentCount', '选课人数', 0, 'number', 'min="0" max="100000" required')}${field('plannedLabHours', '计划实验学时', 2, 'number', 'min="0.01" max="9999" step="0.01" required')}</div>${formActions('创建任务')}</form>`, true);
  }
  function teacherForm(id) {
    if (!admin()) return;
    if (!db.remote) return toast('请先登录后维护教师账号');
    const teacher = db.teachers.find(item => item.id === Number(id)) || { name: '', code: '', college: '' };
    openModal(id ? '编辑教师账号' : '新增教师账号', `<form data-form="teacher" data-id="${id || ''}"><div class="form-grid">${field('code', '登录工号', teacher.code, 'text', 'required maxlength="64"')}${field('name', '教师姓名', teacher.name, 'text', 'required maxlength="100"')}${field('college', '所属学院', teacher.college, 'text', 'maxlength="100"')}</div>${formActions(id ? '保存教师资料' : '创建教师账号')}</form>`);
  }
  function labForm(id) {
    if (!admin()) return;
    if (!db.remote) return toast('请先登录后维护实验室资料');
    const item = db.labs.find(row => row.id === Number(id)) || { code: '', name: '', location: '', managerId: null, equipment: '' };
    openModal(id ? '编辑实验室' : '新增实验室', `<form data-form="lab" data-id="${id || ''}"><div class="form-grid">${field('code', '实验室编号', item.code, 'text', 'required maxlength="32"')}${field('name', '实验室名称', item.name, 'text', 'required maxlength="100"')}${field('location', '实验室位置', item.location === '待安排' ? '' : item.location, 'text', 'maxlength="200"')}${field('equipment', '设备数量（未知留空）', item.equipment, 'number', 'min="0" max="1000000"')}<label class="span-two">负责人教师<select name="managerId"><option value="">待确认</option>${options(db.teachers.map(teacher => ({ id: teacher.id, name: `${teacher.name} · ${teacher.code}` })), item.managerId || '')}</select></label></div>${formActions('保存实验室')}</form>`);
  }
  function confirmDeleteTeacher(id) {
    const teacher = db.teachers.find(item => item.id === Number(id));
    if (!teacher) return;
    openModal('删除教师账号', `<p>确认删除“${esc(teacher.name)}（${esc(teacher.code)}）”？已被任务、实验室或项目引用的账号不能删除。</p><div class="modal-actions">${button('取消', 'close')}${button('确认删除', 'commit-delete-teacher', '', 'danger solid', `data-id="${id}"`)}</div>`);
  }
  function confirmDeleteLab(id) {
    const item = db.labs.find(row => row.id === Number(id));
    if (!item) return;
    openModal('删除实验室', `<p>确认删除“${esc(item.name)}（${esc(item.code)}）”？已有排课的实验室不能删除。</p><div class="modal-actions">${button('取消', 'close')}${button('确认删除', 'commit-delete-lab', '', 'danger solid', `data-id="${id}"`)}</div>`);
  }
  async function generateTerm() {
    if (!admin()) return;
    if (!db.remote) return toast('请先登录后补齐真实学期');
    try {
      await api.json('/teaching/terms/generate', 'POST');
      await refreshData();
      toast('当前学期已补齐');
    } catch (error) {
      toast(error.message || '学期补齐失败');
    }
  }
  function labDetail(id) {
    const l = db.labs.find(x => x.id === Number(id));
    openModal('实验室详情', `<div class="detail-hero"><span class="feature-icon">${icon('building')}</span><div><h3>${esc(l.name)}</h3><p>${esc(l.code)} · ${esc(l.location)}</p></div></div><dl class="detail-grid"><div><dt>负责人</dt><dd>${esc(l.manager)}</dd></div><div><dt>设备数量</dt><dd>${l.equipment === '' ? '待补充' : `${l.equipment} 台`}</dd></div><div><dt>本学期教学任务</dt><dd>${tasks().filter(t => taskUsesLab(t, l.id)).length} 个</dd></div></dl><div class="modal-actions">${button('关闭', 'close')}</div>`);
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
    if (db.remote) {
      api.json(`/teaching/teachers/${id}/reset-password`, 'POST').then(result => {
        openModal('教师密码重置成功', `${account}${notice('请将以下临时密码安全交给教师，登录后立即修改。')}<div class="credential">${esc(result.password || '')}</div><div class="modal-actions">${button('完成', 'close', '', 'primary')}</div>`);
      }).catch(error => toast(error.message || '密码重置失败'));
      return;
    }
    openModal('教师密码重置成功（演示）', `${account}${notice('演示已完成。正式系统在此一次性展示新的临时密码，教师登录后应立即修改。')}<div class="credential">［临时密码展示区］</div><div class="modal-actions">${button('完成', 'close', '', 'primary')}</div>`);
  }
  function copyProjects() {
    if (!canManageProjects() || !tasks().length) return toast('仅教师可向本人当前任务复制项目');
    const target = Number(state.task || tasks()[0].id);
    const targetTask = taskById(target);
    const sources = db.tasks.filter(task => {
      const term = termById(task.term);
      const count = task.projectCount || db.projects.filter(project => project.task === task.id).length;
      const sameCourse = db.remote
        ? task.courseId === targetTask.courseId
        : task.code === targetTask.code;
      return task.id !== target
        && targetTask
        && sameCourse
        && count > 0
        && (db.remote ? term && !term.current : task.term === 'history');
    });
    openModal('从历史任务复制实验项目', `${notice('仅支持同课程复制。历史原项目保留，当前学期创建独立版本。')}<form data-form="copy"><div class="form-grid">${selectField('target', '当前学期目标任务', tasks().map(t => ({ id: t.id, name: t.name })), target)}${sources.length ? selectField('source', '历史来源任务', sources.map(t => ({ id: t.id, name: `${t.name} · ${termById(t.term)?.name || t.termName}` })), sources[0].id) : '<label>历史来源任务<select disabled><option>暂无可复制项目</option></select></label>'}</div>${formActions('复制项目')}</form>`);
  }
  function projectImport() {
    if (!canManageProjects() || !tasks().length) return toast('仅教师可向本人当前任务导入项目');
    openModal('导入实验项目', `${notice(db.remote ? '项目文件将由后端按模板校验并写入数据库；遇到冲突会整体拒绝，不会部分导入。' : '演示选择目标课程和文件的流程；不会读取文件内容。')}<form data-form="project-import">${selectField('task', '目标教学任务', tasks().map(t => ({ id: t.id, name: t.name })), state.task || tasks()[0].id)}<div class="file-pick"><label>选择 Excel 文件${db.remote ? '' : '（演示可选）'}<input type="file" accept=".xlsx" data-file="project" ${db.remote ? 'required' : ''}></label></div>${formActions(db.remote ? '上传并导入' : '添加示例项目')}</form>`);
  }
  function batchDetail(id) {
    const b = db.batches.find(x => x.id === Number(id));
    if (!b) return;
    openModal('导入批次与逐行核对', `<p class="file-name">${icon('layers')}${esc(b.name)}</p>${notice('以下是固定的原始行示例。核对操作只更新此原型批次状态，不创建真实教学数据。')}<div class="batch-stats"><span>来源行数 <b>${b.rows}</b></span><span>已处理 <b>${b.done}</b></span><span>待核对 <b>${b.review}</b></span></div>${table(['原始行号', '示例课程 / 数据', '数据疑点', '状态', '操作'], Array.from({ length: b.review || 1 }, (_, i) => [b.done + i + 1, b.kind === '课程课表' ? '数据库原理与应用 · 软件工程 2401' : esc(b.kind), b.review ? '课程与教师相同，请核实是否为另一独立开课' : '无数据疑点', badge(b.review ? '待核对' : '处理完成', b.review ? 'amber' : 'green'), b.review ? button('确认独立开课', 'confirm-row', '', 'text', `data-id="${b.id}"`) : '已完成']))}<details><summary>展开原始行示例</summary><pre>${esc(JSON.stringify({ 工作表: '教学资料', 课程: '数据库原理与应用', 教师: '陈老师', 班级: '软件工程2401', 备注: '固定演示数据，非所选文件内容' }, null, 2))}</pre></details><div class="modal-actions">${button('关闭', 'close')}</div>`, true);
  }
  function demoImport() {
    const kind = importKinds.find(item => item.id === state.importKind) || importKinds[0];
    openModal('导入预览', `${notice('下表为固定示例，不是文件解析结果。演示校验与导入结果，不新增真实业务资料。')}<div class="import-steps"><span><b>1</b>选择资料</span><i></i><span class="active"><b>2</b>预览与校验</span><i></i><span><b>3</b>核对结果</span></div>${table(['行号', '资料类型', '校验结果'], [['1', kind.name, badge('校验通过', 'green')], ['2', kind.name, badge('校验通过', 'green')], ['3', kind.name, badge('疑似重复，待核对', 'amber')]])}<div class="modal-actions">${button('取消', 'close')}${button('确认演示导入', 'commit-import', '', 'primary')}</div>`);
  }
  function confirmDelete(id) {
    if (!canEditProject(id)) return;
    const row = db.projects.find(p => p.id === Number(id));
    openModal('删除实验项目', `<p>确认删除“${esc(row.name)}”？${db.remote ? '删除后无法恢复，已被跨学期复制引用的原项目不能删除。' : '仅移除当前页面内的演示数据，刷新后恢复。'}</p><div class="modal-actions">${button('取消', 'close')}${button('确认删除', 'commit-delete', '', 'danger solid', `data-kind="project" data-id="${id}"`)}</div>`);
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
    if (['new-task', 'generate-term', 'new-lab', 'edit-lab', 'delete-lab', 'new-teacher', 'edit-teacher', 'delete-teacher', 'upload-import', 'commit-import', 'confirm-row', 'batch-detail', 'import-template', 'export-report', 'report-labs', 'report-projects'].includes(action) && !admin()) return toast('此功能仅对管理员开放');
    const localRender = fn => { fn(); render(); };
    const handlers = {
      close: () => modal.close(),
      'toggle-menu': () => localRender(() => { state.mobile = !state.mobile; }),
      'reset-filter': () => localRender(() => { state.query = ''; state.type = ''; state.task = ''; }),
      'new-project': () => projectForm(), 'edit-project': () => projectForm(id), 'project-detail': () => projectDetail(id),
      'task-detail': () => taskDetail(id),
      'task-projects': () => { state.task = id; modal.close(); navigate('projects'); },
      'task-tab-list': () => localRender(() => { state.tab = 'list'; }),
      'task-tab-week': async () => { state.tab = 'week'; render(); await ensureSchedulesForCurrentTerm(); },
      'open-week': async () => { state.tab = 'week'; await navigate('tasks'); },
      'prev-week': () => localRender(() => { state.week = Math.max(1, state.week - 1); }),
      'next-week': () => localRender(() => { state.week = Math.min(20, state.week + 1); }),
      'term-tasks': async () => { state.term = id; state.task = ''; if (db.remote) await refreshData(); navigate('tasks'); },
      'new-task': taskForm,
      'generate-term': generateTerm,
      'new-lab': () => labForm(),
      'edit-lab': () => labForm(id),
      'delete-lab': () => confirmDeleteLab(id),
      'commit-delete-lab': async () => { try { await api.json(`/teaching/labs/${id}`, 'DELETE'); modal.close(); await refreshData(); toast('实验室已删除'); } catch (error) { toast(error.message || '实验室删除失败'); } },
      'new-teacher': () => teacherForm(),
      'edit-teacher': () => teacherForm(id),
      'delete-teacher': () => confirmDeleteTeacher(id),
      'commit-delete-teacher': async () => { try { await api.json(`/teaching/teachers/${id}`, 'DELETE'); modal.close(); await refreshData(); toast('教师账号已删除'); } catch (error) { toast(error.message || '教师账号删除失败'); } },
      'lab-detail': () => labDetail(id),
      'reset-password': () => resetTeacherPassword(id),
      'commit-password': () => resetTeacherPassword(id, true),
      'delete-project': () => confirmDelete(id),
      'commit-delete': async () => { if (target.dataset.kind !== 'project' || !canEditProject(id)) return toast('无权删除此记录'); try { if (db.remote) await api.json(`/teaching/projects/${id}`, 'DELETE'); else db.projects = db.projects.filter(p => p.id !== Number(id)); modal.close(); if (db.remote) await refreshData(); else render(); toast('实验项目已删除'); } catch (error) { toast(error.message || '删除失败'); } },
      'copy-projects': copyProjects, 'project-import': projectImport,
      'project-template': async () => { if (db.remote) { try { await api.download('/teaching/templates/projects'); } catch (error) { toast(error.message || '模板下载失败'); } return; } downloadCsv('实验项目字段示例', ['实验名称', '实验编号', '学校代码', '实验类别', '实验类型', '实验所属学科', '实验要求', '实验者类别', '每组人数', '实验学时'], [['SQL 基础实验', '36-606-001', 'DEMO', '专业基础', '验证性', '0809', '必做', '本科', 1, 4]]); },

      'import-kind': () => localRender(() => { state.importKind = target.dataset.value; state.importResult = null; }),
      'import-template': async () => { const kind = importKinds.find(item => item.id === state.importKind) || importKinds[0]; if (db.remote) { try { await api.download(`/teaching/templates/${kind.id}`); } catch (error) { toast(error.message || '模板下载失败'); } return; } const templates = { timetable: ['学年', '学期', '课程号', '课程名', '实验总学时', '教师名称', '教学地点', '教学班组成', '上课时间', '选课人数'], teachers: ['工号', '教师姓名', '学院'], labs: ['实验室编号', '实验室名称', '实验室位置', '负责人教师工号', '设备数'] }; downloadCsv(`${kind.name}字段示例`, templates[kind.id], []); },
      'upload-import': async () => {
        if (!db.remote) return demoImport();
        const file = document.getElementById('import-file')?.files?.[0];
        if (!file) return toast('请先选择 .xlsx 文件');
        const kind = importKinds.find(item => item.id === state.importKind) || importKinds[0];
        try {
          state.importResult = await api.upload(`/teaching/imports/${kind.id}`, file);
          await refreshData();
          toast(kind.id === 'timetable'
            ? `课表已导入：读取 ${state.importResult.rows || 0} 行，入库 ${state.importResult.promoted || 0} 行`
            : `${kind.name}已导入 ${state.importResult.imported || 0} 条`);
        } catch (error) { toast(error.message || `${kind.name}导入失败`); }
      },
      'commit-import': () => { const b = { id: Date.now(), name: `${state.importKind}流程演示.xlsx`, kind: state.importKind, date: '2026-09-11 10:00', rows: 3, done: 2, review: 1 }; db.batches.unshift(b); render(); batchDetail(b.id); toast('示例批次已生成，请核对待确认行'); },
      'batch-detail': async () => { if (db.remote) { openModal('导入批次详情', loadingBlock('正在读取批次记录')); try { const detail = await api.request(`/teaching/imports/${id}`); const rows = detail.rows || []; const issueText = issues => Array.isArray(issues) ? issues.map(item => typeof item === 'string' ? item : item.message || item.msg || JSON.stringify(item)).join('；') : String(issues || ''); openModal('导入批次详情', `${table(['工作表', '行号', '状态', '问题', '操作'], rows.map(r => [esc(r.sheet_name), r.source_row, badge(r.status, r.status === 'PROMOTED' ? 'green' : r.status === 'ERROR' ? 'red' : 'amber'), esc(issueText(r.issues)), r.status === 'REVIEW' ? button('确认独立任务', 'confirm-row', 'check', 'text', `data-batch="${id}" data-source-row="${r.source_row}" data-sheet="${esc(r.sheet_name)}"`) : '']))}<div class="modal-actions">${button('关闭', 'close')}</div>`, true); } catch (error) { openModal('导入批次详情', notice(esc(error.message || '批次详情获取失败'), 'warning')); } } else batchDetail(id); },
      'confirm-row': async () => { if (db.remote) { try { await api.json(`/teaching/imports/${target.dataset.batch}/rows/${target.dataset.sourceRow}/confirm?sheet=${encodeURIComponent(target.dataset.sheet || '')}`, 'POST', { confirmDistinctTask: true }); modal.close(); await refreshData(); toast('原始行已确认并重新处理'); } catch (error) { toast(error.message || '原始行确认失败'); } return; } const b = db.batches.find(x => x.id === Number(id)); if (b.review) { b.review--; b.done++; } render(); batchDetail(id); toast('已完成本行的核对演示'); },
      'report-labs': () => localRender(() => { state.report = 'labs'; }),
      'report-projects': () => localRender(() => { state.report = 'projects'; }),
      'export-report': async () => { if (db.remote) { try { await api.download(`/teaching/reports/export?type=${state.report}&termId=${encodeURIComponent(state.term)}`); } catch (error) { toast(error.message || '报表导出失败'); } } else downloadCsv(state.report === 'labs' ? '实验室教学统计' : '实验项目清单', state.report === 'labs' ? ['实验室编号', '实验室名称', '课程数', '任务数', '实际排课学时', '教学人时'] : ['课程名称', '实验室', '实验项目', '项目学时', '课程选课人数', '项目实际人数'], reportRows()); },
      'ai-suggestion': () => localRender(() => { state.ai = aiSuggestions()[Number(id)] || ''; state.aiKind = db.remote ? '' : id; db.aiResult = null; }),
      'export-ai': () => { const result = aiData(); downloadCsv('智能查询示例结果', result.headers, result.rows); },
      logout: async () => { if (api && api.isRemote()) await api.logout(); state.role = 'admin'; navigate('login'); }
    };
    if (handlers[action]) handlers[action]();
  });
  document.addEventListener('change', async event => {
    const el = event.target;
    if (el.name === 'task' && el.form && el.form.dataset.form === 'project') {
      const existing = db.projects.find(p => p.id === Number(el.form.dataset.id));
      const task = taskById(el.value);
      const keepCode = existing && task && taskById(existing.task).lab === task.lab;
      el.form.elements.code.value = keepCode ? existing.code : projectCodes(el.value)[0] || '';
    }
    if (el.dataset.change) {
      state[el.dataset.change] = el.value;
      if (['term', 'role'].includes(el.dataset.change)) { state.task = ''; state.query = ''; state.type = ''; state.ai = ''; state.aiKind = ''; state.importResult = null; if (el.dataset.change === 'role' && db.remote) { await api.logout(); db.remote = false; state.page = 'login'; location.hash = 'login'; return; } if (el.dataset.change === 'term' && db.remote) await refreshData(); routeChange(); }
      else render();
    }
    if (el.dataset.file && el.files[0]) {
      const file = el.files[0];
      if (!/\.xlsx$/i.test(file.name) || file.size > 10 * 1024 * 1024) { el.value = ''; toast('请选择小于 10 MB 的 .xlsx 文件'); return; }
      if (el.dataset.file === 'import') document.getElementById('selected-file').textContent = `已选择：${file.name}`;
      toast(db.remote ? '文件已选择，可上传并导入' : '已选择文件，可继续体验示例流程');
    }
  });
  document.addEventListener('submit', async event => {
    const form = event.target;
    if (!form.dataset.form) return;
    event.preventDefault();
    const formData = new FormData(form);
    const values = Object.fromEntries(formData);
    Object.keys(values).forEach(k => { if (typeof values[k] === 'string') values[k] = values[k].trim(); });
    const kind = form.dataset.form;
    const fail = message => { const node = form.querySelector('.form-error'); if (node) node.textContent = message; else toast(message); };
    if (kind === 'search') { state.query = values.query; render(); return; }
    if (kind === 'login') {
      if (!api) return fail('API 客户端未加载');
      try { await api.login(values.username, values.password, values.role); state.role = values.role; state.task = ''; await refreshData(); navigate('overview'); }
      catch (error) { fail(error.message || '登录失败'); }
      return;
    }
    if (kind === 'ai') {
      state.ai = values.question;
      if (db.remote && admin()) {
        try { const result = await api.json('/teaching/ai/query', 'POST', { question: values.question }); state.aiKind = 'remote'; db.aiResult = result; render(); }
        catch (error) { fail(error.message || '智能查询失败'); }
      } else { const index = aiSuggestions().indexOf(values.question); state.aiKind = index < 0 ? 'unsupported' : String(index); render(); }
      return;
    }
    if (kind === 'password') {
      if (!/[a-zA-Z]/.test(values.password) || !/[0-9]/.test(values.password) || values.password.length < 8) return fail('新密码至少 8 位，并包含字母和数字。');
      if (values.password !== values.confirm) return fail('两次输入的新密码不一致。');
      if (values.password === values.old) return fail('新密码不能与原密码相同。');
      if (db.remote) {
        try {
          await api.json('/teaching/account/password', 'POST', { oldPassword: values.old, newPassword: values.password });
          form.reset();
          await api.logout();
          db.remote = false;
          state.message = '密码已更新，请重新登录';
          navigate('login');
        }
        catch (error) { fail(error.message || '密码修改失败'); }
      } else { form.reset(); form.querySelector('.form-error').textContent = ''; toast('密码校验通过'); }
      return;
    }
    if (kind === 'create-task') {
      if (!admin()) return fail('仅管理员可创建教学任务。');
      const teacherIds = formData.getAll('teacherIds').map(Number).filter(Boolean);
      if (!teacherIds.length) return fail('请至少选择一位授课教师。');
      try {
        await api.json('/teaching/tasks', 'POST', {
          termId: Number(values.termId),
          courseId: Number(values.courseId),
          teacherIds,
          classComposition: values.classComposition,
          majorComposition: values.majorComposition || '',
          enrollmentCount: Number(values.enrollmentCount),
          plannedLabHours: Number(values.plannedLabHours)
        });
        modal.close();
        await refreshData();
        toast('教学任务已创建');
      } catch (error) { fail(error.message || '教学任务创建失败'); }
      return;
    }
    if (kind === 'teacher') {
      if (!admin()) return fail('仅管理员可维护教师账号。');
      const body = { gonghao: values.code, jiaoshixingming: values.name, xueyuan: values.college || '' };
      try {
        const result = await api.json(form.dataset.id ? `/teaching/teachers/${form.dataset.id}` : '/teaching/teachers', form.dataset.id ? 'PUT' : 'POST', body);
        modal.close();
        await refreshData();
        if (result && result.password) openModal('教师账号已创建', `${notice('初始密码只显示一次，请安全交给教师本人。')}<div class="credential">${esc(result.password)}</div><div class="modal-actions">${button('完成', 'close', '', 'primary')}</div>`);
        else toast('教师资料已保存');
      } catch (error) { fail(error.message || '教师资料保存失败'); }
      return;
    }
    if (kind === 'lab') {
      if (!admin()) return fail('仅管理员可维护实验室。');
      const body = {
        shiyanshibianhao: values.code,
        shiyanshimingcheng: values.name,
        shiyanshiweizhi: values.location || '',
        manager_teacher_id: values.managerId ? Number(values.managerId) : null,
        equipment_count: values.equipment === '' ? null : Number(values.equipment)
      };
      try {
        await api.json(form.dataset.id ? `/teaching/labs/${form.dataset.id}` : '/teaching/labs', form.dataset.id ? 'PUT' : 'POST', body);
        modal.close();
        await refreshData();
        toast('实验室资料已保存');
      } catch (error) { fail(error.message || '实验室保存失败'); }
      return;
    }
    if (['project', 'copy', 'project-import'].includes(kind) && !canManageProjects()) return fail('仅教师可维护本人当前学期实验项目。');
    if (kind === 'project') {
      if (!values.name || !values.school || !values.discipline) return fail('请完整填写实验名称、学校代码和学科代码。');
      if (!tasks().some(t => t.id === Number(values.task))) return fail('请选择有效教学任务。');
      if (form.dataset.id && !canEditProject(form.dataset.id)) return fail('无权修改其他教师或历史学期的项目。');
      if (db.remote) {
        const code = { category: { '基础': '1', '专业基础': '2', '专业': '3', '其它': '4' }, type: { '演示性': '1', '验证性': '2', '综合性': '3', '设计研究': '4', '其它': '5' }, requirement: { '必做': '1', '选做': '2', '其它': '3' }, participant: { '博士': '1', '硕士': '2', '本科': '3', '专科': '4', '其他': '5' } };
        const body = { task_id: Number(values.task), project_code: values.code || '', school_code: values.school, name: values.name, category_code: code.category[values.category] || '2', type_code: code.type[values.type] || '2', discipline_code: values.discipline, requirement_code: code.requirement[values.requirement] || '1', participant_type_code: code.participant[values.participant] || '3', group_size: Number(values.group), hours: Number(values.hours), sort_order: 0 };
        try { await api.json(form.dataset.id ? `/teaching/projects/${form.dataset.id}` : '/teaching/projects', form.dataset.id ? 'PUT' : 'POST', body); modal.close(); await refreshData(); state.task = String(body.task_id); render(); toast('实验项目已保存'); }
        catch (error) { fail(error.message || '项目保存失败'); }
        return;
      }
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
      const sourceTerm = db.terms.find(t => String(t.id) === String(source && source.term));
      if (!source || !target || (db.remote ? !sourceTerm || sourceTerm.current : source.term !== 'history') || (!db.remote && source.teacher !== teacherName()) || (db.remote ? source.courseId !== target.courseId : source.code !== target.code)) return fail('来源与目标必须属于同一课程，请重新选择。');
      if (db.remote) {
        try { await api.json('/teaching/projects/copy', 'POST', { sourceTaskId: Number(values.source), targetTaskId: Number(values.target) }); modal.close(); await refreshData(); state.task = String(values.target); render(); toast('历史项目已复制'); }
        catch (error) { fail(error.message || '项目复制失败'); }
        return;
      }
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
      if (db.remote) {
        const file = form.querySelector('input[type="file"]')?.files?.[0];
        if (!file) return fail('请选择 .xlsx 文件');
        try { const result = await api.upload('/teaching/imports/projects', file, { taskId: Number(values.task) }); modal.close(); await refreshData(); state.task = values.task; render(); toast(`项目已导入 ${result.imported || 0} 条`); }
        catch (error) { fail(error.message || '项目导入失败'); }
        return;
      }
      const code = projectCodes(values.task)[0];
      if (!code) return fail('该实验室三位编号已用完，无法导入项目。');
      const id = Date.now();
      db.projects.push({ id, task: Number(values.task), code, name: 'Excel 导入流程示例项目', type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: '0809', school: 'DEMO', group: 2, hours: 4 });
      state.task = values.task;
    }
    modal.close();
    if (['project', 'copy', 'project-import'].includes(kind) && state.page !== 'projects') navigate('projects'); else render();
    if (!db.remote) toast('演示数据已保存，刷新页面可恢复初始状态');
  });
  modal.addEventListener('click', event => { if (event.target === modal) { const r = modal.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) modal.close(); } });
  window.addEventListener('hashchange', routeChange);
  boot();
})();
