import http from '@/utils/http'
import storage from '@/utils/storage'
import { Message } from 'element-ui'

export async function request(path, options = {}) {
  const response = await http(
    Object.assign({ url: '/teaching' + path, method: 'get', timeout: 60000 }, options)
  )
  const body = response.data
  if (!body || Number(body.code) !== 0) throw new Error((body && body.msg) || '请求失败，请稍后重试')
  return body.data
}

export function errorMessage(error) {
  const response = error && error.response
  const text =
    (response && response.data && response.data.msg) || (error && error.message) || '操作失败，请稍后重试'
  Message.error(text === 'Network Error' ? '暂时无法连接服务，请检查后端是否启动' : text)
}

export async function download(path, filename, params = {}) {
  let response
  try {
    response = await http({
      url: '/teaching' + path,
      method: 'get',
      params,
      responseType: 'blob',
      timeout: 60000
    })
  } catch (error) {
    const errorBlob = error.response && error.response.data
    if (errorBlob instanceof Blob) {
      let body
      try {
        body = JSON.parse(await errorBlob.text())
      } catch (parseError) {
        /* Preserve the original HTTP error. */
      }
      if (body && body.msg) throw new Error(body.msg)
    }
    throw error
  }
  const blob = response.data
  const type = response.headers['content-type'] || blob.type || ''
  // Business errors use the normal JSON envelope even on download endpoints.
  const head = await blob.slice(0, 100).text()
  if (type.includes('json') || head.trim().startsWith('{')) {
    const body = JSON.parse(await blob.text())
    throw new Error(body.msg || '文件下载失败')
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function upload(path, file, params = {}) {
  if (!file || !/\.xlsx$/i.test(file.name)) return Promise.reject(new Error('请选择 .xlsx 格式的 Excel 文件'))
  if (file.size >= 10 * 1024 * 1024) return Promise.reject(new Error('文件大小须小于 10 MB'))
  const data = new FormData()
  data.append('file', file)
  return request(path, {
    method: 'post',
    data,
    params,
    timeout: 120000,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const shared = {
  data: () => ({
    loading: false,
    saving: false,
    downloading: false,
    lookups: { terms: [], teachers: [], labs: [], courses: [] }
  }),
  computed: {
    isAdmin() {
      return storage.get('sessionTable') === 'users'
    }
  },
  methods: {
    fail: errorMessage,
    async loadLookups() {
      this.lookups = Object.assign(
        { terms: [], teachers: [], labs: [], courses: [] },
        await request('/lookups')
      )
    },
    text(value) {
      return value === null || value === undefined || value === '' ? '待确认' : value
    },
    status(value) {
      return (
        {
          CURRENT: '当前学期',
          OPEN: '当前学期',
          ARCHIVED: '历史归档',
          FUTURE: '未来学期',
          DRAFT: '未来学期',
          REVIEW: '待核对',
          ERROR: '错误',
          PROMOTED: '已入库',
          COMPLETED: '已完成',
          SUCCESS: '已完成',
          PARTIAL: '部分入库',
          TEMPORARY: '系统分配临时账号'
        }[value] ||
        value ||
        '待确认'
      )
    },
    async downloadFile(path, name, params) {
      this.downloading = true
      try {
        await download(path, name, params)
      } catch (e) {
        this.fail(e)
      } finally {
        this.downloading = false
      }
    }
  }
}
