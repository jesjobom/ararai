package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.tools.WEATHER_FORECAST_TOOL_NAME
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult

internal const val WEATHER_ATTRIBUTION_URL = "https://open-meteo.com/"

internal fun WidgetProgramRevision.usesWeatherTool(): Boolean {
    val parsed = WidgetProgramParser.parse(manifestJson, source) as? WidgetProgramValidationResult.Valid
    return parsed?.program?.manifest?.capabilities?.tools?.any { it.id == WEATHER_FORECAST_TOOL_NAME } == true
}
