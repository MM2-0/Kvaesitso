package de.mm20.launcher2.data.comms

import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.search.VirtualAppProvider
import de.mm20.launcher2.search.VirtualAppDeserializer
import de.mm20.launcher2.search.SearchableDeserializer
import de.mm20.launcher2.data.comms.radio.RadioBrowserClient
import de.mm20.launcher2.data.comms.radio.RadioRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataCommsModule = module {
    single<ContactDirectoryRepository> { ContactDirectoryRepositoryImpl(androidContext(), get()) }
    single<CallLogRepository> { CallLogRepositoryImpl(androidContext(), get()) }
    single<de.mm20.launcher2.comms.repository.SpamRepository> { SpamRepositoryImpl(androidContext()) }

    single {
        HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            engine {
                config { dns(de.mm20.launcher2.data.comms.radio.FallbackDns) }
            }
            install(io.ktor.client.plugins.HttpTimeout)
            install(ContentNegotiation) {
                json(de.mm20.launcher2.serialization.Json.Lenient)
            }
        }
    }
    single { RadioBrowserClient(get()) }
    single<RadioRepository> { RadioRepositoryImpl(androidContext(), get()) }
    single<de.mm20.launcher2.comms.tv.TvRepository> { de.mm20.launcher2.data.comms.tv.TvRepositoryImpl(androidContext()) }

    // === TELOS_PENDING_REVIEW_START: virtual_app_koin_fix ===
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("toolsVirtualAppProvider")) { ToolsVirtualAppProvider(androidContext()) }
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("trashVirtualAppProvider")) { TrashVirtualAppProvider(androidContext()) }
    factory<VirtualAppProvider>(org.koin.core.qualifier.named("commsVirtualAppProvider")) { CommsVirtualAppProvider(androidContext()) }
    // === TELOS_PENDING_REVIEW_END: virtual_app_koin_fix ===

    // One deserializer per virtual app domain, so pinned / docked virtual apps survive a reload.
    listOf(
        VirtualPhoneApp.Domain, VirtualMessagesApp.Domain, VirtualRadioApp.Domain,
        VirtualMusicApp.Domain, VirtualVideoApp.Domain, VirtualTvApp.Domain, VirtualStremioApp.Domain,
        VirtualYouTubeApp.Domain, VirtualYouTubeMusicApp.Domain,
        VirtualFilesApp.Domain, VirtualCalculatorApp.Domain, VirtualVoiceRecorderApp.Domain,
        VirtualCameraApp.Domain, VirtualScreenRecorderApp.Domain, VirtualScreenshotApp.Domain, VirtualNotesApp.Domain,
        VirtualCalendarApp.Domain, VirtualDownloadsApp.Domain, VirtualMediaApp.Domain,
        VirtualNetworkApp.Domain, VirtualSystemApp.Domain, VirtualViewerApp.Domain,  VirtualTrashApp.Domain,
    ).forEach { domain ->
        factory<SearchableDeserializer>(org.koin.core.qualifier.named(domain)) {
            VirtualAppDeserializer(domain) { getAll<VirtualAppProvider>() }
        }
    }
    // Telos Photos is a tab of Telos Viewer now: a Photos icon that was pinned or docked earlier becomes the Viewer
    factory<SearchableDeserializer>(org.koin.core.qualifier.named(VirtualPhotosApp.Domain)) {
        val viewer = VirtualAppDeserializer(VirtualViewerApp.Domain) { getAll<VirtualAppProvider>() }
        object : SearchableDeserializer {
            override suspend fun deserialize(serialized: String) = viewer.deserialize("${VirtualViewerApp.Domain}://viewer")
        }
    }
}
