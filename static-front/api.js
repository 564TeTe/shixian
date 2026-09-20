/* 本地原型数据与模拟请求层：无需后端即可演示全部页面和操作。 */
'use strict';
window.TeachingApi = (() => {
  const source = window.PrototypeData;
  let token = sessionStorage.getItem('teaching.prototype.token') || '';
  const clear = () => { token = ''; sessionStorage.removeItem('teaching.prototype.token'); };
  const copy = value => JSON.parse(JSON.stringify(value));
  const workspace = () => ({
    account: token === 'admin' ? { id: 0, username: 'admin', display_name: '系统管理员', role: 'ADMIN', college: '教务管理中心' } : { id: 1, username: 'T2026001', display_name: '陈老师', role: 'TEACHER', college: '计算机与信息工程学院' },
    terms: copy(source.terms).map(t => ({ ...t, id: t.current ? 1 : 2, academic_year_name: t.year, starts_on: t.start, ends_on: t.end, status: t.current ? 'OPEN' : 'ARCHIVED' })), currentTerm: { ...copy(source.terms.find(t => t.current)), id: 1 },
    teachers: copy(source.teachers).map(t => ({ ...t, jiaoshixingming: t.name, gonghao: t.code, xueyuan: t.college, is_temporary: t.temporary })),
    labs: copy(source.labs).map(l => ({ ...l, shiyanshibianhao: l.code, shiyanshimingcheng: l.name, shiyanshiweizhi: l.location, manager_name: l.manager, equipment_count: l.equipment })),
    tasks: copy(source.tasks).map(t => ({ ...t, term_id: t.term === 'current' ? 1 : 2, course_name_snapshot: t.name, course_code: t.code, task_code: t.code, teacher_names: t.teacher, class_composition: t.className, enrollment_count: t.people, planned_lab_hours: t.hours })),
    projects: copy(source.projects).map(p => ({ ...p, task_id: p.task, project_code: p.code, school_code: p.school, discipline_code: p.discipline, group_size: p.group, category_code: ['基础','专业基础','专业','其它'].indexOf(p.category)+1, type_code: ['演示性','验证性','综合性','设计研究','其它'].indexOf(p.type)+1, requirement_code: ['必做','选做','其它'].indexOf(p.requirement)+1, participant_type_code: ['博士','硕士','本科','专科','其他'].indexOf(p.participant)+1 })),
    schedule: copy(source.tasks).map(t => ({ id: t.id, task_id: t.id, lab_id: t.lab, teaching_week: 1, weekday: t.day, period_start: t.period, period_end: t.period + 1, hours: 2 })), assignments: copy(source.tasks).map(t => ({ task_id: t.id, teacher_account_id: t.teacher === '陈老师' ? 1 : 2 }))
  });
  async function request(path, options = {}) {
    if (path === '/workspace') return workspace();
    if (path === '/ai/status') return { configured: true, message: '原型模式：使用本地模拟数据' };
    if (path === '/imports') return { list: copy(source.batches).map(b => ({ id: b.id, file_name: b.name, created_at: b.date, row_count: b.rows, promoted: b.done, warnings: b.review, errors: 0 })) };
    if (path.startsWith('/imports/')) return { batch: copy(source.batches[0]), rows: [] };
    if (path.includes('/projects') && options.method === 'DELETE') { source.projects = source.projects.filter(p => p.id !== Number(path.split('/').pop())); return {}; }
    if (path.includes('/projects') && ['POST','PUT'].includes(options.method)) { const body = options.body ? JSON.parse(options.body) : {}; const id = Number(path.split('/').pop()); if (options.method === 'PUT') Object.assign(source.projects.find(p => p.id === id) || {}, body); else source.projects.push({ id: Date.now(), task: body.task_id, code: '36-606-999', name: body.name, type: '验证性', category: '专业基础', requirement: '必做', participant: '本科', discipline: body.discipline_code, school: body.school_code, group: body.group_size, hours: body.hours }); return {}; }
    return {};
  }
  async function login(role) { token = role === 'admin' ? 'admin' : 'teacher'; sessionStorage.setItem('teaching.prototype.token', token); }
  const save = (path, body, method = 'POST') => request(path, { method, body: JSON.stringify(body) });
  return { request, login, clear, hasToken: () => !!token, get: path => request(path), save, upload: async () => ({ batchId: 1, imported: 1 }), remove: path => request(path, { method: 'DELETE' }), download: () => { const a = document.createElement('a'); a.download = 'prototype-template.xlsx'; a.href = 'data:text/plain,prototype'; a.click(); } };
})();
