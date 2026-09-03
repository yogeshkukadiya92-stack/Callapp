package com.callflow.app.di

import android.content.Context
import androidx.room.Room
import com.callflow.app.BuildConfig
import com.callflow.app.core.phone.CountryAwarePhoneNumberNormalizer
import com.callflow.app.core.phone.PhoneNumberNormalizer
import com.callflow.app.core.time.DateTimeProvider
import com.callflow.app.core.time.SystemDateTimeProvider
import com.callflow.app.data.local.CallFlowDao
import com.callflow.app.data.local.CallFlowDatabase
import com.callflow.app.data.remote.CallFlowApi
import com.callflow.app.data.repository.LocalMetricsRepository
import com.callflow.app.data.repository.OfflineLeadRepository
import com.callflow.app.data.repository.OfflineCallRepository
import com.callflow.app.domain.repository.CallRepository
import com.callflow.app.data.repository.OutboxSyncRepository
import com.callflow.app.domain.repository.SyncRepository
import com.callflow.app.data.repository.OfflineFollowUpRepository
import com.callflow.app.domain.repository.FollowUpRepository
import com.callflow.app.data.repository.DefaultAuthRepository
import com.callflow.app.domain.repository.AuthRepository
import com.callflow.app.domain.repository.LeadRepository
import com.callflow.app.domain.repository.MetricsRepository
import com.callflow.app.telecom.CallIntegrationManager
import com.callflow.app.telecom.SafeDialerCallIntegrationManager
import com.callflow.app.notifications.FollowUpNotificationManager
import com.callflow.app.notifications.FollowUpReminderScheduler
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import com.callflow.app.data.remote.AccessTokenInterceptor
import com.callflow.app.data.remote.DashboardConnectorInterceptor
import com.callflow.app.data.remote.RefreshTokenAuthenticator
import com.callflow.app.data.remote.SessionRevocationInterceptor
import com.callflow.app.data.remote.RefreshTokenApi
import com.callflow.app.data.session.EncryptedSessionStore
import com.callflow.app.data.session.SessionTokenStore
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds abstract fun sessionTokenStore(value: EncryptedSessionStore): SessionTokenStore
    @Binds abstract fun leadRepository(value: OfflineLeadRepository): LeadRepository
    @Binds abstract fun metricsRepository(value: LocalMetricsRepository): MetricsRepository
    @Binds abstract fun callRepository(value: OfflineCallRepository): CallRepository
    @Binds abstract fun syncRepository(value: OutboxSyncRepository): SyncRepository
    @Binds abstract fun followUpRepository(value: OfflineFollowUpRepository): FollowUpRepository
    @Binds abstract fun authRepository(value: DefaultAuthRepository): AuthRepository
    @Binds abstract fun callIntegration(value: SafeDialerCallIntegrationManager): CallIntegrationManager
    @Binds abstract fun followUpReminderScheduler(value: FollowUpNotificationManager): FollowUpReminderScheduler
    @Binds abstract fun dateTimeProvider(value: SystemDateTimeProvider): DateTimeProvider
    @Binds abstract fun phoneNormalizer(value: CountryAwarePhoneNumberNormalizer): PhoneNumberNormalizer
}

@Module @InstallIn(SingletonComponent::class)
object AppProviders {
    @Provides @Singleton fun database(@ApplicationContext context: Context): CallFlowDatabase =
        Room.databaseBuilder(context, CallFlowDatabase::class.java, "callflow.db")
            .addMigrations(CallFlowDatabase.MIGRATION_1_2)
            .addMigrations(CallFlowDatabase.MIGRATION_2_3)
            .addMigrations(CallFlowDatabase.MIGRATION_3_4)
            .addMigrations(CallFlowDatabase.MIGRATION_4_5)
            .addMigrations(CallFlowDatabase.MIGRATION_5_6)
            .build()
    @Provides fun dao(database: CallFlowDatabase): CallFlowDao = database.dao()
    @Provides @Singleton @Named("rawHttp") fun rawHttp(connector: DashboardConnectorInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(connector).build()
    @Provides @Singleton fun refreshApi(@Named("rawHttp") client: OkHttpClient): RefreshTokenApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build().create(RefreshTokenApi::class.java)
    @Provides @Singleton fun authenticatedHttp(accessToken: AccessTokenInterceptor, connector: DashboardConnectorInterceptor, authenticator: RefreshTokenAuthenticator, revocation: SessionRevocationInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(connector).addInterceptor(accessToken).addInterceptor(revocation).authenticator(authenticator).build()
    @Provides @Singleton fun api(client: OkHttpClient): CallFlowApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build().create(CallFlowApi::class.java)
}
