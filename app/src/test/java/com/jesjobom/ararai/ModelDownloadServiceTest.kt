package com.jesjobom.ararai

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Looper
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.ManagedModelItem
import com.jesjobom.ararai.model.ModelCatalogState
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelDownloadServiceController
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelDownloadServiceTest {
    @Test
    fun `create enters foreground before receiving a command`() {
        service().use { harness ->
            val notification = shadowOf(harness.service).lastForegroundNotification

            assertNotNull(notification)
            assertEquals("Preparing model download", notification.extras.getString(Notification.EXTRA_TITLE))
            assertTrue(harness.fake.downloads.isEmpty())
        }
    }

    @Test
    fun `null and invalid starts are controlled and non sticky`() {
        service().use { harness ->
            assertEquals(Service.START_NOT_STICKY, harness.service.onStartCommand(null, 0, 1))
            assertEquals(
                Service.START_NOT_STICKY,
                harness.service.onStartCommand(Intent("invalid"), 0, 2),
            )
            assertTrue(harness.fake.downloads.isEmpty())
            assertFalse(shadowOf(harness.service).isStoppedBySelf)
        }
    }

    @Test
    fun `download redelivery owns transfer once and destruction cancels once`() {
        service().use { harness ->
            val intent = ModelDownloadService.downloadIntent(harness.service, MODEL_A, replaceExisting = true)

            assertEquals(Service.START_REDELIVER_INTENT, harness.service.onStartCommand(intent, 0, 1))
            harness.fake.downloading(MODEL_A, bytes = 10, total = 100)
            idleMainLooper()
            assertEquals(Service.START_REDELIVER_INTENT, harness.service.onStartCommand(intent, 0, 2))

            assertEquals(listOf(MODEL_A to true, MODEL_A to true), harness.fake.downloads)
            harness.destroy()
            assertEquals(listOf(MODEL_A), harness.fake.cancellations)
        }
    }

    @Test
    fun `progress updates notification and completion stops service`() {
        service(elapsedRealtime = 1_000L).use { harness ->
            harness.service.onStartCommand(
                ModelDownloadService.downloadIntent(harness.service, MODEL_A, replaceExisting = false),
                0,
                1,
            )
            harness.fake.downloading(MODEL_A, bytes = 25, total = 100)
            idleMainLooper()

            val manager = harness.service.getSystemService(NotificationManager::class.java)
            val notification = shadowOf(manager).getNotification(NOTIFICATION_ID)
            assertEquals("Downloading Model A", notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(25, notification.extras.getInt(Notification.EXTRA_PROGRESS))

            harness.fake.complete(MODEL_A)
            idleMainLooper()

            assertTrue(shadowOf(harness.service).isStoppedBySelf)
            assertTrue(shadowOf(harness.service).isForegroundStopped)
        }
    }

    @Test
    fun `cancel command cancels owned transfer and stops after state settles`() {
        service().use { harness ->
            harness.service.onStartCommand(
                ModelDownloadService.downloadIntent(harness.service, MODEL_A, replaceExisting = false),
                0,
                1,
            )
            harness.fake.downloading(MODEL_A, 1, 10)
            idleMainLooper()

            val result =
                harness.service.onStartCommand(
                    ModelDownloadService.cancelIntent(harness.service, MODEL_A),
                    0,
                    2,
                )
            harness.fake.complete(MODEL_A)
            idleMainLooper()

            assertEquals(Service.START_REDELIVER_INTENT, result)
            assertEquals(listOf(MODEL_A), harness.fake.cancellations)
            assertTrue(shadowOf(harness.service).isStoppedBySelf)
        }
    }

    @Test
    fun `completion waits until every owned transfer finishes`() {
        service().use { harness ->
            listOf(MODEL_A, MODEL_B).forEachIndexed { index, modelId ->
                harness.service.onStartCommand(
                    ModelDownloadService.downloadIntent(harness.service, modelId, replaceExisting = false),
                    0,
                    index + 1,
                )
                harness.fake.downloading(modelId, 1, 10)
                idleMainLooper()
            }

            harness.fake.complete(MODEL_A)
            idleMainLooper()
            assertFalse(shadowOf(harness.service).isStoppedBySelf)

            harness.fake.complete(MODEL_B)
            idleMainLooper()
            assertTrue(shadowOf(harness.service).isStoppedBySelf)
        }
    }

    @Test
    fun `failed download releases ownership and stops service`() {
        service().use { harness ->
            harness.service.onStartCommand(
                ModelDownloadService.downloadIntent(harness.service, MODEL_A, replaceExisting = false),
                0,
                1,
            )
            harness.fake.downloading(MODEL_A, 1, 10)
            idleMainLooper()

            harness.fake.fail(MODEL_A)
            idleMainLooper()

            assertTrue(shadowOf(harness.service).isStoppedBySelf)
        }
    }

    @Test
    fun `destruction cancels each distinct owned transfer`() {
        service().use { harness ->
            listOf(MODEL_A, MODEL_B).forEachIndexed { index, modelId ->
                harness.service.onStartCommand(
                    ModelDownloadService.downloadIntent(harness.service, modelId, replaceExisting = false),
                    0,
                    index + 1,
                )
            }

            harness.destroy()

            assertEquals(listOf(MODEL_A, MODEL_B), harness.fake.cancellations)
        }
    }

    private fun service(elapsedRealtime: Long = 0L): ServiceHarness {
        val fake = FakeController()
        val controller = Robolectric.buildService(ModelDownloadService::class.java)
        val service =
            controller.get().apply {
                controllerOverride = fake
                this.elapsedRealtime = { elapsedRealtime }
            }
        controller.create()
        idleMainLooper()
        return ServiceHarness(controller, service, fake)
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class ServiceHarness(
        private val controller: ServiceController<ModelDownloadService>,
        val service: ModelDownloadService,
        val fake: FakeController,
    ) : AutoCloseable {
        private var destroyed = false

        fun destroy() {
            if (!destroyed) {
                destroyed = true
                controller.destroy()
            }
        }

        override fun close() = destroy()
    }

    private class FakeController : ModelDownloadServiceController {
        private val configs = listOf(config(MODEL_A, "Model A"), config(MODEL_B, "Model B"))
        private val mutableState =
            MutableStateFlow(
                ModelCatalogState(
                    models = configs.map { ManagedModelItem(it, ModelStartupState.Missing) },
                    selectedModelId = MODEL_A,
                ),
            )
        override val state: StateFlow<ModelCatalogState> = mutableState
        val downloads = mutableListOf<Pair<String, Boolean>>()
        val cancellations = mutableListOf<String>()

        override fun executeBackgroundDownload(
            modelId: String,
            replaceExisting: Boolean,
        ) {
            downloads += modelId to replaceExisting
        }

        override fun executeBackgroundCancel(modelId: String) {
            cancellations += modelId
        }

        fun downloading(
            modelId: String,
            bytes: Long,
            total: Long,
        ) {
            mutableState.value = state(modelId to ModelStartupState.Downloading(bytes, total))
        }

        fun complete(modelId: String) {
            mutableState.value = state(modelId to ModelStartupState.Missing)
        }

        fun fail(modelId: String) {
            mutableState.value = state(modelId to ModelStartupState.Failed("failed"))
        }

        private fun state(vararg overrides: Pair<String, ModelStartupState>): ModelCatalogState {
            val stateById = overrides.toMap()
            return ModelCatalogState(
                models =
                configs.map { config ->
                    ManagedModelItem(config, stateById[config.id] ?: currentState(config.id))
                },
                selectedModelId = MODEL_A,
            )
        }

        private fun currentState(modelId: String): ModelStartupState = mutableState.value.models
            .firstOrNull { it.config.id == modelId }
            ?.state ?: ModelStartupState.Missing
    }

    private companion object {
        const val MODEL_A = "model-a"
        const val MODEL_B = "model-b"
        const val NOTIFICATION_ID = 1001

        fun config(
            id: String,
            name: String,
        ): ModelConfig = ModelConfig(
            id = id,
            name = name,
            url = "https://example.com/$id.gguf",
            fileName = "$id.gguf",
            relativePath = "models/$id.gguf",
            sha256 = "00",
            expectedBytes = 100,
            inference = InferenceConfig(128, 32, 0.7f, 0.9f),
        )
    }
}
