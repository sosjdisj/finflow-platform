import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { clearToken, getRefreshToken, getToken, setTokens } from '@/utils/auth'
import type { ApiBody, TokenPair } from '@/types/api'

/**
 * Axios 封装：
 * - 请求拦截器注入 Bearer token；
 * - 响应拦截器统一拆包（业务方法直接拿到 data，code!=0 报错 reject）；
 * - 401 自动用 refresh token 换新令牌并重放一次原请求（并发 401 共享同一次刷新），
 *   刷新失败才清 token 回登录页。
 */
const instance = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 写操作统一携带幂等键：后端 @Idempotent 接口（如发起退款）依赖此头做重复提交保护
  if (config.method && config.method.toLowerCase() !== 'get' && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = crypto.randomUUID()
  }
  return config
})

/** 单飞刷新：并发 401 只发一次 /auth/refresh（裸 axios，避免递归经过本模块拦截器）。 */
let refreshPromise: Promise<string | null> | null = null

function refreshAccessToken(): Promise<string | null> {
  refreshPromise ??= (async () => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      return null
    }
    try {
      const resp = await axios.post<ApiBody<TokenPair>>('/api/v1/auth/refresh', { refreshToken })
      if (resp.data.code === 0) {
        const pair = resp.data.data
        setTokens(pair.accessToken, pair.refreshToken)
        return pair.accessToken
      }
      return null
    } catch {
      return null
    } finally {
      refreshPromise = null
    }
  })()
  return refreshPromise
}

/** 凭证彻底失效：清 token 回登录页（整页跳转顺带重置全部内存状态）。 */
function gotoLogin(): void {
  clearToken()
  const redirect = encodeURIComponent(window.location.pathname + window.location.search)
  window.location.href = `/login?redirect=${redirect}`
}

/** 是否 401：axios 错误响应或后端统一包里的 40100/40300 视为未认证。 */
function isUnauthorized(error: AxiosError<ApiBody<unknown>>): boolean {
  return error.response?.status === 401
}

instance.interceptors.response.use(
  (response) => {
    const body = response.data as ApiBody<unknown>
    // 统一拆包：业务方法拿到的直接是 data
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    // 业务方法第二泛型即此处返回值，故直接返回拆包数据（运行时类型以本行为准）
    return body.data as unknown as AxiosResponse
  },
  async (error: AxiosError<ApiBody<unknown>>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    if (original && isUnauthorized(error) && !original._retried && getRefreshToken()) {
      original._retried = true
      const newToken = await refreshAccessToken()
      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` }
        return instance(original)
      }
    }
    if (isUnauthorized(error)) {
      gotoLogin()
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default instance
