/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.preferences

/**
 * The account's location-chat identity: the seed its per-geohash throwaway keys
 * come from, and the handle it posts under.
 *
 * # Why this is not two more fields on [AccountSecrets]
 *
 * Every account save mirrors a whole [AccountSecrets], built field by field from
 * `AccountSettings` — which does not hold these, because they are owned by
 * `GeohashChatIdentityState` rather than by the settings object. Folding them in
 * would make each save write null over them, and the group save uses
 * `putOrRemove`, so null *deletes*. The seed would vanish on the next unrelated
 * save and every geohash identity the user has would silently change. A separate
 * group with its own save path cannot be wiped by a save that does not know
 * about it.
 *
 * # Why encrypted
 *
 * The whole point of the seed is that the identities derived from it are
 * unlinkable to the npub. Anyone who can read it can link every cell the user
 * has ever posted in, to each other and to the device, which is exactly what the
 * feature exists to prevent. It was in an encrypted file before; it stays in one.
 */
data class GeohashIdentitySecrets(
    val deviceSeed: String? = null,
    val nickname: String? = null,
)
