package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.domain.result.ApiError
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.data.session.SessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Thin Ktor wrapper around the Jellyfin REST API. Base URL is per-call (the
 * server is user-supplied at runtime and stored in [SessionProvider]), never
 * baked into a Ktor `defaultRequest`.
 */
class JellyfinClient(
    private val sessions: SessionProvider,
    private val appName: String,
    private val appVersion: String,
    engine: HttpClientEngine? = null,
    private val enableLogging: Boolean = false,
    private val onUnauthorized: suspend () -> Unit = defaultUnauthorizedHandler(sessions),
) {
    private val httpClient: HttpClient = if (engine != null) {
        HttpClient(engine) { configure() }
    } else {
        HttpClient(OkHttp) { configure() }
    }

    private fun HttpClientConfig<*>.configure() {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    explicitNulls = false
                    encodeDefaults = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
        if (enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                // INFO only logs method+URL today, not headers, but this is cheap insurance
                // against a future level bump (HEADERS/ALL) silently putting the session token
                // into logcat via the Authorization header.
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }
        }
    }

    suspend fun authHeader(
        includeToken: Boolean = true,
        tokenOverride: String? = null,
    ): String {
        val deviceId = sessions.deviceId()
        val token = tokenOverride ?: if (includeToken) sessions.current()?.accessToken else null
        val base = """MediaBrowser Client="$appName", Device="Android TV", DeviceId="$deviceId", Version="$appVersion""""
        return if (token != null) """$base, Token="$token"""" else base
    }

    suspend fun currentSession(): Session? = sessions.current()

    suspend fun resolveUrl(path: String, params: Map<String, Any?> = emptyMap()): String? {
        val base = sessions.serverUrlOrNull() ?: return null
        val url = buildUrl(base, path, params)
        return url.takeIf { cleartextRejectionReason(it) == null }
    }

    suspend inline fun <reified T> get(
        path: String,
        params: Map<String, Any?> = emptyMap(),
        baseUrlOverride: String? = null,
        includeToken: Boolean = true,
        invalidateSessionOnUnauthorized: Boolean = true,
    ): ApiResult<T> = when (
        val result = executeRaw(
            HttpMethod.Get,
            path,
            params,
            baseUrlOverride,
            null,
            includeToken,
            invalidateSessionOnUnauthorized,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            ApiResult.Success(result.data.body<T>())
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Serialization(e.message ?: "deserialization failed"))
        }
    }

    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        params: Map<String, Any?> = emptyMap(),
        baseUrlOverride: String? = null,
        includeToken: Boolean = true,
        invalidateSessionOnUnauthorized: Boolean = true,
    ): ApiResult<T> = when (
        val result = executeRaw(
            HttpMethod.Post,
            path,
            params,
            baseUrlOverride,
            body,
            includeToken,
            invalidateSessionOnUnauthorized,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            ApiResult.Success(result.data.body<T>())
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Serialization(e.message ?: "deserialization failed"))
        }
    }

    suspend fun postEmpty(
        path: String,
        body: Any? = null,
        params: Map<String, Any?> = emptyMap(),
        includeToken: Boolean = true,
        invalidateSessionOnUnauthorized: Boolean = true,
    ): ApiResult<Unit> = when (
        val result = executeRaw(
            HttpMethod.Post,
            path,
            params,
            null,
            body,
            includeToken,
            invalidateSessionOnUnauthorized,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> ApiResult.Success(Unit)
    }

    suspend inline fun <reified T> getWithToken(
        path: String,
        token: String,
    ): ApiResult<T> = when (
        val result = executeRaw(
            method = HttpMethod.Get,
            path = path,
            params = emptyMap(),
            baseUrlOverride = null,
            requestBody = null,
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
            tokenOverride = token,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            ApiResult.Success(result.data.body<T>())
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Serialization(e.message ?: "deserialization failed"))
        }
    }

    suspend fun postEmptyWithToken(
        path: String,
        token: String,
    ): ApiResult<Unit> = when (
        val result = executeRaw(
            method = HttpMethod.Post,
            path = path,
            params = emptyMap(),
            baseUrlOverride = null,
            requestBody = null,
            includeToken = false,
            invalidateSessionOnUnauthorized = false,
            tokenOverride = token,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> ApiResult.Success(Unit)
    }

    suspend inline fun <reified T> delete(
        path: String,
        params: Map<String, Any?> = emptyMap(),
    ): ApiResult<T> = when (
        val result = executeRaw(
            HttpMethod.Delete,
            path,
            params,
            null,
            null,
            includeToken = true,
            invalidateSessionOnUnauthorized = true,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            ApiResult.Success(result.data.body<T>())
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Serialization(e.message ?: "deserialization failed"))
        }
    }

    /**
     * DELETE with no response body to decode. Jellyfin answers 204 for the delete endpoints this
     * client uses, so there is nothing to deserialize.
     */
    suspend fun deleteEmpty(
        path: String,
        params: Map<String, Any?> = emptyMap(),
    ): ApiResult<Unit> = when (
        val result = executeRaw(
            HttpMethod.Delete,
            path,
            params,
            null,
            null,
            includeToken = true,
            invalidateSessionOnUnauthorized = true,
        )
    ) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> ApiResult.Success(Unit)
    }

    @PublishedApi
    internal suspend fun executeRaw(
        method: HttpMethod,
        path: String,
        params: Map<String, Any?>,
        baseUrlOverride: String?,
        requestBody: Any?,
        includeToken: Boolean,
        invalidateSessionOnUnauthorized: Boolean,
        tokenOverride: String? = null,
    ): ApiResult<HttpResponse> {
        val base = baseUrlOverride ?: sessions.serverUrlOrNull()
            ?: return ApiResult.Failure(ApiError.Network("No server configured"))
        val url = buildUrl(base, path, params)
        cleartextRejectionReason(url)?.let { reason ->
            return ApiResult.Failure(ApiError.Network(reason))
        }
        return try {
            val response = httpClient.request(url) {
                this.method = method
                header(HttpHeaders.Authorization, authHeader(includeToken, tokenOverride))
                if (requestBody != null) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                }
            }
            when {
                response.status.value == 401 -> {
                    if (invalidateSessionOnUnauthorized) handleUnauthorized()
                    ApiResult.Failure(ApiError.Unauthorized)
                }
                !response.status.isSuccess() -> ApiResult.Failure(
                    ApiError.Http(response.status.value, response.status.description),
                )
                else -> ApiResult.Success(response)
            }
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 401) {
                if (invalidateSessionOnUnauthorized) handleUnauthorized()
                ApiResult.Failure(ApiError.Unauthorized)
            } else {
                ApiResult.Failure(ApiError.Http(e.response.status.value, e.message))
            }
        } catch (e: ServerResponseException) {
            ApiResult.Failure(ApiError.Http(e.response.status.value, e.message))
        } catch (e: HttpRequestTimeoutException) {
            ApiResult.Failure(ApiError.Network(e.message ?: "request timed out"))
        } catch (e: IOException) {
            ApiResult.Failure(ApiError.Network(e.message ?: "network error"))
        } catch (e: SerializationException) {
            ApiResult.Failure(ApiError.Serialization(e.message ?: "serialization error"))
        } catch (e: Exception) {
            ApiResult.Failure(ApiError.Unknown(e.message ?: "unknown error"))
        }
    }

    /**
     * Any authenticated 401 means the stored token was revoked or expired. Clear the
     * credentials and notify the UI that the session ended.
     *
     * Only fires when we actually sent a token. A 401 from
     * `AuthenticateByName` is "wrong password", not "session died", and must not
     * kick the user off the Login screen they are already standing on.
     */
    private suspend fun handleUnauthorized() {
        if (sessions.current()?.accessToken == null) return
        onUnauthorized()
    }

    @PublishedApi
    internal fun buildUrl(base: String, path: String, params: Map<String, Any?>): String {
        val urlBuilder = URLBuilder(base + path)
        params.forEach { (key, value) ->
            if (value == null) return@forEach
            val str = if (value is List<*>) value.joinToString(",") else value.toString()
            urlBuilder.parameters.append(key, str)
        }
        return urlBuilder.buildString()
    }
}

/**
 * Default 401 reaction: wipe the persisted credentials.
 *
 * Production injects `SessionManager::expireSession`, which also publishes the lifecycle event.
 * The fallback keeps isolated clients and tests source-compatible by clearing concrete stores.
 */
internal fun defaultUnauthorizedHandler(sessions: SessionProvider): suspend () -> Unit = {
    sessions.invalidate()
}
