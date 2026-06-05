<template>
  <div>
    <h2>实时监控</h2>

    <el-row :gutter="20" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align: center;">
            <div style="font-size: 24px; font-weight: bold; color: #409eff;">{{ metrics?.activeConnections || 0 }}</div>
            <div style="color: #999; font-size: 14px;">当前连接</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align: center;">
            <div style="font-size: 24px; font-weight: bold; color: #67c23a;">{{ metrics?.totalConnections || 0 }}</div>
            <div style="color: #999; font-size: 14px;">总连接数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align: center;">
            <div style="font-size: 24px; font-weight: bold; color: #e6a23c;">{{ formatBytes(metrics?.totalBytesFromTarget || 0) }}</div>
            <div style="color: #999; font-size: 14px;">数据传输</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div style="text-align: center;">
            <div style="font-size: 24px; font-weight: bold; color: #f56c6c;">{{ metrics?.authFailures || 0 }}</div>
            <div style="color: #999; font-size: 14px;">认证失败</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>活跃连接</span>
          <el-button size="small" @click="refreshMetrics">刷新</el-button>
        </div>
      </template>
      <el-table :data="(metrics?.activeSessions || []).slice(0, 50)" stripe style="width: 100%;" max-height="400">
        <el-table-column prop="id" label="Session ID" width="120" />
        <el-table-column prop="protocol" label="协议" width="80" />
        <el-table-column prop="clientAddress" label="客户端" width="150" />
        <el-table-column prop="targetAddress" label="目标地址" show-overflow-tooltip />
        <el-table-column label="上传" width="100">
          <template #default="{ row }">{{ formatBytes(row.bytesFromClient || 0) }}</template>
        </el-table-column>
        <el-table-column label="下载" width="100">
          <template #default="{ row }">{{ formatBytes(row.bytesFromTarget || 0) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header><span>最近事件</span></template>
      <el-timeline>
        <el-timeline-item
          v-for="event in (metrics?.events || []).slice(0, 20)"
          :key="event.message"
          :timestamp="event.timestamp"
          :type="event.level === 'ERROR' ? 'danger' : event.level === 'WARN' ? 'warning' : 'info'"
        >
          {{ event.message }}
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getMetrics } from '@/api/monitor'
import { ElMessage } from 'element-plus'

const metrics = ref(null)
let timer = null

async function refreshMetrics() {
  try {
    const res = await getMetrics()
    metrics.value = res.data
  } catch (e) {
    ElMessage.error('刷新失败')
  }
}

function formatBytes(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

onMounted(() => {
  refreshMetrics()
  timer = setInterval(refreshMetrics, 2000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
