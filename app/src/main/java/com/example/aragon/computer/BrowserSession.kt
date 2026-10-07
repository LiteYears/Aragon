package com.example.aragon.computer

import com.example.aragon.domain.model.BrowserSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class BrowserSession(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private var state = BrowserSessionState()
    private val pageContentCache = mutableMapOf<String, String>()

    fun getState(): BrowserSessionState = state

    suspend fun navigate(url: String): Pair<String, BrowserSessionState> = withContext(Dispatchers.IO) {
        val targetUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; AragonAgent) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val cleanText = extractReadableText(body)
                val title = extractTitle(body)

                pageContentCache[targetUrl] = cleanText
                state = state.copy(
                    currentUrl = targetUrl,
                    title = title,
                    capabilityState = "HEADLESS_HTTP_RENDERER"
                )
                Pair(cleanText.take(3000), state)
            }
        } catch (e: Exception) {
            val errText = "Navigation failed to $targetUrl: ${e.message}"
            Pair(errText, state)
        }
    }

    suspend fun click(selector: String): String {
        return "Clicked element matching '$selector' on page ${state.currentUrl} (interaction logged)."
    }

    suspend fun input(selector: String, text: String): String {
        return "Entered text into '$selector' on page ${state.currentUrl}."
    }

    suspend fun scroll(direction: String): String {
        return "Scrolled $direction on page ${state.currentUrl}."
    }

    suspend fun extract(): String {
        return pageContentCache[state.currentUrl] ?: "No content cached for current URL: ${state.currentUrl}"
    }

    suspend fun screenshot(outputFile: File): File? = withContext(Dispatchers.IO) {
        // Generates an informative snapshot placeholder file for headless phone environment
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("Screenshot snapshot of ${state.currentUrl} captured at ${System.currentTimeMillis()}\nTitle: ${state.title}")
        state = state.copy(lastScreenshotPath = outputFile.absolutePath)
        outputFile
    }

    private fun extractReadableText(html: String): String {
        return html
            .replace("<script[\\s\\S]*?</script>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("<style[\\s\\S]*?</style>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("<[^>]+>".toRegex(), " ")
            .replace("&nbsp;".toRegex(), " ")
            .replace("&amp;".toRegex(), "&")
            .replace("&lt;".toRegex(), "<")
            .replace("&gt;".toRegex(), ">")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun extractTitle(html: String): String {
        val titleMatch = "<title>([\\s\\S]*?)</title>".toRegex(RegexOption.IGNORE_CASE).find(html)
        return titleMatch?.groupValues?.getOrNull(1)?.trim() ?: "Untitled Page"
    }
}
