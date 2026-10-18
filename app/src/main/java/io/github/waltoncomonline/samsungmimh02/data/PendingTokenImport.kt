package io.github.waltoncomonline.samsungmimh02.data

import android.content.Context
import org.json.JSONObject

internal object PendingTokenImport {
    private const val FILE_NAME = "pending-device-token.json"
    private val protocolValue = Regex("[A-Za-z0-9._:-]{1,256}")

    fun stage(context: Context, host: String, token: String) {
        require(HOST.matches(host)) { "Invalid controller host" }
        require(protocolValue.matches(token)) { "Invalid device token" }
        val contents = JSONObject()
            .put("host", host)
            .put("token", token)
            .toString()
        context.filesDir.resolve(FILE_NAME).writeText(contents)
    }

    fun consume(context: Context): String? {
        val pendingFile = context.filesDir.resolve(FILE_NAME)
        if (!pendingFile.isFile) return null
        return try {
            pendingFile.readText()
        } finally {
            pendingFile.delete()
        }
    }

    private val HOST = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
}
