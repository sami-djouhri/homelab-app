package de.djouhri.cockpit.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.djouhri.cockpit.data.api.DashboardApi
import de.djouhri.cockpit.data.api.InboxApi
import de.djouhri.cockpit.data.api.OpsApi
import de.djouhri.cockpit.data.api.PairingApi
import de.djouhri.cockpit.data.api.VersionApi
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Provides
    @Singleton
    fun providePairingApi(retrofit: Retrofit): PairingApi = retrofit.create(PairingApi::class.java)

    @Provides
    @Singleton
    fun provideOpsApi(retrofit: Retrofit): OpsApi = retrofit.create(OpsApi::class.java)

    @Provides
    @Singleton
    fun provideInboxApi(retrofit: Retrofit): InboxApi = retrofit.create(InboxApi::class.java)

    @Provides
    @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi = retrofit.create(DashboardApi::class.java)

    @Provides
    @Singleton
    fun provideVersionApi(retrofit: Retrofit): VersionApi = retrofit.create(VersionApi::class.java)
}
