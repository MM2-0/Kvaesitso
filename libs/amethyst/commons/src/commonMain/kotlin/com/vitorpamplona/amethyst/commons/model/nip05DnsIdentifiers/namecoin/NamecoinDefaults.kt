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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer

/*
 * The public Namecoin ElectrumX servers Amethyst queries when the user has not configured their own.
 * Quartz ships no server list (since 2.10.1): which servers to use, and which certificate to trust
 * for each, is the app's choice. A self-signed server carries its own certificate in
 * pinnedCertsPem, accepted for that server only.
 *
 * To refresh a certificate: `echo | openssl s_client -connect HOST:PORT 2>/dev/null | openssl x509 -outform PEM`
 */

/**
 * Whole-lookup budget for resolving one Namecoin identifier, across all
 * ElectrumX attempts. Quartz's own default is 20s, which is ample on clearnet
 * (a full nameShow is 3-4 round trips, ~2s direct) but too tight over Tor:
 * a single round trip on a slow circuit can take 5-10s, so the same lookup
 * needs 20s+ and the user sees "Resolution timed out" even with healthy
 * servers. Doubling to 40s covers slow-circuit Tor lookups.
 */
const val NAMECOIN_LOOKUP_TIMEOUT_MS = 40_000L

/** nmc2.bitcoins.sk:57002 / 46.229.238.187:57002, self-signed, expires 2030-10-22. */
private val NMC2_BITCOINS_SK_CERT_PEM =
    """
-----BEGIN CERTIFICATE-----
MIID+TCCAuGgAwIBAgIUdmJGukmfPvqmAYpTfuGcjRoYHJ8wDQYJKoZIhvcNAQEL
BQAwgYsxCzAJBgNVBAYTAlNLMREwDwYDVQQIDAhTbG92YWtpYTETMBEGA1UEBwwK
QnJhdGlzbGF2YTEUMBIGA1UECgwLYml0Y29pbnMuc2sxGTAXBgNVBAMMEG5tYzIu
Yml0Y29pbnMuc2sxIzAhBgkqhkiG9w0BCQEWFGRlYWZib3lAY2ljb2xpbmEub3Jn
MB4XDTIwMTAyNDE5MjQzOVoXDTMwMTAyMjE5MjQzOVowgYsxCzAJBgNVBAYTAlNL
MREwDwYDVQQIDAhTbG92YWtpYTETMBEGA1UEBwwKQnJhdGlzbGF2YTEUMBIGA1UE
CgwLYml0Y29pbnMuc2sxGTAXBgNVBAMMEG5tYzIuYml0Y29pbnMuc2sxIzAhBgkq
hkiG9w0BCQEWFGRlYWZib3lAY2ljb2xpbmEub3JnMIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAzBUkZNDfaz7kc28l5tDKohJjekWmz1ynzfGx3ZLsqOZE
c+kNfcMaWU+zT/j0mV6pX6KSH7G9pPAku+8PRdKRq+d63wiJDEjGSaFztQWKW6L1
vTxgCK5gu+Eir3BkTagJObsrLKS+T6qH610/3+btGgoR3lunB5TzCgB/9oQanjDW
zjg2CwmxgR5Iw1Eqfenx7zkSK33FSXSF2SvbUs1Atj2oPU4DLivyrx0RaUmaPemn
cmcpnax+py4pQeB6dJWU1INhzXt3hTJRyoqsSGY3vCECIKIBIkh8GsYjAX4z+Y9y
6pJx0da2b88qPWdsoxaIMvrQiuWknDrSJwAyw2Yd8QIDAQABo1MwUTAdBgNVHQ4E
FgQUT2J83B2/9jxGGdFeWrxMohTzHNwwHwYDVR0jBBgwFoAUT2J83B2/9jxGGdFe
WrxMohTzHNwwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAsbxX
wN8tZaXOybImMZCQS7zfxmKl2IAcqu+R01KPfnIfrFqXPsGDDl3rYLkwh1O4/hYQ
NKNW9KTxoJxuBmAkm7EXQQh1XUUzajdEDqDBVRyvR0Z2MdMYnMSAiiMXMl2wUZnc
QXYftBo0HbtfsaJjImQdDjmlmRPSzE/RW6iUe+1cesKBC7e8nVf69Yu/fxO4m083
VWwAstlWJfk1GyU7jzVc8svealg/oIiDoOMe6CFSLx1BDv2FeHSpRdqd3fn+AC73
bK2N2smrHUOQnFijuiFw3WOrjERi0eMhjVNfVu9W9ZYa/Wd6SdIzV55LbG+NpmSf
5W7ix41hRvdT6cTAJA==
-----END CERTIFICATE-----
    """.trimIndent()

/**
 * relay.testls.bit:50012 / 23.158.233.10:50012, self-signed (CN=relay.testls.bit), expires 2036-04-30.
 *
 * SHA-256 (DER): BB:0B:35:E6:42:35:A7:94:E1:57:B6:9A:CD:72:DA:4C:CC:16:A4:D8:1D:0F:F6:B1:D8:F7:FD:FC:6C:CA:6C:BA
 */
private val RELAY_TESTLS_BIT_CERT_PEM =
    """
-----BEGIN CERTIFICATE-----
MIIDFzCCAf+gAwIBAgIUAz+Ky5Lu2u1QchHKTwQIStWD8fQwDQYJKoZIhvcNAQEL
BQAwGzEZMBcGA1UEAwwQcmVsYXkudGVzdGxzLmJpdDAeFw0yNjA1MDMwNjEyNDBa
Fw0zNjA0MzAwNjEyNDBaMBsxGTAXBgNVBAMMEHJlbGF5LnRlc3Rscy5iaXQwggEi
MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCngMim0wilCdclMyIIBRAeFBjE
HWA0l5n3ebCtyepYmyXUrbth2TDdyMHiUNVvE3f7RSAUFtE+Tjb9+xzmLf04qlbC
i3b2DV3xdVOZpRCI4kybmraYG6lRLQJ5I/N8NWgPsFgcy2mZF3q0yMVEkzGAKqUT
QGZd2eBs/OVicbCWKmgyhXlnqGeHIs4iKOqenHKSZ8QE5bhAKaMn+Q116QEdBg12
svGNoSZSlX8hDNUf5N5pOcsK8vj0Yb0ypJOd0J5eVpYS/KA0oMMwALnEs0H17hfO
IIaWqrbfaqmdR67uzUfci2EoiwQJrXSy7WkNiVX0ikN02VlUPCb2OaSABmwjAgMB
AAGjUzBRMB0GA1UdDgQWBBSgnHYFU93wufP131xk5w843VWhATAfBgNVHSMEGDAW
gBSgnHYFU93wufP131xk5w843VWhATAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3
DQEBCwUAA4IBAQAiVC5RJj/Y3S5uNYraPmRLQrgPui9eGlWDh6LeZvjftACA3WiN
XabJtTmHxOFFwxmBtzGVCWKR6udETHyfqaq0me/XoYLjGstYTd14GfoV9Klx7Glh
gLOgqC3Do04o2xnXxhPh00c0jUggKdI05KLVAc4dLfU/XDrOfxBl4XHZY4lYz4CF
bn+n5Q1cqqcGqoJIHl2cgDdrxSMigIpsunjk8cJXO+hsteA/Pd1UUY5plvE9nbNv
hy4QgfNqjy36b0Nbm7Fc9Te9W6zgXjHM+q7KhuQvTfKh2sHbCJzRcy7Chgwqyrfq
NsK0JcBkyRPB+fXmEoE/Xmj/UnOD0fZCCanD
-----END CERTIFICATE-----
    """.trimIndent()

private val ELECTRUMX_TESTLS_SPACE = ElectrumxServer("electrumx.testls.space", 50002, useSsl = true)
private val ELECTRUMX2_TESTLS_SPACE = ElectrumxServer("electrumx2.testls.space", 50002, useSsl = true)
private val NMC2_BITCOINS_SK = ElectrumxServer("nmc2.bitcoins.sk", 57002, useSsl = true, pinnedCertsPem = listOf(NMC2_BITCOINS_SK_CERT_PEM))

// relay.testls.bit serves its self-signed certificate on 50012; the box's default ports carry the
// Let's Encrypt electrumx2.testls.space endpoint.
private val RELAY_TESTLS_BIT = ElectrumxServer("relay.testls.bit", 50012, useSsl = true, pinnedCertsPem = listOf(RELAY_TESTLS_BIT_CERT_PEM))

// Bare-IP peers of the two self-signed servers, for resolvers with an unhealthy DNS path. The pin is
// checked against the certificate alone, without hostname verification.
private val NMC2_BITCOINS_SK_IP = ElectrumxServer("46.229.238.187", 57002, useSsl = true, pinnedCertsPem = listOf(NMC2_BITCOINS_SK_CERT_PEM))
private val RELAY_TESTLS_BIT_IP = ElectrumxServer("23.158.233.10", 50012, useSsl = true, pinnedCertsPem = listOf(RELAY_TESTLS_BIT_CERT_PEM))

/**
 * Default servers on clearnet. electrum.nmc.ethicnology.com was dropped: NXDOMAIN since 2026-10-30.
 */
val DEFAULT_ELECTRUMX_SERVERS =
    listOf(
        ELECTRUMX_TESTLS_SPACE,
        NMC2_BITCOINS_SK,
        NMC2_BITCOINS_SK_IP,
        RELAY_TESTLS_BIT,
        RELAY_TESTLS_BIT_IP,
        ELECTRUMX2_TESTLS_SPACE,
    )

/**
 * Default servers when NIP-05 lookups go through Tor: the onion services first, clearnet after.
 * The onions need no pin; the onion address authenticates the server.
 */
val TOR_ELECTRUMX_SERVERS =
    listOf(
        ElectrumxServer("i665jpwsq46zlsdbnj4axgzd3s56uzey5uhotsnxzsknzbn36jaddsid.onion", 50002, useSsl = true),
        // relay.testls.bit's hidden service, plaintext: the onion key already authenticates it.
        ElectrumxServer("6cbn4rskfdr647otej7gpqlmpqcmj723vg2eoeuu7ljbwu6cpdebozyd.onion", 50001, useSsl = false),
        ELECTRUMX_TESTLS_SPACE,
        NMC2_BITCOINS_SK,
        RELAY_TESTLS_BIT,
        RELAY_TESTLS_BIT_IP,
        ELECTRUMX2_TESTLS_SPACE,
    )
