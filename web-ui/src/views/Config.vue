<template>
  <div>
    <h2>系统配置</h2>

    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>配置文件 (application.yml)</span>
          <div>
            <el-button type="primary" @click="loadConfig" :loading="loading">加载</el-button>
            <el-button type="success" @click="saveAndReload" :loading="loading">保存并重载</el-button>
          </div>
        </div>
      </template>

      <el-input v-model="configText" type="textarea" :rows="25" placeholder="加载配置后显示 YAML 内容" style="font-family: monospace; font-size: 13px;" />

      <div style="margin-top: 10px; color: #999; font-size: 12px;">
        注意：修改配置后需要点击"保存并重载"才能生效。
      </div>
    </el-card>

    <el-card>
      <template #header><span>管理账户</span></template>
      <el-form :model="mgmtForm" label-width="120px" style="max-width: 400px;">
        <el-form-item label="用户名">
          <el-input v-model="mgmtForm.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="mgmtForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="Token 认证">
          <el-switch v-model="mgmtForm.tokenAuth" />
        </el-form-item>
        <el-form-item label="Access Token">
          <el-input v-model="mgmtForm.accessToken" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveMgmtConfig">保存管理配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getConfig, setConfig, reloadConfig as apiReload } from '@/api/config'
import { ElMessage } from 'element-plus'

const configText = ref('')
const loading = ref(false)
const mgmtForm = ref({ username: '', password: '', tokenAuth: false, accessToken: '' })

async function loadConfig() {
  loading.value = true
  try {
    const res = await getConfig()
    if (res.data) {
      configText.value = JSON.stringify(res.data, null, 2)
      const mgmt = res.data.proxy?.management?.basic
      if (mgmt) {
        mgmtForm.value = {
          username: mgmt.username || '',
          password: mgmt.password || '',
          tokenAuth: res.data.proxy?.management?.allowTokenAuth || false,
          accessToken: res.data.proxy?.management?.accessToken || ''
        }
      }
    }
  } catch (e) {
    ElMessage.error('加载配置失败')
  } finally {
    loading.value = false
  }
}

async function saveAndReload() {
  loading.value = true
  try {
    const config = JSON.parse(configText.value)
    await setConfig(config)
    await apiReload()
    ElMessage.success('配置已保存并重载')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

async function saveMgmtConfig() {
  try {
    const res = await getConfig()
    if (res.data?.proxy?.management) {
      res.data.proxy.management.basic.username = mgmtForm.value.username
      res.data.proxy.management.basic.password = mgmtForm.value.password
      res.data.proxy.management.allowTokenAuth = mgmtForm.value.tokenAuth
      res.data.proxy.management.accessToken = mgmtForm.value.accessToken
      await setConfig(res.data)
      await apiReload()
      ElMessage.success('管理配置已保存')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  }
}
</script>
