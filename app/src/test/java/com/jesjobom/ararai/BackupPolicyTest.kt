package com.jesjobom.ararai

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupPolicyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `application backup is disabled`() {
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `legacy backup rules exclude every app data domain`() {
        assertEquals(EXCLUDED_DOMAINS, exclusions("backup_rules.xml").single())
    }

    @Test
    fun `modern extraction rules exclude every domain from cloud and device transfer`() {
        assertEquals(listOf(EXCLUDED_DOMAINS, EXCLUDED_DOMAINS), exclusions("data_extraction_rules.xml"))
    }

    private fun exclusions(fileName: String): List<Set<String>> {
        val result = mutableListOf<MutableSet<String>>()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceXml(fileName))
        val root = document.documentElement
        val containers = when (root.tagName) {
            "full-backup-content" -> listOf(root)
            "data-extraction-rules" -> listOf("cloud-backup", "device-transfer").map { tag ->
                root.getElementsByTagName(tag).item(0) as org.w3c.dom.Element
            }
            else -> error("Unexpected backup policy root: ${root.tagName}")
        }
        containers.forEach { container ->
            val current = linkedSetOf<String>()
            val excludes = container.getElementsByTagName("exclude")
            for (index in 0 until excludes.length) {
                val exclude = excludes.item(index) as org.w3c.dom.Element
                assertEquals(".", exclude.getAttribute("path"))
                current += exclude.getAttribute("domain")
            }
            result += current
        }
        return result
    }

    private fun sourceXml(fileName: String): File {
        val candidates = listOf(
            File("app/src/main/res/xml", fileName),
            File("src/main/res/xml", fileName),
        )
        return candidates.firstOrNull(File::isFile).also {
            assertTrue("Unable to locate source XML $fileName from ${File(".").absolutePath}", it != null)
        }!!
    }

    private companion object {
        val EXCLUDED_DOMAINS = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
    }
}
