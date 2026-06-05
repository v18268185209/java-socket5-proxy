<template>
  <div>
    <h2>代理配置</h2>
    <el-tabs v-model="activeTab">
      <!-- SOCKS5 配置 -->
      <el-tab-pane label="SOCKS5 配置" name="socks">
        <el-form :model="socksForm" label-width="160px" style="max-width: 600px;">
          <el-form-item label="启用 SOCKS5">
            <el-switch v-model="socksForm.enabled" />
          </el-form-item>
          <el-form-item label="绑定地址">
            <el-input v-model="socksForm.bindHost" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="socksForm.port" :min="1" :max="65535" />
          </el-form-item>
          <el-divider>认证配置</el-divider>
          <el-form-item label="启用认证">
            <el-switch v-model="socksForm.auth.enabled" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="socksForm.auth.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="socksForm.auth.password" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveSocksConfig">保存 SOCKS5 配置</el-button>
            <el-button @click="applyChanges">应用并重载</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- HTTP 配置 -->
      <el-tab-pane label="HTTP 配置" name="http">
        <el-form :model="httpForm" label-width="160px" style="max-width: 600px;">
          <el-form-item label="启用 HTTP">
            <el-switch v-model="httpForm.enabled" />
          </el-form-item>
          <el-form-item label="绑定地址">
            <el-input v-model="httpForm.bindHost" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="httpForm.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="引擎">
            <el-select v-model="httpForm.engine">
              <el-option label="Legacy (Netty)" value="legacy" />
              <el-option label="Squid" value="squid" />
            </el-select>
          </el-form-item>
          <el-divider>认证配置</el-divider>
          <el-form-item label="启用认证">
            <el-switch v-model="httpForm.auth.enabled" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="httpForm.auth.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="httpForm.auth.password" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveHttpConfig">保存 HTTP 配置</el-button>
            <el-button @click="applyChanges">应用并重载</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- ACL 配置 -->
      <el-tab-pane label="ACL 控制" name="acl">
        <el-form :model="aclForm" label-width="160px" style="max-width: 600px;">
          <el-form-item label="启用 ACL">
            <el-switch v-model="aclForm.enabled" />
          </el-form-item>
          <el-form-item label="允许客户端 CIDR">
            <el-input v-model="aclCidrsText" type="textarea" :rows="3" placeholder="每行一个 CIDR，如 10.0.0.0/8" />
          </el-form-item>
          <el-form-item label="拒绝目标主机">
            <el-input v-model="aclDenyHostsText" type="textarea" :rows="3" placeholder="每行一个规则" />
          </el-form-item>
          <el-form-item label="拒绝目标端口">
            <el-input v-model="aclDenyPortsText" type="textarea" :rows="3" placeholder="每行一个端口" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveAclConfig">保存 ACL 配置</el-button>
            <el-button @click="applyChanges">应用并重载</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 性能配置 -->
      <el-tab-pane label="性能优化" name="perf">
        <el-form :model="perfForm" label-width="180px" style="max-width: 600px;">
          <el-form-item label="连接超时 (ms)">
            <el-input-number v-model="perfForm.connectTimeoutMillis" :min="1000" />
          </el-form-item>
          <el-form-item label="空闲超时 (秒)">
            <el-input-number v-model="perfForm.idleTimeoutSeconds" :min="10" />
          </el-form-item>
          <el-form-item label="Boss 线程数">
            <el-input-number v-model="perfForm.bossThreads" :min="1" />
          </el-form-item>
          <el-form-item label="Worker 线程数 (0=自动)">
            <el-input-number v-model="perfForm.workerThreads" :min="0" />
          </el-form-item>
          <el-form-item label="Backlog">
            <el-input-number v-model="perfForm.backlog" :min="1" />
          </el-form-item>
          <el-form-item label="每客户端最大连接">
            <el-input-number v-model="perfForm.maxConnectionsPerClient" :min="0" />
            <span style="color: #999; margin-left: 10px;">0=不限制</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="savePerfConfig">保存性能配置</el-button>
            <el-button @click="applyChanges">应用并重载</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { getConfig, setConfigValue, reloadConfig as apiReload } from '@/api/config'
import { ElMessage } from 'element-plus'

const activeTab = ref('socks')

const socksForm = ref({ enabled: true, bindHost: '0.0.0.0', port: 1080, auth: { enabled: true, username: '', password: '' } })
const httpForm = ref({ enabled: true, bindHost: '0.0.0.0', port: 8080, engine: 'legacy', auth: { enabled: true, username: '', password: '' } })
const aclForm = ref({ enabled: true, allowClientCidrs: [], denyTargetHosts: [], denyTargetPorts: [] })
const perfForm = ref({ connectTimeoutMillis: 10000, idleTimeoutSeconds: 120, bossThreads: 1, workerThreads: 0, backlog: 1024, maxConnectionsPerClient: 0 })

const aclCidrsText = ref('')
const aclDenyHostsText = ref('')
const aclDenyPortsText = ref('')

watch(aclCidrsText, val => { aclForm.value.allowClientCidrs = val.split('\n').filter(Boolean).map(s => s.trim()) })
watch(aclDenyHostsText, val => { aclForm.value.denyTargetHosts = val.split('\n').filter(Boolean).map(s => s.trim()) })
watch(aclDenyPortsText, val => { aclForm.value.denyTargetPorts = val.split('\n').filter(Boolean).map(Number).filter(n => !isNaN(n)) })

onMounted(async () => {
  try {
    const { data } = await getConfig()
    const proxy = data?.proxy || {}
    const socks = proxy.socks || {}
    socksForm.value = { enabled: socks.enabled ?? true, bindHost: socks.bindHost || '0.0.0.0', port: socks.port || 1080, auth: { ...socks.auth } }
    const http = proxy.http || {}
    httpForm.value = { enabled: http.enabled ?? true, bindHost: http.bindHost || '0.0.0.0', port: http.port || 8080, engine: http.engine || 'legacy', auth: { ...http.auth } }
    const acl = proxy.acl || {}
    aclForm.value = { ...acl }
    aclCidrsText.value = (acl.allowClientCidrs || []).join('\n')
    aclDenyHostsText.value = (acl.denyTargetHosts || []).join('\n')
    aclDenyPortsText.value = (acl.denyTargetPorts || []).join('\n')
    const perf = proxy.performance || {}
    perfForm.value = { connectTimeoutMillis: perf.connectTimeoutMillis || 10000, idleTimeoutSeconds: perf.idleTimeoutSeconds || 120, bossThreads: perf.bossThreads || 1, workerThreads: perf.workerThreads || 0, backlog: perf.backlog || 1024, maxConnectionsPerClient: perf.maxConnectionsPerClient || 0 }
  } catch (e) {
    ElMessage.error('加载配置失败')
  }
})

async function saveSocksConfig() {
  const f = socksForm.value
  await setConfigValue('proxy.socks.enabled', f.enabled)
  await setConfigValue('proxy.socks.bind-host', f.bindHost)
  await setConfigValue('proxy.socks.port', f.port)
  await setConfigValue('proxy.socks.auth.enabled', f.auth.enabled)
  await setConfigValue('proxy.socks.auth.username', f.auth.username)
  await setConfigValue('proxy.socks.auth.password', f.auth.password)
  ElMessage.success('SOCKS5 配置已保存')
}

async function saveHttpConfig() {
  const f = httpForm.value
  await setConfigValue('proxy.http.enabled', f.enabled)
  await setConfigValue('proxy.http.bind-host', f.bindHost)
  await setConfigValue('proxy.http.port', f.port)
  await setConfigValue('proxy.http.engine', f.engine)
  await setConfigValue('proxy.http.auth.enabled', f.auth.enabled)
  await setConfigValue('proxy.http.auth.username', f.auth.username)
  await setConfigValue('proxy.http.auth.password', f.auth.password)
  ElMessage.success('HTTP 配置已保存')
}

async function saveAclConfig() {
  const f = aclForm.value
  await setConfigValue('proxy.acl.enabled', f.enabled)
  await setConfigValue('proxy.acl.allow-client-cidrs', f.allowClientCidrs)
  await setConfigValue('proxy.acl.deny-target-hosts', f.denyTargetHosts)
  await setConfigValue('proxy.acl.deny-target-ports', f.denyTargetPorts)
  ElMessage.success('ACL 配置已保存')
}

async function savePerfConfig() {
  const f = perfForm.value
  await setConfigValue('proxy.performance.connect-timeout-millis', f.connectTimeoutMillis)
  await setConfigValue('proxy.performance.idle-timeout-seconds', f.idleTimeoutSeconds)
  await setConfigValue('proxy.performance.boss-threads', f.bossThreads)
  await setConfigValue('proxy.performance.worker-threads', f.workerThreads)
  await setConfigValue('proxy.performance.backlog', f.backlog)
  await setConfigValue('proxy.performance.max-connections-per-client', f.maxConnectionsPerClient)
  ElMessage.success('性能配置已保存')
}

async function applyChanges() {
  try {
    await apiReload()
    ElMessage.success('配置已应用并重载')
    window.location.reload()
  } catch (e) {
    ElMessage.error('配置重载失败')
  }
}
</script>
