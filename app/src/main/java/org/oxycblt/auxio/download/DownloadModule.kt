package org.oxycblt.auxio.download

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideSongLinkClient(): SongLinkClient = SongLinkClient()

    @Provides
    @Singleton
    fun provideTidalClient(): TidalClient = TidalClient()

    @Provides
    @Singleton
    fun provideQobuzClient(): QobuzClient = QobuzClient()

    @Provides
    @Singleton
    fun provideDeezerClient(): DeezerClient = DeezerClient()

    @Provides
    @Singleton
    fun provideDownloadManager(
        songLinkClient: SongLinkClient,
        tidalClient: TidalClient,
        qobuzClient: QobuzClient,
        deezerClient: DeezerClient
    ): DownloadManager = DownloadManager(songLinkClient, tidalClient, qobuzClient, deezerClient)
}
