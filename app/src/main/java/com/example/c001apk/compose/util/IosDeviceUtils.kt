package com.example.c001apk.compose.util

import android.content.Context
import com.example.c001apk.compose.c001Application
import java.util.Locale
import java.util.UUID

/**
 * 持久化 Coolapk iOS 设备身份，并负责 UUID/CDID 的生命周期切换。
 *
 * SharedPreferences 用于身份是有意的：拦截器需要在同步请求头生成阶段读取当前身份，
 * 不应依赖异步 DataStore 或 Compose 生命周期。
 */
object IosDeviceUtils {

    private const val PREFS_NAME = "ios_device_identity"
    private const val KEY_ID = "device_id"
    private const val KEY_CDID = "cdid"
    private const val KEY_IDFV = "idfv"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_MODEL = "model"
    private const val KEY_HARDWARE_ID = "hardware_id"
    private const val KEY_SALES_MODEL = "sales_model"
    private const val KEY_SYSTEM_VERSION = "system_version"

    private val lock = Any()

    @Volatile
    private var cached: IosDevice? = null

    fun current(): IosDevice {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            return loadOrCreate().also { cached = it }
        }
    }

    fun deviceId(): String = current().id

    fun hasCdid(): Boolean = prefs.getString(KEY_CDID, "").orEmpty().isNotBlank()

    /** 只重新生成机型档案，保留 UUID/CDID。 */
    fun regenerate(): IosDevice = synchronized(lock) {
        createAndPersist(current().id).also { cached = it }
    }

    /** 更新 iOS 机型信息，设备身份本身不变。 */
    fun updateProfile(
        model: String = current().model,
        hardwareId: String = current().hardwareId,
        salesModel: String = current().salesModel,
        systemVersion: String = current().systemVersion,
    ): IosDevice = synchronized(lock) {
        val base = current()
        val updated = IosDevice(
            id = base.id,
            model = sanitize(model).ifBlank { base.model },
            hardwareId = sanitize(hardwareId).ifBlank { base.hardwareId },
            salesModel = sanitize(salesModel),
            systemVersion = sanitize(systemVersion).ifBlank { base.systemVersion },
        )
        persist(updated)
        cached = updated
        updated
    }

    /** 登录成功后切换到服务端登记的 CDID。 */
    fun setCdid(cdid: String): IosDevice = synchronized(lock) {
        val value = cdid.trim()
        require(value.isNotEmpty()) { "CDID 不能为空" }
        prefs.edit().putString(KEY_CDID, value).apply()
        val updated = current().copy(id = value)
        persist(updated)
        cached = updated
        updated
    }

    /** 清理当前 CDID 并回到新的匿名 UUID。 */
    fun resetDeviceId(): IosDevice = synchronized(lock) {
        prefs.edit()
            .remove(KEY_CDID)
            .remove(KEY_IDFV)
            .remove(KEY_INSTALL_ID)
            .apply()
        val updated = current().copy(id = newRandomUuid())
        persist(updated)
        cached = updated
        updated
    }

    /** 主动退出或登录失效时调用，避免匿名请求继续复用旧账号的 CDID。 */
    fun onLogout(): IosDevice = synchronized(lock) {
        prefs.edit().remove(KEY_CDID).apply()
        val updated = current().copy(id = newRandomUuid())
        persist(updated)
        cached = updated
        updated
    }

    fun idfv(): String = synchronized(lock) {
        prefs.getString(KEY_IDFV, "").orEmpty().ifBlank {
            newRandomUuid().also { prefs.edit().putString(KEY_IDFV, it).apply() }
        }
    }

    fun installId(): String = synchronized(lock) {
        prefs.getString(KEY_INSTALL_ID, "").orEmpty().ifBlank {
            newRandomUuid().also { prefs.edit().putString(KEY_INSTALL_ID, it).apply() }
        }
    }

    fun appDevice(device: IosDevice = current()): String = IosHeaders.appDevice(device)

    fun userAgent(device: IosDevice = current()): String = IosHeaders.userAgent(device)

    fun appToken(
        timestampSeconds: Long = nowSeconds(),
        device: IosDevice = current(),
    ): String = IosHeaders.appToken(device, timestampSeconds)

    fun headers(
        timestampSeconds: Long = nowSeconds(),
        device: IosDevice = current(),
    ): Map<String, String> = IosHeaders.headers(device, timestampSeconds)

    fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

    private val prefs
        get() = c001Application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadOrCreate(): IosDevice {
        val storedId = prefs.getString(KEY_ID, "").orEmpty()
        val cdid = prefs.getString(KEY_CDID, "").orEmpty()
        val id = cdid.ifBlank {
            storedId.ifBlank { newRandomUuid() }
        }
        val model = prefs.getString(KEY_MODEL, "").orEmpty()
        val hardwareId = prefs.getString(KEY_HARDWARE_ID, "").orEmpty()
        val systemVersion = prefs.getString(KEY_SYSTEM_VERSION, "").orEmpty()
        if (model.isNotBlank() && hardwareId.isNotBlank() && systemVersion.isNotBlank()) {
            val device = IosDevice(
                id = id,
                model = model,
                hardwareId = hardwareId,
                salesModel = prefs.getString(KEY_SALES_MODEL, "").orEmpty(),
                systemVersion = systemVersion,
            )
            if (storedId != id) persist(device)
            return device
        }
        return createAndPersist(id)
    }

    private fun createAndPersist(existingId: String): IosDevice {
        val profile = PROFILES.random()
        return IosDevice(
            id = existingId,
            model = profile.model,
            hardwareId = profile.hardwareId,
            salesModel = profile.salesModel,
            systemVersion = VERSIONS.random(),
        ).also { persist(it) }
    }

    private fun persist(device: IosDevice) {
        prefs.edit()
            .putString(KEY_ID, device.id)
            .putString(KEY_MODEL, device.model)
            .putString(KEY_HARDWARE_ID, device.hardwareId)
            .putString(KEY_SALES_MODEL, device.salesModel)
            .putString(KEY_SYSTEM_VERSION, device.systemVersion)
            .apply()
    }

    private fun newRandomUuid(): String = UUID.randomUUID().toString().uppercase(Locale.US)

    private fun sanitize(value: String): String = value.trim().replace(";", "")

    private data class Profile(
        val model: String,
        val hardwareId: String,
        val salesModel: String,
    )

    private val PROFILES = listOf(
        Profile("iPhone 15", "iPhone15,4", "MTLD3CH/A"),
        Profile("iPhone 15", "iPhone15,4", "MTLG3CH/A"),
        Profile("iPhone 16", "iPhone17,3", "MYEV3CH/A"),
        Profile("iPhone 16", "iPhone17,3", "MYEW3CH/A"),
        Profile("iPhone 17", "iPhone18,3", "MG6W4CH/A"),
        Profile("iPhone 17", "iPhone18,3", "MG704CH/A"),
    )

    private val VERSIONS = listOf("26.7", "26.7.1", "27.0.1")
}
