package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.WEATHER_FORECAST_TOOL_NAME

/** A fixed weather layout keeps ordinary forecasts out of model-authored presentation code. */
internal object WidgetWeatherRenderArtifact {
    fun forecast(algorithm: WidgetAlgorithmArtifact): String? {
        val call = algorithm.toolCallSteps.singleOrNull()
        if (algorithm.renderCapability != WidgetRenderCapability.CustomSource ||
            call?.tool?.id != WEATHER_FORECAST_TOOL_NAME
        ) {
            return null
        }
        val alias = JsonPrimitive(call.id).toString()
        val source = """function render(runtime, outcomes, state) {
  var alias = $alias;
  if (!runtime.isSuccess(outcomes, alias)) return runtime.card("Weather forecast unavailable.");
  var data = outcomes[alias].payload;
  var rows = [typeof data.location === "string" ? data.location : "Weather forecast"];
  var rain = runtime.language === "pt" ? "chuva" : "rain";
  if (data.period === "now") {
    var current = data.current;
    if (!current || typeof current.temperatureC !== "number") return runtime.card("Weather data unavailable.");
    rows.push(current.temperatureC + " °C");
    if (typeof current.humidityPercent === "number") {
      rows.push((runtime.language === "pt" ? "Umidade " : "Humidity ") + current.humidityPercent + "%");
    }
    if (typeof current.feelsLikeC === "number") rows.push((runtime.language === "pt" ? "Sensação " : "Feels like ") + current.feelsLikeC + " °C");
    if (typeof current.precipitationMm === "number") rows.push(rain + " " + current.precipitationMm + " mm");
  } else {
    var days = data.days;
    if (!Array.isArray(days) || days.length === 0) return runtime.card("Weather data unavailable.");
    for (var i = 0; i < days.length && i < 7; i++) {
      var day = days[i];
      if (!day || typeof day.date !== "string" || typeof day.temperatureMaxC !== "number" || typeof day.temperatureMinC !== "number") continue;
      var line = day.date + "  " + day.temperatureMaxC + " / " + day.temperatureMinC + " °C";
      if (typeof day.precipitationProbabilityMaxPercent === "number") line += "  " + rain + " " + day.precipitationProbabilityMaxPercent + "%";
      rows.push(line);
    }
    if (rows.length === 1) return runtime.card("Weather data unavailable.");
  }
  return runtime.card(rows);
}"""
        return JsonObject().apply { addProperty("source", source) }.toString()
    }
}
