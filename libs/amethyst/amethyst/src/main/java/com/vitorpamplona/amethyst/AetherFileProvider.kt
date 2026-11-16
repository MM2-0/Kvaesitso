/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst

import androidx.core.content.FileProvider

/** Keeps the client's URI roots separate from the launcher's FileProvider declaration. */
class AetherFileProvider : FileProvider()
