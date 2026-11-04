package com.mira.app.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult<out T> {
    data class Ok<T>(val value: T, val raw: String = "") : ApiResult<T>()
    data class Fail(val message: String, val updateNeeded: Boolean = false) : ApiResult<Nothing>()
}

/** Blocking calls: run them off the main thread. The device key is only ever put in the Authorization header. */
class MiraApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    fun fetchConfig(baseUrl: String): ApiResult<ApiLogic.RemoteConfig> {
        val base = ApiLogic.normalizeBaseUrl(baseUrl) ?: return ApiResult.Fail(ApiLogic.urlProblem(baseUrl) ?: "URL galat hai.")
        val req = Request.Builder().url("$base/v1/config").get().build()
        return call(req) { code, body ->
            if (code == 426) return@call ApiResult.Fail(ApiLogic.UPDATE_MESSAGE, updateNeeded = true)
            if (code != 200) return@call ApiResult.Fail(ApiLogic.httpMessage(code))
            val cfg = ApiLogic.parseConfig(body) ?: return@call ApiResult.Fail(ApiLogic.badResponseMessage())
            ApiResult.Ok(cfg, body)
        }
    }

    fun fetchToken(baseUrl: String, deviceKey: String, deviceId: String, appVersionCode: Int): ApiResult<ApiLogic.TokenInfo> {
        val base = ApiLogic.normalizeBaseUrl(baseUrl) ?: return ApiResult.Fail(ApiLogic.urlProblem(baseUrl) ?: "URL galat hai.")
        val req = Request.Builder()
            .url("$base/v1/token")
            .header("Authorization", "Bearer $deviceKey")
            .post(ApiLogic.tokenBody(deviceId, appVersionCode).toRequestBody("application/json".toMediaType()))
            .build()
        return call(req) { code, body ->
            if (code == 426) return@call ApiResult.Fail(ApiLogic.UPDATE_MESSAGE, updateNeeded = true)
            if (code != 200) return@call ApiResult.Fail(ApiLogic.httpMessage(code))
            val t = ApiLogic.parseToken(body) ?: return@call ApiResult.Fail(ApiLogic.badResponseMessage())
            ApiResult.Ok(t) // the JWT is deliberately not kept in `raw`
        }
    }

    private fun <T> call(req: Request, handle: (Int, String) -> ApiResult<T>): ApiResult<T> = try {
        client.newCall(req).execute().use { resp -> handle(resp.code, resp.body?.string().orEmpty()) }
    } catch (e: IOException) {
        ApiResult.Fail(ApiLogic.networkMessage())
    }
}
