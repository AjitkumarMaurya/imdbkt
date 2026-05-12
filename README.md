# imdb-kt

[![CI](https://github.com/AjitkumarMaurya/imdbkt/actions/workflows/ci.yml/badge.svg)](https://github.com/AjitkumarMaurya/imdbkt/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ajitkumarmaurya/imdb-kt)](https://central.sonatype.com/artifact/io.github.ajitkumarmaurya/imdb-kt)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-purple.svg)](https://kotlinlang.org)

A production-ready Kotlin library that crawls and parses IMDb metadata directly from IMDb web pages — **no API key required**.

> **Note:** This library is for educational and personal use. Review IMDb's Terms of Service before deploying in production.

---

## Features

- **Search** — movies, TV series, anime, episodes, people via IMDb's suggestion API
- **Title Details** — full metadata for any movie or series (rating, cast, directors, genres, runtime, box office…)
- **Actor / Person** — bio, filmography, known-for titles
- **Trending** — parse IMDb chart pages (moviemeter, tvmeter, Top 250, box office)
- **Season Episodes** — episode list with ratings and air dates
- **Tiered Cache** — in-memory + optional disk cache with configurable TTL
- **Coroutines** — all public APIs are `suspend` functions
- **Resilient** — automatic retries with exponential back-off, user-agent rotation, rate limiting
- **Clean Architecture** — Repository pattern, isolated parser layer, centralised CSS selectors

---

## Installation

### Maven Central (stable)

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.ajitkumarmaurya:imdb-kt:1.0.0")
}
```

For Android projects also add:
```kotlin
android {
    packagingOptions {
        resources.excludes += "META-INF/INDEX.LIST"
    }
}
```

### JitPack (snapshot / latest commit)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.AjitkumarMaurya:imdbkt:main-SNAPSHOT")
}
```

---

## Quick Start

```kotlin
import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.model.ImdbResult
import io.github.ajitkumarmaurya.imdbkt.model.TrendingType

val imdb = Imdb()

// ── Search ─────────────────────────────────────────────────────────────────
val results = imdb.search("Interstellar")
results.onSuccess { items ->
    items.forEach { println("${it.title} (${it.year}) — ${it.imdbId}") }
}

// ── Title details ──────────────────────────────────────────────────────────
val movie = imdb.getTitle("tt0816692")
movie.onSuccess { title ->
    println(title.title)       // Interstellar
    println(title.rating)      // 8.7
    println(title.genres)      // [Adventure, Drama, Sci-Fi]
    println(title.cast.first().name) // Matthew McConaughey
}

// ── Series ─────────────────────────────────────────────────────────────────
val series = imdb.getTitle("tt5753856")   // Dark
series.onSuccess { println("Seasons: ${it.seasons}") }

// ── Season episodes ────────────────────────────────────────────────────────
val episodes = imdb.getSeasonEpisodes("tt5753856", season = 1)
episodes.onSuccess { season ->
    season.episodes.forEach { ep ->
        println("S${ep.season}E${ep.episode} — ${ep.title} (★${ep.rating})")
    }
}

// ── Actor ──────────────────────────────────────────────────────────────────
val actor = imdb.getActor("nm0000190")
actor.onSuccess { a ->
    println("${a.name} — born ${a.birthDate} in ${a.birthPlace}")
    println("Known for: ${a.knownFor.map { it.title }}")
}

// ── Trending ───────────────────────────────────────────────────────────────
val trending = imdb.getTrending(TrendingType.TOP_250_MOVIES)
trending.onSuccess { list ->
    list.take(5).forEachIndexed { i, item ->
        println("${i + 1}. ${item.title}")
    }
}

imdb.close()
```

---

## Configuration

```kotlin
val imdb = Imdb(
    ImdbConfig(
        connectTimeoutSeconds  = 15,
        readTimeoutSeconds     = 30,
        maxRequestsPerSecond   = 2,    // rate limiter
        maxRetries             = 3,    // exponential back-off
        enableLogging          = BuildConfig.DEBUG,
        cacheDir               = context.cacheDir,   // enables disk cache (Android)
        memoryCacheTtlMs       = 10 * 60_000L,       // 10 min
        diskCacheTtlMs         = 60 * 60_000L,       // 1 hour
    )
)
```

---

## Error Handling

All methods return `ImdbResult<T>` — a sealed class with three states:

```kotlin
when (val result = imdb.getTitle("tt0816692")) {
    is ImdbResult.Success -> { /* result.data: ImdbTitle */ }
    is ImdbResult.Error   -> { /* result.message, result.type (NETWORK / PARSING / NOT_FOUND / RATE_LIMITED) */ }
    ImdbResult.Empty      -> { /* search returned no results */ }
}
```

---

## Data Models

### `ImdbTitle`
| Field | Type | Description |
|---|---|---|
| `imdbId` | String | e.g. `tt0816692` |
| `title` | String | Display title |
| `originalTitle` | String? | Original language title |
| `type` | TitleType | MOVIE, TV_SERIES, TV_EPISODE… |
| `year` | String? | Start year |
| `rating` | Float? | IMDb aggregate rating |
| `voteCount` | Long? | Total votes |
| `genres` | List\<String\> | Genre list |
| `runtimeMinutes` | Int? | Runtime in minutes |
| `cast` | List\<CastMember\> | Top billed cast |
| `directors` | List\<Credit\> | Director credits |
| `seasons` | Int? | Season count (series only) |
| `poster` | String? | Poster image URL |
| `boxOffice` | BoxOffice? | Budget / gross figures |

### `ImdbSearchItem`
| Field | Type |
|---|---|
| `imdbId` | String |
| `title` | String |
| `year` | String? |
| `type` | TitleType |
| `poster` | String? |
| `subtitle` | String? |

---

## Architecture

```
core/
├── Imdb.kt                  # Public entry point
├── ImdbConfig.kt            # Configuration
├── model/                   # Immutable data classes + ImdbResult sealed class
├── network/
│   ├── HttpClient.kt        # OkHttp wrapper with browser headers
│   ├── RetryInterceptor.kt  # Exponential back-off retry
│   └── RateLimiter.kt       # Token-bucket rate limiter
├── parser/
│   ├── selectors/
│   │   └── ImdbSelectors.kt # ALL CSS selectors and JSON paths in one place
│   ├── SearchParser.kt      # IMDb suggestion API
│   ├── TitleParser.kt       # __NEXT_DATA__ + JSON-LD + Jsoup fallback
│   ├── ActorParser.kt       # Person pages
│   ├── TrendingParser.kt    # Chart pages
│   └── EpisodeParser.kt     # Season episode lists
├── repository/
│   ├── ImdbRepository.kt    # Interface
│   └── ImdbRepositoryImpl.kt
├── cache/
│   ├── Cache.kt             # Interface
│   ├── MemoryCache.kt       # LRU in-memory cache
│   ├── DiskCache.kt         # File-based persistent cache
│   └── TieredCache.kt       # Memory → Disk read-through
└── utils/
    ├── Extensions.kt        # JsonElement traversal helpers
    └── UserAgents.kt        # Browser user-agent pool
```

---

## Parsing Strategy

IMDb uses Next.js. Each page embeds a `__NEXT_DATA__` JSON script tag with rich structured data. The library:

1. **Extracts `__NEXT_DATA__`** — primary source, most complete
2. **Falls back to JSON-LD** — `<script type="application/ld+json">`, structured and reliable
3. **Falls back to Jsoup CSS selectors** — defined in `ImdbSelectors.kt` (update here when IMDb changes HTML)

When IMDb updates its HTML, update **only `ImdbSelectors.kt`** to fix all parsers.

---

## Publishing to Maven Central

### Prerequisites

1. A [Sonatype Central Portal](https://central.sonatype.com) account
2. A GPG key pair for signing
3. `gradle.properties` configured with your group ID, developer info

### Steps

```bash
# 1. Set credentials in ~/.gradle/gradle.properties (never commit these)
mavenCentralUsername=your_sonatype_username
mavenCentralPassword=your_sonatype_password
signingInMemoryKey=<base64 encoded private key>
signingInMemoryKeyId=<key ID last 8 chars>
signingInMemoryKeyPassword=<key passphrase>

# 2. Publish
./gradlew :core:publishAndReleaseToMavenCentral
```

### GitHub Actions

Create these repository secrets:

| Secret | Description |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype password |
| `SIGNING_KEY` | Base64-encoded GPG private key |
| `SIGNING_KEY_ID` | Last 8 chars of GPG key ID |
| `SIGNING_KEY_PASSWORD` | GPG key passphrase |

Then publish by creating a GitHub Release — the workflow triggers automatically.

---

## Running the JVM Sample

```bash
./gradlew :sample-jvm:run
```

---

## Running Tests

```bash
./gradlew :core:test
```

---

## Contribution Guide

1. Fork → feature branch (`git checkout -b feature/my-feature`)
2. Run tests: `./gradlew :core:test`
3. Run detekt: `./gradlew :core:detekt`
4. Submit a PR

**When IMDb changes its HTML:**
- Run the test suite to identify failing parsers
- Update selectors in [`core/src/main/kotlin/.../parser/selectors/ImdbSelectors.kt`](core/src/main/kotlin/io/github/username/imdbkt/parser/selectors/ImdbSelectors.kt)
- Add/update HTML fixture files in `core/src/test/resources/`

---

## License

```
Copyright 2024 Ajit Kumar Maurya

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0
```
