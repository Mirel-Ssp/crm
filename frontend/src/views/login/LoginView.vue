<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="title">CRM 客户管理系统</h2>
      <el-form :model="form" :rules="rules" label-position="top" @keyup.enter="onLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名 / 工号" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" size="large" />
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="onLogin">登 录</el-button>
      </el-form>
      <p class="tip">默认管理员账号 admin / admin123（首次登录后请修改密码）</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { login } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// POST /api/auth/login（JWT 双令牌）；错误提示由 request.ts 拦截器统一弹出
async function onLogin() {
  loading.value = true
  try {
    const resp = await login(form.username, form.password)
    userStore.setSession(resp.accessToken, resp.userInfo.realName, resp.userInfo.permissions, resp.userInfo.dataScope)
    localStorage.setItem('crm_refresh_token', resp.refreshToken)
    router.push({ name: 'workbench' })
  } catch {
    // 错误信息已由拦截器提示，这里仅保持登录页
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a8a, #2563eb);
}
.login-card {
  width: 380px;
  border-radius: 14px;
}
.title {
  text-align: center;
  margin-bottom: 22px;
  color: #1e3a8a;
}
.btn {
  width: 100%;
  margin-top: 4px;
}
.tip {
  margin-top: 14px;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
}
</style>
