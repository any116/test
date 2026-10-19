package com.example.c001apk.compose.util

import com.example.c001apk.compose.util.CookieUtil.SESSID
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Created by bggRGjQaUbCoE on 2024/6/3
 */
object AddCookiesInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val device = IosDeviceUtils.current()
        val builder: Request.Builder = chain.request().newBuilder()
        builder.apply {
            // 使用 header() 覆盖调用方旧的 Android 头，避免重复头影响服务端解析。
            IosHeaders.headers(device, IosDeviceUtils.nowSeconds()).forEach { (name, value) ->
                header(name, value)
            }
            header("Content-Type", "application/x-www-form-urlencoded")
            if (CookieUtil.isLogin) {
                header(
                    "Cookie",
                    "uid=${CookieUtil.uid}; username=${CookieUtil.username}; token=${CookieUtil.token}"
                )
            } else if (SESSID.isNotBlank()) {
                header("Cookie", SESSID)
            } else {
                removeHeader("Cookie")
            }
        }
        return chain.proceed(builder.build())
    }
}
