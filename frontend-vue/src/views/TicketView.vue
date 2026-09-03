<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { buyTicket, listContacts, listStations, payOrder, queryTickets } from '../api'

const props = defineProps({ user: { type: Object, required: true } })

const stations = ref([]) // {id,name}
const contacts = ref([])
const rows = ref([])
const loading = ref(false)

const form = reactive({
  from: null,
  to: null,
  date: new Date(Date.now() + 86400000).toISOString().slice(0, 10),
  seatClass: ''
})

onMounted(async () => {
  try {
    stations.value = await listStations()
    contacts.value = await listContacts()
  } catch (e) {
    ElMessage.error(e.message)
  }
})

const yuan = (c) => (c / 100).toFixed(0)

async function search() {
  if (!form.from || !form.to || !form.date) {
    ElMessage.warning('请选择出发地、到达地与日期')
    return
  }
  loading.value = true
  try {
    rows.value = await queryTickets(form.from, form.to, form.date, form.seatClass)
    if (!rows.value.length) ElMessage.info('该区间当日无可用车次或余票')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function buy(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认购买 ${row.trainCode} ${row.from}->${row.to} ${row.seatClass}（¥${yuan(row.priceCents)}）？\n乘车人默认本人。`,
      '确认购票',
      { confirmButtonText: '确认购买', cancelButtonText: '取消', inputPlaceholder: '输入订单备注（可选）' }
    )
    const r = await buyTicket({
      tripId: row.tripId,
      seatClass: row.seatClass,
      fromStationId: form.from,
      toStationId: form.to
    })
    ElMessage.success(`下单成功，订单号 ${r.orderNo}（待支付）`)
    await goPay(r.requestId)
    await search()
  } catch (e) {
    if (e === 'cancel') return
    ElMessage.error(e.message || '已取消')
  }
}

async function goPay(requestId) {
  try {
    await ElMessageBox.confirm('座位已锁定，立即支付？', '支付', {
      confirmButtonText: '立即支付', cancelButtonText: '稍后在我的车票支付'
    })
    await payOrder(requestId)
    ElMessage.success('支付成功')
  } catch (_) {
    /* 用户选择稍后支付 */
  }
}
</script>

<template>
  <div class="panel">
    <el-form inline>
      <el-form-item label="出发地">
        <el-select v-model="form.from" filterable placeholder="出发站" style="width: 150px">
          <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="到达地">
        <el-select v-model="form.to" filterable placeholder="到达站" style="width: 150px">
          <el-option v-for="s in stations" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="乘车日期" />
      </el-form-item>
      <el-form-item label="席别">
        <el-select v-model="form.seatClass" clearable placeholder="全部席别" style="width: 130px">
          <el-option label="二等座" value="二等座" />
          <el-option label="一等座" value="一等座" />
          <el-option label="商务座" value="商务座" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="search">查询车票</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" stripe empty-text="请输入条件查询车票">
      <el-table-column prop="trainCode" label="车次" width="90" />
      <el-table-column label="区间" min-width="170">
        <template #default="{ row }">{{ row.from }} -> {{ row.to }}</template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ row.departTime }} ~ {{ row.arriveTime }}</template>
      </el-table-column>
      <el-table-column prop="seatClass" label="席别" width="90" />
      <el-table-column label="票价" width="90">
        <template #default="{ row }">¥{{ yuan(row.priceCents) }}</template>
      </el-table-column>
      <el-table-column label="余票" width="80">
        <template #default="{ row }">
          <span :class="row.remaining > 0 ? 'ok' : 'no'">{{ row.remaining }} 张</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="primary" :disabled="row.remaining <= 0" @click="buy(row)">
            购票
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.panel { background: #fff; border: 1px solid var(--resv-line); padding: 16px; }
.ok { color: #1e4e8c; }
.no { color: #b0b8c2; }
</style>
