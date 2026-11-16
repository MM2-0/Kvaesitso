package de.mm20.launcher2.comms.stremio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioProtocolTest {

    @Test
    fun baseUrlAcceptsStremioLinksAndManifestAddresses() {
        assertEquals("https://example.com/addon", StremioProtocol.baseUrl("stremio://example.com/addon/manifest.json"))
        assertEquals("https://example.com/addon", StremioProtocol.baseUrl(" https://example.com/addon/manifest.json?x=1 "))
        assertEquals("https://example.com", StremioProtocol.baseUrl("https://example.com/"))
        assertNull(StremioProtocol.baseUrl("ftp://example.com"))
        assertNull(StremioProtocol.baseUrl("not a url"))
    }

    @Test
    fun resourceUrlsFollowTheProtocol() {
        assertEquals("https://a.b/catalog/movie/top.json", StremioProtocol.resourceUrl("https://a.b/", "catalog", "movie", "top"))
        assertEquals(
            "https://a.b/catalog/movie/top/search=game%20of%20thrones&skip=100.json",
            StremioProtocol.resourceUrl("https://a.b", "catalog", "movie", "top", linkedMapOf("search" to "game of thrones", "skip" to "100")),
        )
        // the colons of series ids stay readable
        assertEquals("https://a.b/stream/series/tt0108778:1:1.json", StremioProtocol.resourceUrl("https://a.b", "stream", "series", "tt0108778:1:1"))
    }

    @Test
    fun manifestResourcesMayBeStringsOrObjects() {
        val m = StremioProtocol.parseManifest(
            """{"id":"x.y","name":"X","version":"1","types":["movie","series"],"idPrefixes":["tt"],
               "resources":["catalog",{"name":"stream","types":["movie"],"idPrefixes":["tt"]}],
               "catalogs":[{"type":"movie","id":"top","name":"Top","extra":[{"name":"search"}]}],
               "behaviorHints":{"p2p":true}}"""
        )!!
        assertTrue(m.catalogs.single().searchable)
        assertTrue(m.supports("stream", "movie", "tt1"))
        assertTrue(!m.supports("stream", "series", "tt1"))
        assertTrue(!m.supports("stream", "movie", "yt:1"))
        assertTrue(!m.supports("meta", "movie", "tt1"))
    }

    @Test
    fun streamsAndMagnets() {
        val streams = StremioProtocol.parseStreams(
            """{"streams":[{"url":"https://v.example/a.mp4","name":"HD"},
               {"infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":2,"sources":["tracker:udp://t.example:80","dht:0123"],"title":"Old name"},
               {"nzbUrl":"https://x/y.nzb"}]}""",
            "Addon",
        )
        assertEquals(StreamItem.Kind.Web, streams[0].kind)
        assertEquals(StreamItem.Kind.Torrent, streams[1].kind)
        assertEquals("Old name", streams[1].description)
        assertEquals(StreamItem.Kind.Unsupported, streams[2].kind)
        val magnet = StremioProtocol.magnet(streams[1], "A B")!!
        assertTrue(magnet.startsWith("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=A%20B"))
        assertTrue(magnet.contains("&tr=udp%3A%2F%2Ft.example%3A80"))
        assertNull(StremioProtocol.magnet(streams[0], "x"))
    }

    @Test
    fun metaWithEpisodes() {
        val meta = StremioProtocol.parseMeta(
            """{"meta":{"id":"tt1","type":"series","name":"S","videos":[{"id":"tt1:1:1","title":"Pilot","season":1,"episode":1}]}}"""
        )!!
        assertTrue(meta.isSeries)
        assertEquals(1, meta.videos.single().season)
    }

    @Test
    fun addonDirectories() {
        val list = StremioProtocol.parseAddonCatalog(
            """{"addons":[{"transportUrl":"https://x.example/manifest.json","transportName":"http",
               "manifest":{"id":"a.b","name":"A","version":"1.0","resources":["stream"],"types":["movie"],"catalogs":[]}}]}"""
        )
        assertEquals("https://x.example", list.single().baseUrl)
        assertEquals("A", list.single().manifest.name)
        // a plain list works as well
        assertEquals(1, StremioProtocol.parseAddonList("""[{"transportUrl":"stremio://y.example/manifest.json","manifest":{"id":"c","name":"C","version":"1","resources":["catalog"],"types":["movie"],"catalogs":[]}}]""").size)
        assertTrue(StremioProtocol.parseAddonList("not json").isEmpty())
    }

    @Test
    fun parseDeepLinkDetailAndAddon() {
        val a = StremioProtocol.parseDeepLink("stremio:///detail/movie/tt0111161") as StremioDeepLink.Detail
        assertEquals("movie", a.type)
        assertEquals("tt0111161", a.id)
        val b = StremioProtocol.parseDeepLink("stremio://detail/series/tt0108778:1:1") as StremioDeepLink.Detail
        assertEquals("series", b.type)
        assertEquals("tt0108778:1:1", b.id)
        val c = StremioProtocol.parseDeepLink("stremio://example.com/addon/manifest.json") as StremioDeepLink.AddonInstall
        assertEquals("stremio://example.com/addon/manifest.json", c.url)
        val d = StremioProtocol.parseDeepLink("https://example.com/addon/manifest.json") as StremioDeepLink.AddonInstall
        assertEquals("https://example.com/addon/manifest.json", d.url)
        assertNull(StremioProtocol.parseDeepLink("ftp://x"))
        assertNull(StremioProtocol.parseDeepLink(""))
    }

    @Test
    fun parseManifestConfigurationUrl() {
        val m = StremioProtocol.parseManifest(
            """{"id":"x","name":"X","resources":["catalog"],"types":["movie"],"catalogs":[],
               "behaviorHints":{"configurationRequired":true,"configurationURL":"https://example.com/cfg"}}"""
        )!!
        assertTrue(m.configurationRequired)
        assertEquals("https://example.com/cfg", m.configurationURL)
    }
}
