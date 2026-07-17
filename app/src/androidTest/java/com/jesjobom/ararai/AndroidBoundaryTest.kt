package com.jesjobom.ararai

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jesjobom.ararai.chat.FileChatMediaRepository
import com.jesjobom.ararai.engine.JniLlamaNativeBridge
import com.jesjobom.ararai.ui.chatImageImporter
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBoundaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestDeclaresAudioPermissionAndDisablesBackup() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertTrue(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.RECORD_AUDIO))
        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        context.resources.getXml(R.xml.backup_rules).close()
        context.resources.getXml(R.xml.data_extraction_rules).close()
    }

    @Test
    fun importsImageThroughRealContentResolverProvider() {
        val mediaDir = File(context.cacheDir, "instrumentation-chat-media").apply {
            deleteRecursively()
            mkdirs()
        }
        val repository = FileChatMediaRepository(mediaDir)
        val importer = context.chatImageImporter(repository)

        val imported = importer.import(Uri.parse("content://com.jesjobom.ararai.test.chat-image/image"))

        assertTrue(imported.file.isFile)
        assertEquals("provider-image.png", imported.displayName)
        assertTrue(imported.file.length() > 0L)
        mediaDir.deleteRecursively()
    }

    @Test
    fun mainActivitySurvivesStopAndResumeLifecycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.moveToState(Lifecycle.State.CREATED)
            assertEquals(Lifecycle.State.CREATED, scenario.state)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun llamaJniLibraryLoadsAndNullHandleOperationsAreSafe() {
        JniLlamaNativeBridge.cancel(0L)
        JniLlamaNativeBridge.unloadModel(0L)
        assertFalse(Thread.currentThread().isInterrupted)
    }

    @Test
    fun nativeTextToSpeechInitializesWhenAvailable() {
        val initialized = CountDownLatch(1)
        var initializationStatus = TextToSpeech.ERROR
        val engine = TextToSpeech(context) { status ->
            initializationStatus = status
            initialized.countDown()
        }

        try {
            assertTrue("TextToSpeech initialization timed out", initialized.await(10, TimeUnit.SECONDS))
            assumeTrue("No native TextToSpeech engine is available", initializationStatus == TextToSpeech.SUCCESS)
            assertTrue("No default TextToSpeech voice is available", engine.voice != null)
        } finally {
            engine.stop()
            engine.shutdown()
        }
    }
}
