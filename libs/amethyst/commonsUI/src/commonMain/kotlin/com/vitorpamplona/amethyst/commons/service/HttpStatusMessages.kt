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
package com.vitorpamplona.amethyst.commons.service

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.http_status_400
import com.vitorpamplona.amethyst.commons.resources.http_status_401
import com.vitorpamplona.amethyst.commons.resources.http_status_402
import com.vitorpamplona.amethyst.commons.resources.http_status_403
import com.vitorpamplona.amethyst.commons.resources.http_status_404
import com.vitorpamplona.amethyst.commons.resources.http_status_405
import com.vitorpamplona.amethyst.commons.resources.http_status_406
import com.vitorpamplona.amethyst.commons.resources.http_status_407
import com.vitorpamplona.amethyst.commons.resources.http_status_408
import com.vitorpamplona.amethyst.commons.resources.http_status_409
import com.vitorpamplona.amethyst.commons.resources.http_status_410
import com.vitorpamplona.amethyst.commons.resources.http_status_411
import com.vitorpamplona.amethyst.commons.resources.http_status_412
import com.vitorpamplona.amethyst.commons.resources.http_status_413
import com.vitorpamplona.amethyst.commons.resources.http_status_414
import com.vitorpamplona.amethyst.commons.resources.http_status_415
import com.vitorpamplona.amethyst.commons.resources.http_status_416
import com.vitorpamplona.amethyst.commons.resources.http_status_417
import com.vitorpamplona.amethyst.commons.resources.http_status_426
import com.vitorpamplona.amethyst.commons.resources.http_status_500
import com.vitorpamplona.amethyst.commons.resources.http_status_501
import com.vitorpamplona.amethyst.commons.resources.http_status_502
import com.vitorpamplona.amethyst.commons.resources.http_status_503
import com.vitorpamplona.amethyst.commons.resources.http_status_504
import com.vitorpamplona.amethyst.commons.resources.http_status_505
import com.vitorpamplona.amethyst.commons.resources.http_status_506
import com.vitorpamplona.amethyst.commons.resources.http_status_507
import com.vitorpamplona.amethyst.commons.resources.http_status_508
import com.vitorpamplona.amethyst.commons.resources.http_status_511
import org.jetbrains.compose.resources.StringResource

class HttpStatusMessages {
    companion object {
        fun resourceIdFor(statusCode: Int = 0): StringResource? =
            when (statusCode) {
                400 -> Res.string.http_status_400
                401 -> Res.string.http_status_401
                402 -> Res.string.http_status_402
                403 -> Res.string.http_status_403
                404 -> Res.string.http_status_404
                405 -> Res.string.http_status_405
                406 -> Res.string.http_status_406
                407 -> Res.string.http_status_407
                408 -> Res.string.http_status_408
                409 -> Res.string.http_status_409
                410 -> Res.string.http_status_410
                411 -> Res.string.http_status_411
                412 -> Res.string.http_status_412
                413 -> Res.string.http_status_413
                414 -> Res.string.http_status_414
                415 -> Res.string.http_status_415
                416 -> Res.string.http_status_416
                417 -> Res.string.http_status_417
                426 -> Res.string.http_status_426
                500 -> Res.string.http_status_500
                501 -> Res.string.http_status_501
                502 -> Res.string.http_status_502
                503 -> Res.string.http_status_503
                504 -> Res.string.http_status_504
                505 -> Res.string.http_status_505
                506 -> Res.string.http_status_506
                507 -> Res.string.http_status_507
                508 -> Res.string.http_status_508
                511 -> Res.string.http_status_511
                else -> null
            }
    }
}
