/**
 * token 存取唯一入口（localStorage）。
 * access token 短效、refresh token 长效，均由本模块读写，其他代码不碰 localStorage。
 */
const ACCESS_KEY = 'finflow_token'
const REFRESH_KEY = 'finflow_refresh_token'

export function getToken(): string | null {
  return localStorage.getItem(ACCESS_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(ACCESS_KEY, token)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

/** 登录/刷新成功后成对写入。 */
export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken)
  localStorage.setItem(REFRESH_KEY, refreshToken)
}

/** 登出 / 凭证失效时清空双 token。 */
export function clearToken(): void {
  localStorage.removeItem(ACCESS_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
