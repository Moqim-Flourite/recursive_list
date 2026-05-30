package com.moqim.list.feature.home

import android.content.ComponentName
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object ShizukuWallpaperSetter {
    sealed class SetResult {
        data class Applied(val detail: String) : SetResult()
        data class OpenedSystemPicker(val detail: String) : SetResult()
        data class Failed(val detail: String) : SetResult()
    }

    private const val COMPONENT = "com.moqim.list/com.moqim.list.wallpaper.MoqimLiveWallpaperService"
    private const val COMPONENT_PACKAGE = "com.moqim.list"
    private const val COMPONENT_CLASS = "com.moqim.list.wallpaper.MoqimLiveWallpaperService"
    private const val DEBUG_FILE_PATH = "/sdcard/Download/moqim_list/wallpaper_debug_latest.txt"

    fun isAvailable(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun trySetLiveWallpaper(): SetResult {
        if (!isAvailable()) {
            return SetResult.Failed("Shizuku 不可用或未授权")
        }

        clearDebugLog()
        appendDebugLog("=== DIRECT_INVOKE_START ===")

        val directResult = runCatching {
            appendDebugLog("step=getServiceClass")
            val serviceManagerClass = Class.forName("android.os.ServiceManager")

            appendDebugLog("step=getServiceMethod")
            val getService = serviceManagerClass.getDeclaredMethod("getService", String::class.java)

            appendDebugLog("step=getServiceInvoke")
            val binder = getService.invoke(null, "wallpaper") ?: error("wallpaper binder == null")
            appendDebugLog("binderClass=${binder.javaClass.name}")

            appendDebugLog("step=stubClass")
            val stubClass = Class.forName("android.app.IWallpaperManager\$Stub")

            appendDebugLog("step=asInterfaceMethod")
            val asInterface = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)

            appendDebugLog("step=asInterfaceInvoke")
            val manager = asInterface.invoke(null, binder) ?: error("IWallpaperManager == null")
            appendDebugLog("managerClass=${manager.javaClass.name}")

            appendDebugLog("step=getMethod:setWallpaperComponent")
            val setWallpaperComponent = manager.javaClass.getMethod(
                "setWallpaperComponent",
                ComponentName::class.java,
            )

            val componentName = ComponentName(COMPONENT_PACKAGE, COMPONENT_CLASS)
            appendDebugLog("component=$componentName")

            appendDebugLog("step=invoke:setWallpaperComponent")
            setWallpaperComponent.invoke(manager, componentName)

            appendDebugLog("=== DIRECT_INVOKE_SUCCESS ===")
            buildString {
                appendLine("setWallpaperComponent invoke success")
                appendLine("managerClass=${manager.javaClass.name}")
                appendLine("component=$componentName")
            }
        }

        if (directResult.isFailure) {
            val error = directResult.exceptionOrNull()
            appendDebugLog("=== DIRECT_INVOKE_FAILED ===")
            appendDebugLog("${error?.javaClass?.name}: ${error?.message}")
            val target = (error as? java.lang.reflect.InvocationTargetException)?.targetException
            if (target != null) {
                appendDebugLog("targetException=${target.javaClass.name}: ${target.message}")
                target.stackTrace.forEach { appendDebugLog(it.toString()) }
            }
            error?.stackTrace?.forEach { appendDebugLog(it.toString()) }
        }

        if (directResult.isSuccess) {
            return SetResult.Applied(directResult.getOrNull().orEmpty())
        }

        appendDebugLog("=== FALLBACK_START ===")
        val outputs = mutableListOf<String>()
        reflectWallpaperManagerMethods().onSuccess { outputs += it }

        val commands = listOf(
            "am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER --es android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT $COMPONENT",
            "am start -n com.android.wallpaper.livepicker/.LiveWallpaperChange --es android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT $COMPONENT",
            "am start -n com.android.wallpaper.livepicker/.LiveWallpaperChange --es android.intent.extra.COMPONENT_NAME $COMPONENT",
        )

        var openedPicker = false
        commands.forEach { command ->
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
            process.waitFor()
            if (process.exitValue() == 0 && stdout.contains("Starting: Intent")) {
                openedPicker = true
            }
            outputs += "cmd=$command\nexit=${process.exitValue()}\nstdout=$stdout\nstderr=$stderr"
        }

        val fallbackMessage = outputs.joinToString("\n\n")
        appendDebugLog("--- fallback commands ---")
        appendDebugLog(fallbackMessage)
        appendDebugLog("=== FALLBACK_DONE ===")
        return if (openedPicker) {
            SetResult.OpenedSystemPicker(fallbackMessage)
        } else {
            SetResult.Failed(fallbackMessage)
        }
    }

    fun reflectWallpaperManagerMethods(): Result<String> {
        return runCatching {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getService = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "wallpaper") ?: error("wallpaper binder == null")

            val stubClass = Class.forName("android.app.IWallpaperManager\$Stub")
            val asInterface = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)
            val manager = asInterface.invoke(null, binder) ?: error("IWallpaperManager == null")

            val stubMethods = stubClass.declaredMethods
                .sortedBy { it.name }
                .joinToString("\n") { method ->
                    val params = method.parameterTypes.joinToString(", ") { it.name }
                    "stub ${method.name}($params) -> ${method.returnType.name}"
                }

            val managerMethods = manager.javaClass.methods
                .filter {
                    val name = it.name.lowercase()
                    name.contains("wallpaper") || name.contains("component") || name.contains("set")
                }
                .sortedBy { it.name }
                .joinToString("\n") { method ->
                    val params = method.parameterTypes.joinToString(", ") { it.name }
                    "mgr ${method.name}($params) -> ${method.returnType.name}"
                }

            buildString {
                appendLine("IWallpaperManager stub class: ${stubClass.name}")
                appendLine("IWallpaperManager impl class: ${manager.javaClass.name}")
                appendLine("--- stub methods ---")
                appendLine(stubMethods)
                appendLine("--- manager candidate methods ---")
                appendLine(managerMethods)
            }
        }
    }

    fun getDebugFilePath(): String = DEBUG_FILE_PATH

    private fun writeDebugLog(content: String) {
        runCatching {
            val file = File(DEBUG_FILE_PATH)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
    }

    private fun appendDebugLog(content: String) {
        runCatching {
            val file = File(DEBUG_FILE_PATH)
            file.parentFile?.mkdirs()
            file.appendText(content + "\n")
        }
    }

    private fun clearDebugLog() {
        runCatching {
            val file = File(DEBUG_FILE_PATH)
            file.parentFile?.mkdirs()
            file.writeText("")
        }
    }
}
