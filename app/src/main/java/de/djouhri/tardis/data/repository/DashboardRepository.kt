package de.djouhri.tardis.data.repository

import de.djouhri.tardis.BuildConfig
import de.djouhri.tardis.data.api.DashboardApi
import de.djouhri.tardis.data.api.VersionApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.AppVersion
import de.djouhri.tardis.data.model.tardis.DashboardSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val dashboardApi: DashboardApi,
    private val versionApi: VersionApi,
    private val demo: DemoModeManager,
) {
    suspend fun summary(): Result<DashboardSummary> {
        if (demo.isActive) return Result.success(DemoData.summary)
        return runCatching { dashboardApi.summary() }
    }

    suspend fun latestVersion(): Result<AppVersion> {
        if (demo.isActive) {
            // Im Demo-Modus gilt die App als aktuell (kein hoeherer versionCode).
            return Result.success(
                AppVersion(
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                    changelog = "",
                ),
            )
        }
        return runCatching { versionApi.appVersion() }
    }
}
