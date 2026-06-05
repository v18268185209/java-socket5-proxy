import api from './request'

export function getConfig() {
  return api.get('/config')
}

export function setConfig(config) {
  return api.put('/config', config)
}

export function getConfigValue(path) {
  return api.get(`/config/${path}`)
}

export function setConfigValue(path, value) {
  return api.patch(`/config/${path}`, { value })
}

export function reloadConfig() {
  return api.post('/config/reload')
}
