interface ApiEnvelope<T> {
  success: boolean
  errorMsg?: string
  data: T
}

export class ApiError extends Error {
  constructor(message: string, public readonly status?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = sessionStorage.getItem('token')
  if (token) headers.set('authorization', token)
  // Let the browser set the multipart boundary for FormData uploads.
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(`/api${path}`, { ...init, headers })
  } catch {
    throw new ApiError('无法连接服务器，请确认后端服务正在运行')
  }

  if (response.status === 401) {
    window.location.assign('/login.html')
    throw new ApiError('登录已过期，请重新登录', 401)
  }

  let envelope: ApiEnvelope<T>
  try {
    envelope = await response.json() as ApiEnvelope<T>
  } catch {
    throw new ApiError(response.ok ? '服务器返回了无法识别的数据' : '服务器暂时不可用', response.status)
  }

  if (!response.ok || !envelope.success) {
    throw new ApiError(envelope.errorMsg || '操作失败，请稍后重试', response.status)
  }
  return envelope.data
}

export function requireLogin(): boolean {
  if (sessionStorage.getItem('token')) return true
  window.location.assign('/login.html')
  return false
}
