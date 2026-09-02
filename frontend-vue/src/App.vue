<script setup>
import { ref } from 'vue'
import LoginView from './views/LoginView.vue'
import ChatView from './views/ChatView.vue'

// 演示期用 token 判断是否登录；正式对接统一认证后替换。
const user = ref(JSON.parse(localStorage.getItem('resv_user') || 'null'))

function onLogin(info) {
  user.value = info
}
function onLogout() {
  user.value = null
}
</script>

<template>
  <LoginView v-if="!user" @login="onLogin" />
  <ChatView v-else :user="user" @logout="onLogout" />
</template>
