<template>
  <div>
    <h2>用户管理</h2>
    <div style="margin-bottom: 20px;">
      <el-button type="primary" @click="showAddDialog">添加用户</el-button>
    </div>

    <el-table :data="users" stripe style="width: 100%;">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="200" />
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" />
      <el-table-column label="操作" width="250">
        <template #default="{ row }">
          <el-button size="small" @click="toggleUserStatus(row)">
            {{ row.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" type="warning" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="confirmDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '添加用户'" width="400px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password :placeholder="isEdit ? '留空则不修改' : ''" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUsers, createUser, toggleUser, deleteUser } from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ username: '', password: '', enabled: true })

onMounted(async () => {
  const res = await getUsers()
  users.value = res.data || []
})

function showAddDialog() {
  isEdit.value = false
  form.value = { username: '', password: '', enabled: true }
  dialogVisible.value = true
}

function showEditDialog(user) {
  isEdit.value = true
  form.value = { username: user.username, password: '', enabled: user.enabled }
  dialogVisible.value = true
}

async function submitUser() {
  if (!form.value.username) {
    ElMessage.warning('用户名不能为空')
    return
  }
  if (!isEdit.value && !form.value.password) {
    ElMessage.warning('密码不能为空')
    return
  }
  try {
    const payload = {
      username: form.value.username,
      password: form.value.password || undefined,
      enabled: form.value.enabled
    }
    await createUser(payload)
    ElMessage.success(isEdit.value ? '用户已更新' : '用户已添加')
    dialogVisible.value = false
    const res = await getUsers()
    users.value = res.data || []
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function toggleUserStatus(user) {
  try {
    await toggleUser(user.username)
    ElMessage.success(`用户已${user.enabled ? '禁用' : '启用'}`)
    const res = await getUsers()
    users.value = res.data || []
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function confirmDelete(user) {
  try {
    await ElMessageBox.confirm(`确定要删除用户 "${user.username}" 吗？`, '确认删除', { type: 'warning' })
    await deleteUser(user.username)
    ElMessage.success('用户已删除')
    const res = await getUsers()
    users.value = res.data || []
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}
</script>
