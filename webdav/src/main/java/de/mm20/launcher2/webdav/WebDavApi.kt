package de.mm20.launcher2.webdav

import com.balsikandar.crashreporter.CrashReporter
import de.mm20.launcher2.ktx.castToOrNull
import de.mm20.launcher2.ktx.decodeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

object WebDavApi {
    suspend fun search(webDavUrl: String, username: String, query: String, client: OkHttpClient): List<WebDavFile> {
        val requestBody = """
            <?xml version="1.0" encoding="UTF-8"?>
             <d:searchrequest xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                 <d:basicsearch>
                     <d:select>
                         <d:prop>
                             <oc:fileid/>
                             <d:displayname/>
                             <d:getcontenttype/>
                             <d:resourcetype/>
                             <oc:size/>
                             <oc:owner-display-name/>
                         </d:prop>
                     </d:select>
                     <d:from>
                         <d:scope>
                             <d:href><![CDATA[/files/$username]]></d:href>
                             <d:depth>infinity</d:depth>
                         </d:scope>
                     </d:from>
                     <d:where>
                         <d:like>
                             <d:prop>
                                 <d:displayname/>
                             </d:prop>
                             <d:literal><![CDATA[%$query%]]></d:literal>
                         </d:like>
                     </d:where>
                     <d:orderby/>
                </d:basicsearch>
            </d:searchrequest>
        """.trimIndent()
        val request = Request.Builder()
                .url(webDavUrl)
                .method("SEARCH", requestBody.toRequestBody("text/xml".toMediaType()))
                .build()
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<WebDavFile>()
            try {
                val response = client.newCall(request).execute()
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.body?.byteStream()
                        ?: return@withContext emptyList<WebDavFile>())
                val responses = document.getElementsByTagName("d:response")
                for (i in 0 until responses.length) {
                    val res = responses.item(i) as? Element ?: continue
                    val url = res.getElementsByTagName("d:href")
                            .takeIf { it.length > 0 }?.item(0)
                            ?.textContent?.takeIf { it.isNotEmpty() } ?: continue
                    val fileId = res.getElementsByTagName("oc:fileid")
                            .takeIf { it.length > 0 }?.item(0)
                            ?.textContent?.toLongOrNull() ?: continue

                    val displayName = res.getElementsByTagName("d:displayname")
                            .takeIf { it.length > 0 }?.item(0)?.textContent
                            ?.takeIf { it.isNotEmpty() }
                            ?: url.trimEnd('/').substringAfterLast("/").decodeUrl("utf8")
                            ?: continue

                    val isDirectory = res.getElementsByTagName("d:resourcetype")
                            .takeIf { it.length > 0 }
                            ?.item(0)?.childNodes?.length == 1
                    val mimeType = res.getElementsByTagName("d:getcontenttype")
                            .takeIf { it.length > 0 }?.item(0)?.textContent?.takeIf { it.isNotEmpty() }
                            ?: if (isDirectory) "inode/directory" else "application/octet-stream"
                    val size = res.getElementsByTagName("oc:size")
                            .takeIf { it.length > 0 }?.item(0)?.textContent?.toLongOrNull()
                            ?: 0L
                    val owner = res.getElementsByTagName("oc:owner-display-name")
                            .takeIf { it.length > 0 }?.item(0)?.textContent
                            ?.takeIf { it.isNotEmpty() }


                    results += WebDavFile(
                            name = displayName,
                            id = fileId,
                            isDirectory = isDirectory,
                            mimeType = mimeType,
                            size = size,
                            owner = owner,
                            url = url
                    )
                }
            } catch (e: Exception) {
                CrashReporter.logException(e)
            }
            return@withContext results
        }
    }

    fun getSearchRequestBody(query: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <oc:search-files
                xmlns:a="DAV:"
                xmlns:oc="http://owncloud.org/ns">
                <a:prop>
                    <oc:fileid/>
                    <a:displayname/>
                    <a:getcontenttype/>
                    <a:resourcetype/>
                    <oc:size/>
                    <oc:owner-display-name/>
                </a:prop>
                <oc:search>
                    <oc:pattern><![CDATA[$query]]></oc:pattern>
                    <oc:limit>20</oc:limit>
                </oc:search>
            </oc:search-files>
        """.trimIndent()
    }

    suspend fun searchReport(webDavUrl: String, username: String, query: String, client: OkHttpClient): List<WebDavFile> {
        val requestBody = getSearchRequestBody(query)
        val request = Request.Builder()
                .url("${webDavUrl}files/$username")
                .method("REPORT", requestBody.toRequestBody())
                .build()
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<WebDavFile>()
            try {
                val response = client.newCall(request).execute()
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.body?.byteStream()
                        ?: return@withContext emptyList<WebDavFile>())
                val responses = document.getElementsByTagName("d:response")
                for (i in 0 until responses.length) {
                    val res = responses.item(i) as? Element ?: continue
                    val url = res.getElementsByTagName("d:href")
                            .takeIf { it.length > 0 }?.item(0)
                            ?.textContent?.takeIf { it.isNotEmpty() } ?: continue
                    val fileId = res.getElementsByTagName("oc:fileid")
                            .takeIf { it.length > 0 }?.item(0)
                            ?.textContent?.toLongOrNull() ?: continue

                    val displayName = res.getElementsByTagName("d:displayname")
                            .takeIf { it.length > 0 }?.item(0)?.textContent
                            ?.takeIf { it.isNotEmpty() }
                            ?: url.trimEnd('/').substringAfterLast("/").decodeUrl("utf8")
                            ?: continue

                    val isDirectory = res.getElementsByTagName("d:resourcetype")
                            .takeIf { it.length > 0 }
                            ?.item(0)?.childNodes?.length == 1
                    val mimeType = res.getElementsByTagName("d:getcontenttype")
                            .takeIf { it.length > 0 }?.item(0)?.textContent?.takeIf { it.isNotEmpty() }
                            ?: if (isDirectory) "inode/directory" else "application/octet-stream"
                    val size = res.getElementsByTagName("oc:size")
                            .takeIf { it.length > 0 }?.item(0)?.textContent?.toLongOrNull()
                            ?: 0L
                    val owner = res.getElementsByTagName("oc:owner-display-name")
                            .takeIf { it.length > 0 }?.item(0)?.textContent
                            ?.takeIf { it.isNotEmpty() }


                    results += WebDavFile(
                            name = displayName,
                            id = fileId,
                            isDirectory = isDirectory,
                            mimeType = mimeType,
                            size = size,
                            owner = owner,
                            url = url
                    )
                }
            } catch (e: Exception) {
                CrashReporter.logException(e)
            }
            return@withContext results
        }
    }
}