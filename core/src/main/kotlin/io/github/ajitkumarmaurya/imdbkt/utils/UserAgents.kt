package io.github.ajitkumarmaurya.imdbkt.utils

import kotlin.random.Random

data class BrowserProfile(
    val userAgent: String,
    val secChUa: String,
    val secChUaPlatform: String
)

object UserAgents {
    private val profiles = listOf(
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"Windows\""
        ),
        BrowserProfile(
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            secChUa = "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"",
            secChUaPlatform = "\"macOS\""
        ),
        BrowserProfile(
            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            secChUa = "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"",
            secChUaPlatform = "\"Linux\""
        )
    )

    fun randomProfile(): BrowserProfile = profiles[Random.nextInt(profiles.size)]
}
