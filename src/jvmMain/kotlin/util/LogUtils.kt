package util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    override fun toString(): String = name.lowercase()
}
fun Long.formatLogTime(): String {
    val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    return date.format(formatter)
}
fun logWithLineInfo(showCaller: Boolean = false, level: LogLevel, message: String) {
    val stackTrace = Thread.currentThread().stackTrace
    val caller = if (showCaller) stackTrace[5] else stackTrace[4]
    val lineNumber = caller.lineNumber
    val methodName = caller.methodName
    val fileName = caller.fileName

//    var logMessage = "${caller.className}.$methodName() [(${fileName}:$lineNumber)]$message"
    var logMessage = "[${System.currentTimeMillis().formatLogTime()}][(${fileName}:$lineNumber):$methodName()]$message"

    when (level) {
        LogLevel.DEBUG -> println("[DEBUG] $logMessage")
        LogLevel.INFO -> println("[INFO] $logMessage")
        LogLevel.WARN -> println("[WARN] $logMessage")
        LogLevel.ERROR -> println("[ERROR] $logMessage")
    }
}

object LogUtils {


    @JvmStatic
    fun d(message: String) = logWithLineInfo(level = LogLevel.DEBUG, message = message)

    @JvmStatic
    fun logdCaller(message: String) = logWithLineInfo(true, LogLevel.DEBUG, message)

    @JvmStatic
    fun i(message: String) = logWithLineInfo(level = LogLevel.INFO, message = message)

    @JvmStatic
    fun w(message: String) = logWithLineInfo(level = LogLevel.WARN, message = message)

    @JvmStatic
    fun e(message: String) = logWithLineInfo(level = LogLevel.ERROR, message = message)

}

fun test(s: String = "", age: Int) {
    LogUtils.logdCaller("xxx")
}

fun main() {
    LogUtils.d("111")
//    LogUtils.e("111")
    test(age = 1)

}