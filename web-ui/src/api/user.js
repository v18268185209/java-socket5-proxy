import api from './request'

export function getUsers() {
  return api.get('/users')
}

export function createUser(data) {
  return api.post('/users', data)
}

export function toggleUser(username) {
  return api.put(`/users/${encodeURIComponent(username)}/toggle`)
}

export function deleteUser(username) {
  return api.delete(`/users/${encodeURIComponent(username)}`)
}
