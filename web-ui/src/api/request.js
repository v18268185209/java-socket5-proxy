import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

// 管理界面 Basic Auth
const MGMT_USER = import.meta.env.VITE_MGMT_USER || 'mgmtadmin'
const MGMT_PASS = import.meta.env.VITE_MGMT_PASS || 'mgmtadmin'

api.interceptors.request.use(config => {
  config.headers.Authorization = 'Basic ' + btoa(`${MGMT_USER}:${MGMT_PASS}`)
  return config
})

api.interceptors.response.use(
  response => response,
  error => {
    ElMessage.error('请求失败: ' + (error.message || '未知错误'))
    return Promise.reject(error)
  }
)

export default api
