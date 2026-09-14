'use strict';

/*
 * Browser client for the Spring Boot teaching API.
 * Unauthenticated visitors keep using the local examples in data.js; authenticated
 * requests always use the database-backed API and the returned Token.
 */
(function () {
  const storage = window.localStorage;
  const configured = String(window.STATIC_FRONT_API_BASE || '').replace(/\/$/, '');
  const base = configured || (location.protocol === 'file:' || location.port === '8090'
    ? 'http://localhost:8080/springboote51e2'
    : `${location.origin}/springboote51e2`);
  const rolePath = { admin: 'users', teacher: 'jiaoshi' };

  function token() {
    return storage.getItem('teachingToken') || '';
  }

  function setToken(value, role) {
    if (value) storage.setItem('teachingToken', value);
    else storage.removeItem('teachingToken');
    if (role) storage.setItem('teachingRole', role);
  }

  function isRemote() {
    return !!token();
  }

  async function responseMessage(response) {
    const type = response.headers.get('content-type') || '';
    if (type.includes('json')) {
      try {
        const body = await response.json();
        return body.msg || body.message || `请求失败（${response.status}）`;
      } catch (_) {
        return `请求失败（${response.status}）`;
      }
    }
    try {
      const text = (await response.text()).trim();
      return text || `请求失败（${response.status}）`;
    } catch (_) {
      return `请求失败（${response.status}）`;
    }
  }

  async function request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (!headers.has('Accept')) headers.set('Accept', 'application/json');
    if (token()) headers.set('Token', token());
    const init = { ...options, headers };
    if (init.body && !(init.body instanceof FormData) && typeof init.body !== 'string') {
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      init.body = JSON.stringify(init.body);
    }
    let response;
    try {
      response = await fetch(`${base}${path}`, init);
    } catch (_) {
      throw new Error('无法连接后端服务，请确认 Spring Boot 已启动');
    }
    const type = response.headers.get('content-type') || '';
    if (!response.ok) {
      const message = await responseMessage(response);
      if (response.status === 401) setToken('');
      throw new Error(message);
    }
    if (type.includes('json')) {
      const body = await response.json();
      if (Number(body.code) !== 0) {
        if (Number(body.code) === 401) setToken('');
        throw new Error(body.msg || body.message || '请求失败');
      }
      return body.data === undefined ? body : body.data;
    }
    return response;
  }

  async function login(username, password, role) {
    const selectedRole = role === 'teacher' ? 'teacher' : 'admin';
    const form = new URLSearchParams({ username, password });
    let response;
    try {
      response = await fetch(`${base}/${rolePath[selectedRole]}/login`, {
        method: 'POST',
        body: form,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
          Accept: 'application/json'
        }
      });
    } catch (_) {
      throw new Error('无法连接后端服务，请确认 Spring Boot 已启动');
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok || Number(body.code) !== 0 || !body.token) {
      throw new Error(body.msg || '账号、密码或登录身份不正确');
    }
    setToken(body.token, selectedRole);
    storage.setItem('teachingUsername', username);
    return body.token;
  }

  async function logout() {
    const role = storage.getItem('teachingRole') === 'teacher' ? 'teacher' : 'admin';
    try {
      if (token()) await request(`/${rolePath[role]}/logout`, { method: 'POST' });
    } catch (_) {
      /* Local cleanup must still happen when the server session already expired. */
    } finally {
      setToken('');
      storage.removeItem('teachingUsername');
      storage.removeItem('teachingRole');
    }
  }

  function dateOnly(value) {
    if (!value) return '';
    if (typeof value === 'string') return value.slice(0, 10);
    const date = new Date(Number(value) + 8 * 60 * 60 * 1000);
    if (Number.isNaN(date.getTime())) return String(value);
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const day = String(date.getUTCDate()).padStart(2, '0');
    return `${date.getUTCFullYear()}-${month}-${day}`;
  }

  function mapTerms(rows) {
    return (rows || []).map(term => ({
      id: Number(term.id),
      name: term.name || `${term.academic_year_name} 第${term.term_no}学期`,
      year: term.academic_year_name || '',
      start: dateOnly(term.starts_on),
      end: dateOnly(term.ends_on),
      current: term.status === 'OPEN' || term.status === 'CURRENT',
      status: term.status || '',
      termNo: Number(term.term_no || 0),
      startYear: Number(term.start_year || 0),
      raw: term
    }));
  }

  function mapLookups(data) {
    const terms = mapTerms(data.terms);
    const currentTerm = terms.find(term => term.status === 'OPEN')
      || terms.find(term => term.status === 'CURRENT')
      || terms[0];
    return {
      terms,
      currentTermId: currentTerm && currentTerm.id,
      teachers: (data.teachers || []).map(teacher => ({
        id: Number(teacher.id),
        code: teacher.gonghao,
        name: teacher.jiaoshixingming,
        college: teacher.xueyuan || '',
        temporary: teacher.is_temporary === true || teacher.is_temporary === 1,
        raw: teacher
      })),
      labs: (data.labs || []).map(lab => ({
        id: Number(lab.id),
        code: lab.shiyanshibianhao,
        name: lab.shiyanshimingcheng,
        location: lab.shiyanshiweizhi || '待安排',
        managerId: lab.manager_teacher_id == null ? null : Number(lab.manager_teacher_id),
        manager: lab.manager_name || '待补充',
        equipment: lab.equipment_count == null ? '' : Number(lab.equipment_count),
        raw: lab
      })),
      courses: (data.courses || []).map(course => ({
        id: Number(course.id),
        code: course.course_code,
        name: course.course_name,
        raw: course
      }))
    };
  }

  function mapTask(row, labs) {
    const labNames = row.lab_names || '';
    const matchedLab = (labs || []).find(lab =>
      labNames.split(/[、,，;；]/).map(value => value.trim()).includes(lab.name));
    return {
      id: Number(row.id),
      code: row.task_code || row.course_code,
      courseCode: row.course_code || '',
      name: row.course_name || row.course_name_snapshot,
      teacher: row.teacher_names || '',
      className: row.class_composition || '',
      major: row.major_composition || '',
      people: Number(row.enrollment_count || 0),
      hours: Number(row.planned_lab_hours || 0),
      scheduledHours: Number(row.scheduled_hours || 0),
      projectCount: Number(row.project_count || 0),
      labNames,
      lab: matchedLab ? matchedLab.id : 0,
      term: String(row.term_id),
      termName: row.term_name || '',
      status: row.status || '',
      courseId: row.course_id == null ? null : Number(row.course_id),
      day: 1,
      period: 1,
      weeks: 16,
      schedule: null,
      editable: false,
      raw: row
    };
  }

  function mapSchedule(task, rows) {
    task.schedule = rows || [];
    const first = task.schedule[0];
    if (first) {
      task.day = Number(first.weekday || 1);
      task.period = Math.max(1, Math.ceil(Number(first.period_start || 1) / 2));
      task.weeks = Math.max(...task.schedule.map(slot => Number(slot.teaching_week || 1)));
    }
    return task;
  }

  function mapProject(row) {
    const categories = { '1': '基础', '2': '专业基础', '3': '专业', '4': '其它' };
    const types = { '1': '演示性', '2': '验证性', '3': '综合性', '4': '设计研究', '5': '其它' };
    const requirements = { '1': '必做', '2': '选做', '3': '其它' };
    const participants = { '1': '博士', '2': '硕士', '3': '本科', '4': '专科', '5': '其他' };
    return {
      id: Number(row.id),
      task: Number(row.task_id),
      code: row.project_code,
      name: row.name,
      category: categories[row.category_code] || row.category_code,
      type: types[row.type_code] || row.type_code,
      requirement: requirements[row.requirement_code] || row.requirement_code,
      participant: participants[row.participant_type_code] || row.participant_type_code,
      discipline: row.discipline_code || '',
      school: row.school_code || '',
      group: Number(row.group_size || 1),
      hours: Number(row.hours || 0),
      sort: Number(row.sort_order || 0),
      raw: row
    };
  }

  async function loadPages(path, params = {}, pageSize = 200) {
    const rows = [];
    let page = 1;
    let total = 0;
    do {
      const query = new URLSearchParams({ ...params, page: String(page), limit: String(pageSize) });
      const result = await request(`${path}?${query.toString()}`);
      const pageRows = result.list || result.rows || [];
      rows.push(...pageRows);
      total = Number(result.total == null ? rows.length : result.total);
      page += 1;
      if (!pageRows.length) break;
    } while (rows.length < total && page <= 20);
    return { list: rows, total };
  }

  async function loadTasks(options = {}) {
    const result = await loadPages('/teaching/tasks', {
      termId: options.termId == null ? '' : options.termId,
      q: options.q || ''
    }, options.limit || 200);
    return result;
  }

  async function loadProjects(taskId) {
    const result = await request(`/teaching/projects?taskId=${encodeURIComponent(taskId)}`);
    return {
      list: (result.list || []).map(mapProject),
      total: Number(result.total || 0),
      editable: result.editable === true
    };
  }

  async function loadTask(taskId) {
    return request(`/teaching/tasks/${encodeURIComponent(taskId)}`);
  }

  function formatDateTime(value) {
    if (!value) return '';
    const date = typeof value === 'number' ? new Date(value) : new Date(String(value).replace(' ', 'T'));
    if (Number.isNaN(date.getTime())) return String(value);
    const pad = number => String(number).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  async function loadAll(role, selectedTermId) {
    const [lookupData, dashboard] = await Promise.all([
      request('/teaching/lookups'),
      request('/teaching/dashboard').catch(() => null)
    ]);
    const lookups = mapLookups(lookupData);
    const taskResult = await loadTasks();
    const tasks = taskResult.list.map(row => mapTask(row, lookups.labs));
    const termId = selectedTermId || lookups.currentTermId;
    const selectedTasks = tasks.filter(task =>
      String(task.term) === String(termId) && task.projectCount > 0);
    const loadedProjectTasks = new Set();
    const projectResults = await Promise.all(selectedTasks.map(async task => {
      try {
        const result = await loadProjects(task.id);
        loadedProjectTasks.add(task.id);
        return result.list;
      } catch (_) {
        return [];
      }
    }));
    const projects = projectResults.flat();

    let report = null;
    let batches = [];
    let aiStatus = null;
    if (role === 'admin') {
      [report, batches, aiStatus] = await Promise.all([
        termId
          ? request(`/teaching/reports?termId=${encodeURIComponent(termId)}`).catch(() => null)
          : Promise.resolve(null),
        request('/teaching/imports').then(result => result.list || []).catch(() => []),
        request('/teaching/ai/status').catch(() => null)
      ]);
    }
    return {
      ...lookups,
      tasks,
      projects,
      loadedProjectTasks: Array.from(loadedProjectTasks),
      batches: batches.map(batch => ({
        id: Number(batch.id),
        name: batch.file_name || batch.filename || '未命名文件',
        kind: '课程课表',
        date: formatDateTime(batch.created_at),
        rows: Number(batch.row_count || 0),
        done: Number(batch.promoted || 0),
        review: Number(batch.warnings || 0),
        errors: Number(batch.errors || 0),
        raw: batch
      })),
      report,
      dashboard,
      aiStatus,
      remote: true
    };
  }

  async function json(path, method, body) {
    return request(path, { method, body });
  }

  async function upload(path, file, params = {}) {
    const form = new FormData();
    form.append('file', file);
    Object.entries(params).forEach(([key, value]) => form.append(key, value));
    return request(path, { method: 'POST', body: form });
  }

  async function download(path) {
    const response = await request(path);
    const blob = await response.blob();
    const disposition = response.headers.get('content-disposition') || '';
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    const plain = disposition.match(/filename="?([^";]+)"?/i);
    let filename = encoded ? decodeURIComponent(encoded[1]) : plain ? plain[1] : '';
    if (!filename) filename = path.split('/').pop() || 'download.xlsx';
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  window.TeachingApi = {
    base,
    token,
    isRemote,
    login,
    logout,
    request,
    json,
    upload,
    download,
    loadAll,
    loadTasks,
    loadProjects,
    loadTask,
    loadPages,
    mapLookups,
    mapTask,
    mapSchedule,
    mapProject,
    setToken,
    formatDateTime
  };
})();
