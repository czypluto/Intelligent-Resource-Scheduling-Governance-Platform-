<script setup>
import { nextTick, reactive, ref } from 'vue'
import { chatStream, clearToken } from '../api'

const props = defineProps({ user: { type: Object, required: true } })
const emit = defineEmits(['logout'])

const messages = reactive([]) // { role, text, stage, tone, model }
const input = ref('')
const sending = ref(false)
const listRef = ref(null)
let abort = null

const QUICK = [
  '帮我预约总裁班车座位',
  '现在有哪些会议室可以约',
  '我能约总裁班车吗'
]

function scrollBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function send(text) {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return
  input.value = ''
  sending.value = true

  const current = { role: 'assistant', text: '', stage: '正在处理…', tone: 'normal', model: '' }
  messages.push({ role: 'user', text: content })
  messages.push(current)
  scrollBottom()

  const onEvent = (ev) => {
    if (ev.kind === 'think') {
      current.stage = '正在为您办理…'
    } else if (ev.kind === 'check') {
      current.stage = '正在校验预约权限…'
    } else if (ev.kind === 'act') {
      current.stage = '正在抢票…'
    } else if (ev.kind === 'error') {
      current.stage = ''
      current.text = ev.text
      current.tone = 'error'
    } else if (ev.kind === 'denied') {
      current.stage = ''
      current.text = ev.text
      current.tone = 'denied'
    } else if (ev.kind === 'result' || ev.kind === 'answer') {
      current.stage = ''
      current.text = ev.text
      current.tone = ev.kind === 'result' ? 'success' : 'normal'
    }
    current.model = ev.model || current.model
    scrollBottom()
  }
  const onDone = () => {
    sending.value = false
    if (!current.text) {
      current.stage = ''
      current.text = '服务未返回结果，请重试。'
      current.tone = 'error'
    }
    scrollBottom()
  }

  abort = chatStream(content, localStorage.getItem('resv_token'), onEvent, onDone)
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function stopStream() {
  if (abort) abort()
  sending.value = false
}

function logout() {
  clearToken()
  localStorage.removeItem('resv_user')
  emit('logout')
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div class="brand">铁路购票系统</div>
      <div class="user">
        <span>{{ user.name }}（{{ user.department }} · {{ user.position }}）</span>
        <el-button link type="primary" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="main">
      <section class="chat">
        <div ref="listRef" class="msg-list">
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="msg"
            :class="m.role === 'user' ? 'msg-user' : `msg-assist msg-${m.tone}`"
          >
            <div class="bubble">
              <span v-if="m.stage" class="stage">{{ m.stage }}</span>
              <div v-if="m.text" class="text">{{ m.text }}</div>
              <div v-if="m.model" class="meta">经 {{ m.model }}</div>
            </div>
          </div>
          <div v-if="messages.length === 0" class="empty">
            请用一句话描述要预约的资源，例如“帮我预约下周一的员工班车”。
          </div>
        </div>

        <div class="quick" v-if="!sending">
          <button v-for="q in QUICK" :key="q" class="chip" @click="send(q)">{{ q }}</button>
        </div>

        <div class="input-row">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            :disabled="sending"
            placeholder="输入预约需求，Enter 发送，Shift+Enter 换行"
            resize="none"
            @keydown="onKeydown"
          />
          <div class="actions">
            <el-button v-if="sending" @click="stopStream">停止</el-button>
            <el-button type="primary" :disabled="sending || !input.trim()" @click="send()">
              发送
            </el-button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--resv-line);
}
.brand {
  color: var(--resv-blue-dark);
  font-size: 16px;
}
.user {
  font-size: 13px;
  color: #5b6b7d;
}
.main {
  flex: 1;
  min-height: 0;
  padding: 16px 24px 24px;
}
.chat {
  height: 100%;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--resv-line);
}
.msg-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}
.empty {
  margin-top: 60px;
  text-align: center;
  color: #8592a5;
  font-size: 14px;
}
.msg {
  display: flex;
  margin-bottom: 14px;
}
.msg-user {
  justify-content: flex-end;
}
.msg-user .bubble {
  background: var(--resv-blue);
  color: #fff;
}
.msg-assist .bubble {
  background: #f4f6f9;
  border: 1px solid #e6ebf1;
}
.msg-denied .bubble {
  background: #fdf6ec;
  border-color: #f5d7a0;
}
.msg-error .bubble {
  background: #fef0f0;
  border-color: #f3c2c2;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}
.stage {
  color: #7c8a9b;
  font-size: 13px;
}
.meta {
  margin-top: 6px;
  font-size: 11px;
  color: #9aa7b5;
}
.quick {
  padding: 4px 12px 8px;
  border-top: 1px dashed #e6ebf1;
}
.chip {
  margin: 0 8px 4px 0;
  padding: 4px 10px;
  border: 1px solid var(--resv-line);
  border-radius: 12px;
  background: #fff;
  color: var(--resv-blue);
  font-size: 12px;
  cursor: pointer;
}
.chip:hover {
  border-color: var(--resv-blue);
}
.input-row {
  flex: none;
  padding: 12px;
  border-top: 1px solid var(--resv-line);
}
.actions {
  margin-top: 8px;
  text-align: right;
}
</style>
