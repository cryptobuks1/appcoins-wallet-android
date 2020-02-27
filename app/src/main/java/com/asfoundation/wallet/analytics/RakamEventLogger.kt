package com.asfoundation.wallet.analytics

import android.util.Log
import cm.aptoide.analytics.AnalyticsManager
import cm.aptoide.analytics.EventLogger
import io.rakam.api.Rakam
import org.json.JSONException
import org.json.JSONObject

class RakamEventLogger : EventLogger {

  companion object {
    const val TAG = "RakamEventLogger"
  }

  override fun setup() {

  }

  override fun log(eventName: String, data: Map<String, Any>?,
                   action: AnalyticsManager.Action, context: String) {
    if (data != null) {
      Rakam.getInstance()
          .logEvent(eventName, mapToJsonObject(data))
    } else {
      Rakam.getInstance()
          .logEvent(eventName)
    }
    Log.d(TAG, "log() called with: "
        + "eventName = ["
        + eventName
        + "], data = ["
        + data
        + "], action = ["
        + action
        + "], context = ["
        + context
        + "]")

  }

  private fun mapToJsonObject(data: Map<String, Any>): JSONObject {
    val eventData = JSONObject()

    for (entry in data.entries) {
      try {
        eventData.put(entry.key, entry.value
            .toString())
      } catch (e: JSONException) {
        e.printStackTrace()
      }
    }
    return eventData
  }
}