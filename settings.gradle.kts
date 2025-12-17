pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":app:app")
include(":app:ui")

include(":core:base")
include(":core:crashreporter")
include(":core:compat")
include(":core:preferences")
include(":core:ktx")
include(":core:i18n")
include(":data:database")
include(":core:permissions")
include(":core:shared")

include(":data:appshortcuts")
include(":data:customattrs")
include(":data:applications")
include(":data:calendar")
include(":data:calculator")
include(":data:themes")
include(":data:contacts")
include(":data:currencies")
include(":data:files")
include(":data:unitconverter")
include(":data:websites")
include(":data:wikipedia")
include(":data:widgets")
include(":data:weather")
include(":data:notifications")
include(":data:search-actions")
include(":data:searchable")
include(":data:plugins")

include(":services:accounts")
include(":services:tags")
include(":services:search")
include(":services:badges")
include(":services:icons")
include(":services:backup")
include(":services:music")

include(":libs:material-color-utilities")
include(":libs:nextcloud")
include(":libs:owncloud")
include(":libs:webdav")
include(":libs:address-formatter")
include(":services:global-actions")
include(":services:widgets")
include(":services:favorites")

include(":plugins:sdk")
include(":data:locations")
include(":services:plugins")
include(":core:devicepose")
include(":core:profiles")
include(":data:i18n")
