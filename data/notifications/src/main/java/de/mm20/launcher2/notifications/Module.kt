package de.mm20.launcher2.notifications

import org.koin.dsl.module

val notificationsModule = module {
    single<NotificationRepository> { NotificationRepository() }
}