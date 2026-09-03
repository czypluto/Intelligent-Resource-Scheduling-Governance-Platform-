<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelOrder, myOrders, payOrder } from '../api'

const orders = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    orders.value = await myOrders()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const STATUS = { PENDING: '待支付', PAID: '已支付', CANCELLED: '已取消', EXPIRED: '已过期' }
const yuan = (c) => (c / 100).toFixed(0)

async function pay(o) {
  try {
    await payOrder(o.requestId)
    ElMessage.success('支付成功')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function cancel(o) {
  try {
    await cancelOrder(o.requestId)
    ElMessage.success('已退票，余票已回补')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="panel">
    <el-table v-loading="loading" :data="orders" stripe empty-text="暂无订单">
      <el-table-column label="订单号" width="200">
        <template #default="{ row }">{{ row.orderNo }}</template>
      </el-table-column>
      <el-table-column label="区间" min-width="170">
        <template #default="{ row }">{{ row.from }} -> {{ row.to }}</template>
      </el-table-column>
      <el-table-column prop="seatClass" label="席别" width="90" />
      <el-table-column prop="passengerName" label="乘车人" width="100" />
      <el-table-column label="票价" width="90">
        <template #default="{ row }">¥{{ yuan(row.priceCents) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ STATUS[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="下单时间" width="170">
        <template #default="{ row }">{{ (row.createdAt || '').replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="pay(row)">支付</el-button>
          <el-button
            v-if="row.status === 'PAID' || row.status === 'PENDING'"
            size="small"
            @click="cancel(row)"
          >退票</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.panel { background: #fff; border: 1px solid var(--resv-line); padding: 16px; }
</style>
