package de.mm20.launcher2.licenses

import de.mm20.launcher2.base.R

val OpenSourceLicenses = arrayOf(
    OpenSourceLibrary(
        name = "Kotlin Standard Library",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://kotlinlang.org/"
    ),
    OpenSourceLibrary(
        name = "Android Jetpack",
        description = "A collection of Android software components to make it easier to develop great Android apps.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://developer.android.com/jetpack"
    ),
    OpenSourceLibrary(
        name = "Accompanist",
        description = "Accompanist is a group of libraries that aim to supplement Jetpack Compose with features that are commonly required by developers but not yet available.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright 2020 The Android Open Source Project",
        url = "https://developer.android.com/jetpack"
    ),
    OpenSourceLibrary(
        name = "Material Components for Android",
        description = "Material Components for Android (MDC-Android) help developers execute Material Design.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://material.io/develop/android/"
    ),
    OpenSourceLibrary(
        name = "Lottie",
        description = "Lottie is a library for Android, iOS, Web, and Windows that parses Adobe After Effects animations exported as json with Bodymovin and renders them natively on mobile and on the web.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://airbnb.io/lottie/"
    ),
    OpenSourceLibrary(
        name = "OkHttp",
        description = "An HTTP & HTTP/2 client for Android and Java applications",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright 2019 Square, Inc.",
        url = "https://square.github.io/okhttp/"
    ),
    OpenSourceLibrary(
        name = "Retrofit",
        description = "A type-safe HTTP client for Android and Java",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright 2013 Square, Inc.",
        url = "https://square.github.io/retrofit/"
    ),
    OpenSourceLibrary(
        name = "Gson",
        description = "Gson is a Java library that can be used to convert Java Objects into their JSON representation.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright 2008 Google Inc.",
        url = "https://github.com/google/gson/"
    ),
    OpenSourceLibrary(
        name = "commons-suncalc",
        description = "A Java library for calculation of sun and moon positions and phases.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://github.com/shred/commons-suncalc"
    ),
    OpenSourceLibrary(
        name = "Jsoup",
        description = "A Java library for working with real-world HTML",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        copyrightNote = "Copyright (c) 2009-2021 Jonathan Hedley <https://jsoup.org/>",
        url = "https://jsoup.org/"
    ),
    OpenSourceLibrary(
        name = "TextDrawable",
        description = "A light-weight library providing images with letter/text like the Gmail app",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        copyrightNote = "Copyright (c) 2014 Amulya Khare",
        url = "https://github.com/amulyakhare/TextDrawable"
    ),
    OpenSourceLibrary(
        name = "Glide",
        description = "A fast and efficient open source media management and image loading framework for Android",
        licenseName = R.string.glide_license_name,
        licenseText = R.raw.license_glide,
        url = "https://bumptech.github.io/glide/"
    ),
    OpenSourceLibrary(
        name = "Glide Transformations",
        description = "An Android transformation library providing a variety of image transformations for Glide",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright (C) 2020 Wasabeef",
        url = "https://github.com/wasabeef/glide-transformations"
    ),
    OpenSourceLibrary(
        name = "Material Dialogs",
        description = "Easy to use material design dialogs",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright (C) 2020 Wasabeef",
        url = "https://github.com/afollestad/material-dialogs"
    ),
    OpenSourceLibrary(
        name = "Groupie",
        description = "Easy to use material design dialogs",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        url = "https://github.com/lisawray/groupie"
    ),
    OpenSourceLibrary(
        name = "DragLinearLayout",
        description = "An Android LinearLayout that supports draggable and swappable child Views",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        copyrightNote = "Copyright (c) 2014 Justas Medeisis",
        url = "https://github.com/justasm/DragLinearLayout"
    ),
    OpenSourceLibrary(
        name = "ViewPropertyValueAnimator",
        description = "Wrapper of the ObjectAnimator that can be used similarly to ViewPropertyAnimator.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright 2015 Bartosz Lipiński",
        url = "https://github.com/blipinsk/ViewPropertyObjectAnimator"
    ),
    OpenSourceLibrary(
        name = "mXparser",
        description = "A super easy, rich, fast and highly flexible math expression parser library",
        licenseName = R.string.bsd_2clause_name,
        licenseText = R.raw.license_bsd_2clause,
        copyrightNote = "Copyright 2010 - 2020 Mariusz Gromada. All rights reserved.",
        url = "https://mathparser.org/"
    ),
    OpenSourceLibrary(
        name = "Google Auth Library",
        description = "Open source authentication client library for Java.",
        licenseName = R.string.bsd_3clause_name,
        licenseText = R.raw.license_bsd_3clause,
        copyrightNote = "Copyright 2014, Google Inc. All rights reserved.",
        url = "https://github.com/googleapis/google-auth-library-java"
    ),
    OpenSourceLibrary(
        name = "Google APIs Client Library for Android",
        description = "The Google APIs Client Library for Java is a flexible, efficient, and powerful Java client library for accessing any HTTP-based API on the web, not just Google APIs.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://github.com/googleapis/google-api-java-client"
    ),
    OpenSourceLibrary(
        name = "Microsoft Graph Android SDK",
        description = "Deprecated API client for Microsoft Graph APIs",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        copyrightNote = "Copyright (c) 2015 Microsoft Corporation",
        url = "https://github.com/microsoftgraph/msgraph-sdk-android"
    ),
    OpenSourceLibrary(
        name = "Microsoft Authentication Library (MSAL) for Android",
        description = "The MSAL library for Android gives your app the ability to use the Microsoft Cloud by supporting Microsoft Azure Active Directory and Microsoft accounts in a converged experience using industry standard OAuth2 and OpenID Connect. The library also supports Azure AD B2C.",
        licenseName = R.string.mit_license_name,
        licenseText = R.raw.license_mit,
        copyrightNote = "Copyright (c) Microsoft Corporation",
        url = "https://github.com/AzureAD/microsoft-authentication-library-for-android"
    ),
    OpenSourceLibrary(
        name = "CrashReporter",
        description = "CrashReporter is a handy tool to capture app crashes and save them in a file.",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        copyrightNote = "Copyright (C) 2016 Bal Sikandar\nCopyright (C) 2011 Android Open Source Project",
        url = "https://github.com/MindorksOpenSource/CrashReporter"
    ),
    OpenSourceLibrary(
        name = "Material Design Icons",
        description = "Beautifully crafted, delightful, and easy to use in your web, Android, and iOS projects",
        licenseName = R.string.apache_license_name,
        licenseText = R.raw.license_apache_2,
        url = "https://material.io/icons/"
    ),
)