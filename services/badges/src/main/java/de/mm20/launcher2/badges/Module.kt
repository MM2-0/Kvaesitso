package de.mm20.launcher2.badges

import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.badges.settings.BadgeSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val badgesModule = module {
    single<BadgeService> { BadgeServiceImpl(androidContext(), get()) }
    single<BadgeSettings> { BadgeSettings(androidContext(), get()) }
    factory<Backupable>(named<BadgeSettings>()) { get<BadgeSettings>() }
}