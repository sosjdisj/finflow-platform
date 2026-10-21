<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { CaptchaInfo } from '@/types/api'

/** 登录页：图形验证码 + 双 token 登录，成功后加载资料进工作台。 */
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const captcha = ref<CaptchaInfo | null>(null)
const form = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha(): Promise<void> {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

onMounted(refreshCaptcha)

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !captcha.value) {
    return
  }
  submitting.value = true
  try {
    const pair = await authApi.login({
      username: form.username.trim(),
      password: form.password,
      captchaId: captcha.value.captchaId,
      captchaCode: form.captchaCode.trim(),
    })
    await userStore.loginAfterSuccess(pair.accessToken, pair.refreshToken)
    ElMessage.success('登录成功')
    await router.replace(redirectTarget(route.query.redirect))
  } catch {
    // 验证码一次一用：无论失败原因都换一张，避免旧码已被消费导致连环失败；
    // 资料加载失败会留下「有 token 无资料」的半登录态，这里一并清掉
    userStore.reset()
    await refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

/** 登录后跳转目标：只接受站内绝对路径（防 //外站 open redirect 与编码畸形值），其余回首页。 */
function redirectTarget(raw: unknown): string {
  return typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') ? raw : '/'
}
</script>

<template>
  <div class="login">
    <el-card class="box" header="FinFlow 管理平台">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="不区分大小写" maxlength="4" />
            <img
              v-if="captcha"
              class="captcha-img"
              :src="captcha.imageBase64"
              alt="验证码"
              title="点击刷新"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-button class="submit" type="primary" :loading="submitting" native-type="button" @click="onSubmit">
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--el-fill-color-light);
}

.box {
  width: 380px;
}

.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.captcha-img {
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
}

.submit {
  width: 100%;
}
</style>
