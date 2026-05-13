package io.github.ajitkumarmaurya.imdbkt.sample.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ajitkumarmaurya.imdbkt.Imdb
import io.github.ajitkumarmaurya.imdbkt.ImdbConfig
import io.github.ajitkumarmaurya.imdbkt.sample.BuildConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideImdbConfig(@ApplicationContext context: Context): ImdbConfig =
        ImdbConfig(
            cacheDir = context.cacheDir,
            enableLogging = BuildConfig.DEBUG,
            firecrawlApiKey = BuildConfig.FIRECRAWL_API_KEY.takeIf { it.isNotBlank() },
        )

    @Provides
    @Singleton
    fun provideImdb(config: ImdbConfig): Imdb = Imdb(config)
}
