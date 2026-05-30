package com.airecorder.android.util

object ErrorUtils {
    fun getFriendlyErrorMessage(message: String): String {
        return when {
            message.contains("502") || message.contains("503") || message.contains("504") || message.contains("530") -> 
                "无法连接到 PC 端服务，请检查 PC 端是否已启动并在运行 (50x/530)"
            message.contains("401") || message.contains("403") -> 
                "API Token 无效或无访问权限 (401/403)"
            message.contains("404") -> 
                "找不到服务器接口，请检查服务器地址配置 (404)"
            message.contains("500") -> 
                "服务器内部错误，请检查 PC 端日志 (500)"
            message.contains("ConnectException") || message.contains("Unable to resolve host") || message.contains("Failed to connect") -> 
                "网络连接失败，请检查手机网络或服务器地址是否正确"
            message.contains("SocketTimeoutException") || message.contains("timeout") -> 
                "连接超时，请检查网络状况"
            else -> message
        }
    }
}
