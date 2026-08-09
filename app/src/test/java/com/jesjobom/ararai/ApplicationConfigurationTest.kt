package com.jesjobom.ararai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ApplicationConfigurationTest {
    @Test
    fun `application declares branded launcher and launch theme`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceManifest())
        val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        val activities = document.getElementsByTagName("activity")
        val mainActivity =
            (0 until activities.length)
                .map { activities.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("android:name") == ".MainActivity" }

        assertEquals("@mipmap/ic_launcher", application.getAttribute("android:icon"))
        assertEquals("@mipmap/ic_launcher_round", application.getAttribute("android:roundIcon"))
        assertEquals("@style/Theme.ArarAI.Starting", mainActivity.getAttribute("android:theme"))
        assertTrue(sourceResource("mipmap-anydpi-v26/ic_launcher.xml").isFile)
        assertTrue(sourceResource("mipmap-anydpi-v26/ic_launcher_round.xml").isFile)
        assertTrue(sourceResource("values-v31/styles.xml").isFile)
    }

    @Test
    fun `launcher activity is locked to portrait`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceManifest())
        val activities = document.getElementsByTagName("activity")
        val mainActivity =
            (0 until activities.length)
                .map { activities.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("android:name") == ".MainActivity" }

        assertEquals("portrait", mainActivity.getAttribute("android:screenOrientation"))
        assertEquals("singleTask", mainActivity.getAttribute("android:launchMode"))
    }

    @Test
    fun `model download service is a non-exported foreground data sync service`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceManifest())
        val services = document.getElementsByTagName("service")
        val service =
            (0 until services.length)
                .map { services.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("android:name") == ".ModelDownloadService" }

        assertEquals("false", service.getAttribute("android:exported"))
        assertEquals("dataSync", service.getAttribute("android:foregroundServiceType"))
    }

    @Test
    fun `manifest declares foreground service and notification permissions`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sourceManifest())
        val permissions = document.getElementsByTagName("uses-permission")
        val names =
            (0 until permissions.length)
                .map { permissions.item(it) as org.w3c.dom.Element }
                .map { it.getAttribute("android:name") }

        assertTrue("android.permission.FOREGROUND_SERVICE" in names)
        assertTrue("android.permission.FOREGROUND_SERVICE_DATA_SYNC" in names)
        assertTrue("android.permission.POST_NOTIFICATIONS" in names)
    }

    private fun sourceManifest(): File {
        val candidates =
            listOf(
                File("app/src/main/AndroidManifest.xml"),
                File("src/main/AndroidManifest.xml"),
            )
        return candidates.firstOrNull(File::isFile).also {
            assertTrue("Unable to locate source AndroidManifest.xml from ${File(".").absolutePath}", it != null)
        }!!
    }

    private fun sourceResource(relativePath: String): File {
        val candidates =
            listOf(
                File("app/src/main/res/$relativePath"),
                File("src/main/res/$relativePath"),
            )
        return candidates.firstOrNull(File::isFile) ?: candidates.first()
    }
}
