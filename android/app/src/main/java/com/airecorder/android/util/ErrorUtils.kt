package com.airecorder.android.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// ============================================================
// 统一错误类型体系
// ============================================================

sealed class AppError {
    /** 设备无网络连接 */
    data object NetworkUnavailable : AppError()

    /** PC 后端不可达 */
    data class ServerUnreachable(val url: String = "") : AppError()

    /** API Token 无效 (HTTP 401/403) */
    data object AuthFailed : AppError()

    /** 服务器内部错误 (HTTP 5xx) */
    data class ServerError(val code: Int) : AppError()

    /** 请求超时 */
    data object Timeout : AppError()

    /** 未找到资源 (HTTP 404) */
    data object NotFound : AppError()

    /** 未知错误 */
    data class Unknown(val cause: Throwable? = null) : AppError()
}

/**
 * 错误 UI 状态 — 包含友好消息和操作建议
 */
data class ErrorUIState(
    val title: String,
    val message: String,
    val actionLabel: String? = null,   // 建议操作按钮文字
    val action: (() -> Unit)? = null    // 建议操作
)

/**
 * 异常 / 错误码 → AppError
 */
fun Throwable.toAppError(): AppError {
    return when {
        this is ConnectException || this is UnknownHostException ->
            AppError.ServerUnreachable()
        this is SocketTimeoutException ->
            AppError.Timeout
        this is IOException && message?.contains("403") == true ->
            AppError.AuthFailed
        this is IOException && message?.contains("404") == true ->
            AppError.NotFound
        this is IOException ->
            AppError.ServerUnreachable()
        else ->
            AppError.Unknown(this)
    }
}

/**
 * AppError → 用户可读的 ErrorUIState
 */
fun AppError.toUIState(): ErrorUIState {
    return when (this) {
        is AppError.NetworkUnavailable -> ErrorUIState(
            title = "网络不可用",
            message = "请检查手机网络连接",
            actionLabel = "打开设置",
            action = null // 调用方可注入系统设置 Intent
        )
        is AppError.ServerUnreachable -> ErrorUIState(
            title = "PC 已离线",
            message = "请确认 PC 开机且 cloudflared 正在运行",
            actionLabel = "重试",
            action = null
        )
        is AppError.AuthFailed -> ErrorUIState(
            title = "认证失败",
            message = "API Token 无效或已过期，请重新输入",
            actionLabel = "打开设置",
            action = null
        )
        is AppError.ServerError -> ErrorUIState(
            title = "服务器错误",
            message = "PC 端服务异常（错误码 ${code}），请检查后端日志",
            actionLabel = "重试",
            action = null
        )
        is AppError.Timeout -> ErrorUIState(
            title = "连接超时",
            message = "网络响应超时，请检查网络状况后重试",
            actionLabel = "重试",
            action = null
        )
        is AppError.NotFound -> ErrorUIState(
            title = "资源不存在",
            message = "请求的资源可能已被删除",
            actionLabel = "返回",
            action = null
        )
        is AppError.Unknown -> ErrorUIState(
            title = "发生错误",
            message = cause?.message ?: "未知错误",
            actionLabel = "重试",
            action = null
        )
    }
}

// ============================================================
// 旧版兼容方法
// ============================================================

object ErrorUtils {
    /**
     * 获取友好的错误消息（中文）
     */
    fun getFriendlyErrorMessage(message: String): String {
        return when {
            message.contains("502") || message.contains("503") ||
            message.contains("504") || message.contains("530") ->
                "无法连接到 PC 端服务，请检查 PC 端是否已启动并在运行"

            message.contains("401") || message.contains("403") ->
                "API Token 无效或无访问权限，请前往设置页检查 Token"

            message.contains("404") ->
                "找不到服务器接口，请检查服务器地址配置"

            message.contains("500") ->
                "服务器内部错误，请检查 PC 端日志"

            message.contains("ConnectException") ||
            message.contains("Unable to resolve host") ||
            message.contains("Failed to connect") ->
                "网络连接失败，请检查手机网络或服务器地址是否正确"

            message.contains("SocketTimeoutException") ||
            message.contains("timeout") ->
                "连接超时，请检查网络状况后重试"

            message.contains("SSL") || message.contains("certificate") ->
                "SSL 证书错误，请检查服务器地址是否使用 HTTPS"

            else -> message
        }
    }
}
