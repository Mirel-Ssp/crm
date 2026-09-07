# INF-DV-03 端到端验证脚本（在 PowerShell 交互窗口执行，或由 Agent 在应用启动后逐步执行）
# 1) 无 token 访问受保护接口 -> 期望 401 JSON (code 40100)
# 2) 错误密码登录 -> 期望 code 40100 "用户名或密码错误"
# 3) 正确登录 -> 期望 code 0 + accessToken/refreshToken
# 4) 带 token 访问 /api/auth/me -> 期望 realName=系统管理员
# 5) 刷新令牌 -> 期望新的 accessToken
