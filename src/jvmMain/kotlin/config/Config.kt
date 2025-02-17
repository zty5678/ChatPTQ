package config

import androidx.compose.runtime.*
import androidx.compose.ui.window.WindowPlacement
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.launch
import repository.service.UserProxy
import repository.service.retrofitService
import view.LocalAppToaster
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStreamReader

data class AppConfig(
    val enableSystemProxy: Boolean = false,
    val userProxy: UserProxy = UserProxy("0.0.0.0", 0),
    val apiKey: String = "",
    val autoStart: Boolean = false,
    val gptName: String = "小彭",
    val fastSendMode: String = FastSendMode.ShiftEnter.name,
    val fastSendLongPressDuration: Long = 300L,
)

typealias OnConfigChange = (AppConfig) -> Unit
class AppConfigContext(val appConfig: AppConfig = AppConfig(), val onConfigChange: OnConfigChange)

val LocalAppConfig = compositionLocalOf { AppConfigContext { } }

@Composable
fun AppConfig(onConfigChange: OnConfigChange? = null, App: @Composable () -> Unit) {
    var appConfig by remember { mutableStateOf(AppConfig()) }
    val coroutineScope = rememberCoroutineScope()
    val toast = LocalAppToaster.current

    remember(appConfig) {
        onConfigChange?.invoke(appConfig)
    }

    LaunchedEffect(Unit) {
        val jsonConfig = readConfig() ?: run {
            toast.toastFailure("配置文件未找到")
            return@LaunchedEffect
        }
        applyChanges(null, jsonConfig, onSuccess = {

        }, onFailure = {
            toast.toastFailure(it)
        })
        appConfig = jsonConfig
    }

    CompositionLocalProvider(LocalAppConfig provides AppConfigContext(appConfig) { new ->
        coroutineScope.launch {
            writeConfig(appConfig, new, onSuccess = {

            }, onFailure = {
                toast.toastFailure(it)
            })
            appConfig = new
        }
    }) {
        App()
    }
}

fun readConfig(): AppConfig? {
    val configFile = File("appConfig.json")
    if (!configFile.exists()) {
        if (!configFile.createNewFile()) {
            return null
        }
        var success = false
        writeConfig(null, AppConfig(), onSuccess = {
            success = true
        }, onFailure = {
            success = false
        })
        if (!success) {
            return null
        }
    }
    return try {
        Gson().fromJson(JsonReader(InputStreamReader(FileInputStream(configFile))), AppConfig::class.java)
    } catch (e:Exception) {
        e.printStackTrace()
        null
    }
}
fun writeConfig(oldConfig: AppConfig?, newConfig: AppConfig, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    try {
        val configFile = File("appConfig.json")
        if (!configFile.exists()) {
            if (!configFile.createNewFile()) {
                onFailure("配置失败")
            }
        }
        FileWriter(configFile).run {
            write(Gson().toJson(newConfig))
            close()
        }
        applyChanges(oldConfig, newConfig, onSuccess = {
            onSuccess()
        }, onFailure = onFailure)
    } catch (e: Exception) {
        e.printStackTrace()
        onFailure(e.localizedMessage)
    }
}

fun getSystemProxy(defaultVal : UserProxy):UserProxy{
    val props = System.getProperties()
    run{
        val message = props.getProperty("http.proxyHost", "")
        val port = props.getProperty("http.proxyPort", "")
        if (!message.isEmpty()){
            return UserProxy(message, port.toInt())
        }
    }
    run{
        val message = props.getProperty("https.proxyHost", "")
        val port = props.getProperty("https.proxyPort", "")
        if (!message.isEmpty()){
            return UserProxy(message, port.toInt())
        }
    }
    return defaultVal
}

fun applyChanges(oldConfig: AppConfig?, newConfig: AppConfig, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
    if (oldConfig == null || oldConfig.autoStart != newConfig.autoStart) {
        if (newConfig.autoStart) {
            onFailure("暂不支持开机启动")
        }
    }

    if (oldConfig == null || oldConfig.apiKey != newConfig.apiKey) {
        retrofitService.setApiKey(newConfig.apiKey)
    }

    if (oldConfig == null || oldConfig.userProxy != newConfig.userProxy) {
        if (!newConfig.enableSystemProxy) {
            retrofitService.setProxy(newConfig.userProxy)
        }
    }

    if (oldConfig == null || oldConfig.enableSystemProxy != newConfig.enableSystemProxy) {
        if (newConfig.enableSystemProxy) {
            retrofitService.setProxy(getSystemProxy(newConfig.userProxy))
        } else {
            retrofitService.setProxy(newConfig.userProxy)
        }
    }

    onSuccess()
}


enum class FastSendMode {
    LongPressEnter, ShiftEnter, ControlEnter, None
}

val FastSendMode.ChineseName: String
    get() = when(this) {
        FastSendMode.LongPressEnter -> "长按Enter{duration}毫秒发送//Enter换行"
        FastSendMode.ShiftEnter -> "Enter发送//Shift+Enter换行"
        FastSendMode.ControlEnter -> "Enter发送//Control+Enter换行"
        FastSendMode.None -> "无快捷键"
    }