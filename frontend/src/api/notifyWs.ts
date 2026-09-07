import { ref } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * RTP-WS 实时通知 WebSocket 客户端（RTP-DV-02/03）
 * - WS 优先：登录后连接 /ws/notify?token=，服务端推送 NOTIFY 消息
 * - 断线自动重连：指数退避（1s→2s→…→30s 封顶），连接成功后重置
 * - 轮询兜底（RTP-DV-01 保留）：connected=false 期间由调用方恢复 5s 轮询
 */

export interface NotifyEvent {
  id: number
  msgType: string
  title: string
  content: string
  relType: string
  relId: number
  read: boolean
  createdAt: string
}

const MAX_RETRY = 6 // 连续失败上限（token 过期等场景），兜底轮询接管

export function useNotifyWs(onNotify: (n: NotifyEvent) => void) {
  const connected = ref(false)
  let ws: WebSocket | null = null
  let retry = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined
  let manuallyClosed = false

  function connect() {
    const token = useUserStore().token
    if (!token || ws || manuallyClosed) return
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    try {
      ws = new WebSocket(`${proto}://${location.host}/ws/notify?token=${encodeURIComponent(token)}`)
    } catch {
      ws = null
      scheduleReconnect()
      return
    }
    ws.onopen = () => {
      connected.value = true
      retry = 0 // 成功连接后重置退避
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data as string)
        if (msg?.type === 'NOTIFY') {
          onNotify(msg as NotifyEvent)
        }
        // CONNECTED 心跳消息无需处理
      } catch {
        // 非 JSON 消息忽略
      }
    }
    ws.onclose = () => {
      connected.value = false
      ws = null
      if (!manuallyClosed) scheduleReconnect()
    }
    ws.onerror = () => {
      // onclose 会随后触发，统一在 onclose 处理重连
    }
  }

  function scheduleReconnect() {
    if (manuallyClosed || retry >= MAX_RETRY) return
    const delay = Math.min(1000 * 2 ** retry, 30_000)
    retry += 1
    reconnectTimer = setTimeout(connect, delay)
  }

  function close() {
    manuallyClosed = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
  }

  return { connected, connect, close }
}
