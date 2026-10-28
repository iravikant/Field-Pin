package com.fieldpin.di

import com.fieldpin.BuildConfig
import com.fieldpin.core.NetworkMonitor
import com.fieldpin.data.camera.PhotoStamper
import com.fieldpin.data.export.GeoJsonExporter
import com.fieldpin.data.local.AppPrefs
import com.fieldpin.data.map.OfflineMapManager
import com.fieldpin.data.map.TileCache
import com.fieldpin.data.map.TileFetcher
import com.fieldpin.data.local.FieldPinDatabase
import com.fieldpin.data.local.SessionStore
import com.fieldpin.data.location.LocationTracker
import com.fieldpin.data.remote.FieldPinApi
import com.fieldpin.data.remote.MockFieldPinApi
import com.fieldpin.data.remote.MockServerStore
import com.fieldpin.data.repo.AuthRepository
import com.fieldpin.data.repo.DocumentRepository
import com.fieldpin.data.repo.DocumentStorage
import com.fieldpin.data.repo.InspectionRepository
import com.fieldpin.data.repo.PhotoStorage
import com.fieldpin.data.repo.SiteRepository
import com.fieldpin.data.repo.SyncManager
import com.fieldpin.ui.account.AccountViewModel
import com.fieldpin.ui.camera.CameraViewModel
import com.fieldpin.ui.detail.SiteDetailViewModel
import com.fieldpin.ui.draw.DrawSiteViewModel
import com.fieldpin.ui.inspection.InspectionViewModel
import com.fieldpin.ui.login.LoginViewModel
import com.fieldpin.ui.map.MapViewModel
import com.fieldpin.ui.sites.SitesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // infrastructure
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { NetworkMonitor(androidContext(), get()) }
    single { SessionStore(androidContext()) }
    single { AppPrefs(androidContext()) }
    single { FieldPinDatabase.build(androidContext()) }
    single { get<FieldPinDatabase>().siteDao() }
    single { get<FieldPinDatabase>().inspectionDao() }
    single { get<FieldPinDatabase>().siteDocumentDao() }
    single { LocationTracker(androidContext()) }
    single { PhotoStorage(androidContext()) }
    single { DocumentStorage(androidContext()) }
    single { PhotoStamper() }
    single { GeoJsonExporter(androidContext()) }

    // "backend": swap MockFieldPinApi for a real client using BuildConfig.API_BASE_URL
    single { MockServerStore(androidContext()) }
    single<FieldPinApi> { MockFieldPinApi(BuildConfig.API_BASE_URL, get(), get()) }

    // repositories
    single { SiteRepository(get(), get(), get(), get()) }
    single { InspectionRepository(get(), get(), get(), get()) }
    single { DocumentRepository(get(), get()) }
    single { AuthRepository(get(), get(), get(), get(), get(), get(), get()) }
    single { SyncManager(get(), get(), get(), get(), get(), get(), get(), get()) }

    // offline map pack
    single { TileCache(androidContext()) }
    single { TileFetcher(BuildConfig.TILE_URL_TEMPLATE, "FieldPin/${BuildConfig.VERSION_NAME} (${androidContext().packageName})") }
    single { OfflineMapManager(get(), get(), get(), get(), get(), get(), get()) }

    // view models
    viewModel { LoginViewModel(get()) }
    viewModel { SitesViewModel(get(), get(), get()) }
    viewModel { MapViewModel(get(), get(), get()) }
    viewModel { DrawSiteViewModel(get(), get()) }
    viewModel { params -> SiteDetailViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> InspectionViewModel(params.get(), get()) }
    viewModel { params -> CameraViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { AccountViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
