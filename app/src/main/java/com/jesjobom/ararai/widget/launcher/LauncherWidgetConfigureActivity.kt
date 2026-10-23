package com.jesjobom.ararai.widget.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.ArarAiApplication
import com.jesjobom.ararai.R
import com.jesjobom.ararai.settings.SharedPreferencesApplicationLanguagePreferenceStore
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import kotlinx.coroutines.launch

class LauncherWidgetConfigureActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = SharedPreferencesApplicationLanguagePreferenceStore(newBase).language
        super.attachBaseContext(SharedPreferencesApplicationLanguagePreferenceStore.localizedContext(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, result)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (info?.provider != LauncherWidgetUpdates.provider(this)) {
            finish()
            return
        }
        val repository = (application as ArarAiApplication).managedWidgetApplicationServices.repository
        setContent {
            var definitions by remember { mutableStateOf<List<ManagedWidgetDefinition>?>(null) }
            var choosing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                definitions = runCatching { repository.listDefinitions() }.getOrDefault(emptyList())
            }
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            getString(R.string.widget_launcher_choose_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        when {
                            definitions == null -> CircularProgressIndicator()
                            definitions.orEmpty().isEmpty() -> Text(getString(R.string.widget_launcher_choose_empty))
                            else -> definitions.orEmpty().forEach { definition ->
                                Button(
                                    onClick = {
                                        choosing = true
                                        scope.launch {
                                            LauncherWidgetBindings(this@LauncherWidgetConfigureActivity)
                                                .put(appWidgetId, definition.id)
                                            runCatching {
                                                LauncherWidgetRenderer.updateOne(
                                                    this@LauncherWidgetConfigureActivity,
                                                    appWidgetId,
                                                    repository,
                                                )
                                            }
                                            LauncherWidgetUpdates.request(this@LauncherWidgetConfigureActivity)
                                            setResult(Activity.RESULT_OK, result)
                                            finish()
                                        }
                                    },
                                    enabled = !choosing,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(definition.displayName) }
                            }
                        }
                    }
                }
            }
        }
    }
}
