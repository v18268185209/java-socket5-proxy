import api from './request'

export function getMetrics() {
  return api.get('/dashboard/overview')
}

export function getEvents() {
  return api.get('/dashboard/events')
}
