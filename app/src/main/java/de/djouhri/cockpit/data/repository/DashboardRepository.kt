package de.djouhri.cockpit.data.repository

import de.djouhri.cockpit.BuildConfig
import de.djouhri.cockpit.data.api.DashboardApi
import de.djouhri.cockpit.data.api.VersionApi
import de.djouhri.cockpit.data.demo.DemoData
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.model.cockpit.AppVersion
import de.djouhri.cockpit.data.model.cockpit.DashboardSummary
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
