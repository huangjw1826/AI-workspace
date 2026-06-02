/**
 * AI Recorder 通用工具函数
 *
 * 提供前端应用常用的辅助函数：
 * - API 基础地址自动检测（开发/生产模式自适应）
 * - 文件名安全清理（防止路径遍历和 XSS）
 */

/** 后端 API 默认地址（开发模式使用） */
const DEFAULT_API_BASE = "http://127.0.0.1:8000";

/**
 * Vite 开发服务器默认端口范围（5173-5180）
 * 5173 是默认端口，5180 是 Vite 自动尝试的最大备用端口
 */
const DEV_SERVER_PORTS = new Set(
  Array.from({ length: 8 }, (_, i) => String(5173 + i))
);

/**
 * 自动检测后端 API 的基础 URL
 *
 * 检测优先级：
 * 1. Vite 环境变量 VITE_API_BASE_URL（手动配置）
 * 2. 开发模式（端口在 5173-5180 之间）：使用 localhost:8000
 * 3. 生产模式：使用当前页面的 origin（同源）
 *
 * @returns 去掉尾部斜杠的 API 基础 URL
 */
export function getApiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  if (configured) return configured.replace(/\/+$/, "");

  if (typeof window !== "undefined" && DEV_SERVER_PORTS.has(window.location.port)) {
    return DEFAULT_API_BASE;
  }

  return typeof window !== "undefined" ? window.location.origin : DEFAULT_API_BASE;
}

/**
 * 清理文件名中的危险字符
 *
 * 移除或替换以下不安全字符：
 * - Windows 文件名非法字符：\ / : * ? < > |
 * - 路径遍历序列：..
 * - 隐藏文件前缀：.
 *
 * @param filename 原始文件名
 * @returns 清理后的安全文件名
 */
export function sanitizeFilename(filename: string): string {
  if (!filename) return "";
  return filename
    .replace(/[\\/:"*?<>|]/g, "_")
    .replace(/\.\./g, "")
    .replace(/^\.+/, "")
    .trim();
}
