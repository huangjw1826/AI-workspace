/**
 * 通用工具函数
 */

const DEFAULT_API_BASE = "http://127.0.0.1:8000";

/**
 * Vite 开发服务器默认端口范围（5173-5180）
 * 5173 是默认端口，其他为 Vite 自动尝试的备用端口
 */
const DEV_SERVER_PORTS = new Set(
  Array.from({ length: 8 }, (_, i) => String(5173 + i))
);

/** 自动检测 API 基础地址 */
export function getApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured.replace(/\/+$/, "");

  if (typeof window !== "undefined" && DEV_SERVER_PORTS.has(window.location.port)) {
    return DEFAULT_API_BASE;
  }

  return typeof window !== "undefined" ? window.location.origin : DEFAULT_API_BASE;
}

/** 清理文件名中的危险字符（防止路径遍历和 XSS） */
export function sanitizeFilename(filename: string): string {
  if (!filename) return "";
  return filename
    .replace(/[\\/:"*?<>|]/g, "_")
    .replace(/\.\./g, "")
    .replace(/^\.+/, "")
    .trim();
}
