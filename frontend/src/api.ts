export class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function readCookie(name: string, cookies = document.cookie): string | undefined {
  const entry = cookies
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`))
  if (!entry) return undefined
  try {
    return decodeURIComponent(entry.substring(name.length + 1))
  } catch {
    return undefined
  }
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  const method = options.method?.toUpperCase() ?? 'GET'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrf = readCookie('XSRF-TOKEN')
    if (csrf) headers.set('X-XSRF-TOKEN', csrf)
  }
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' })
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as {
      message?: string
      detail?: string
      error?: string
    } | null
    const fallback =
      response.status === 401
        ? 'Your session has ended. Sign in to continue.'
        : 'The request could not be completed. Please try again.'
    throw new ApiError(body?.detail || body?.message || body?.error || fallback, response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function errorMessage(error: unknown): string {
  if (error instanceof TypeError)
    return 'Cannot reach the server. Check that the application is running, then try again.'
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}
