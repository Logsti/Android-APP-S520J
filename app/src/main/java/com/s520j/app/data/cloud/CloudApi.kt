package com.s520j.app.data.cloud

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/** 云函数统一请求体：{ action, payload }。 */
@Serializable
data class CloudRequest(
    val action: String,
    val payload: JsonObject = JsonObject(emptyMap())
)

/** 云函数统一响应：code=0 成功，其余为业务/鉴权错误。 */
@Serializable
data class CloudResponse(
    val code: Int,
    val message: String = "",
    val data: JsonElement? = null
)

/** 保留云端业务/鉴权码，供同步流程区分鉴权、权限和暂时性失败。 */
class CloudRequestException(
    val code: Int,
    override val message: String
) : IllegalStateException(message)

/**
 * 通过 HTTP 访问服务直连云函数（路径 /api/<group>）。
 * 鉴权使用本 APP 自有的 access token，不依赖云开发登录态。
 */
interface CloudApi {

    @POST
    suspend fun call(
        @Url path: String,
        @Header("Authorization") authorization: String?,
        @Body body: CloudRequest
    ): CloudResponse
}
