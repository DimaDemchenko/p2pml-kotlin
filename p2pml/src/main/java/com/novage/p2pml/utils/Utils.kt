package com.novage.p2pml.utils

import com.novage.p2pml.Constants
import com.novage.p2pml.Constants.HTTP_PREFIX
import com.novage.p2pml.Constants.LOCALHOST
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import okhttp3.Request

internal object Utils {
    fun replaceToProxyUrl(
        manifest: String,
        baseManifestUrl: String,
        originalUrl: String,
        updatedManifestBuilder: StringBuilder,
        serverPort: Int,
        queryParam: String? = null,
    ) {
        val urlToFind = findUrlInManifest(manifest, originalUrl, baseManifestUrl)
        val absoluteUrl =
            getAbsoluteUrl(baseManifestUrl, originalUrl).encodeURLQueryComponent()
        val newUrl =
            if (queryParam != null) {
                getUrl(serverPort, "$queryParam$absoluteUrl")
            } else {
                absoluteUrl
            }

        val startIndex =
            updatedManifestBuilder
                .indexOf(urlToFind)
                .takeIf { it != -1 }
                ?: throw IllegalStateException("URL not found in manifest: $originalUrl")
        val endIndex = startIndex + urlToFind.length
        updatedManifestBuilder.replace(startIndex, endIndex, newUrl)
    }

    fun findUrlInManifest(
        manifest: String,
        urlToFind: String,
        manifestUrl: String,
    ): String {
        val baseManifestURL = manifestUrl.substringBeforeLast("/") + "/"
        val relativeUrlToFind = urlToFind.removePrefix(baseManifestURL)

        return when {
            manifest.contains(urlToFind) -> urlToFind
            manifest.contains(relativeUrlToFind) -> relativeUrlToFind
            else -> throw IllegalStateException(
                "URL not found in manifest. urlToFind:" +
                        "$urlToFind, manifestUrl: $manifestUrl",
            )
        }
    }

    fun getAbsoluteUrl(
        baseManifestUrl: String,
        mediaUri: String,
    ): String {
        if (mediaUri.startsWith(HTTP_PREFIX) || mediaUri.startsWith(Constants.HTTPS_PREFIX)) {
            return mediaUri
        }

        var baseUrl = baseManifestUrl.substringBeforeLast("/")
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }

        return "$baseUrl$mediaUri"
    }

    fun encodeUrlToBase64(url: String): String = url.encodeBase64().encodeURLParameter()

    fun decodeBase64Url(encodedString: String): String = encodedString.decodeBase64String().decodeURLQueryComponent()

    fun getUrl(
        port: Int,
        path: String,
    ): String = "$HTTP_PREFIX$LOCALHOST:$port/$path"

    fun copyHeaders(
        call: ApplicationCall,
        requestBuilder: Request.Builder,
    ) {
        val excludedHeaders =
            setOf(
                "Host",
                "Connection",
                "Transfer-Encoding",
                "Expect",
                "Upgrade",
                "Proxy-Connection",
                "Keep-Alive",
                "Accept-Encoding",
            )

        for (headerName in call.request.headers.names()) {
            if (headerName !in excludedHeaders) {
                val headerValues = call.request.headers.getAll(headerName)
                if (headerValues != null) {
                    for (headerValue in headerValues) {
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                }
            }
        }
    }
}
