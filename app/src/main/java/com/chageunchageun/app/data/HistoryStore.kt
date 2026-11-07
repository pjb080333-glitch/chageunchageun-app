package com.chageunchageun.app.data

import android.content.Context
import com.chageunchageun.app.engine.LampAnswer
import com.chageunchageun.app.engine.MaintenanceRecord
import com.chageunchageun.app.engine.TirePoint
import com.chageunchageun.app.engine.TripLog
import com.chageunchageun.app.engine.WarningEvent
import com.chageunchageun.app.model.Lamp
import com.chageunchageun.app.model.Wheel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LampRecord(val timeMillis: Long, val lamp: Lamp, val answer: LampAnswer)
data class ChecklistRecord(val timeMillis: Long, val phase: String, val itemId: String, val ok: Boolean)

/** 점검 이력의 메모리 표현. 저장 형식은 JSON 파일 하나 (차량 내 저장). */
data class History(
    val maintenance: MutableList<MaintenanceRecord> = mutableListOf(),
    val tirePoints: MutableList<TirePoint> = mutableListOf(),
    val warningEvents: MutableList<WarningEvent> = mutableListOf(),
    val trips: MutableList<TripLog> = mutableListOf(),
    val lamps: MutableList<LampRecord> = mutableListOf(),
    val checklist: MutableList<ChecklistRecord> = mutableListOf(),
    var lastDriveEndMillis: Long? = null,
    var driveStartOdometerKm: Double? = null,
)

/**
 * 점검 이력 저장소. 운전 기록은 차량 안에만 저장한다.
 * TODO(가족 공유): 동의한 가족에게만 Sync SDK(SyncDocument)로 요약을 동기화.
 */
class HistoryStore(val file: File) {

    /** 연동은 history.json, 데모는 history_demo.json (#005 H4: 섞지 않음). */
    constructor(context: Context, fileName: String = HistoryFiles.LIVE) : this(File(context.filesDir, fileName))

    var data: History = load()
        private set

    fun save() {
        prune()
        file.writeText(toJson(data).toString())
    }

    fun reset(newData: History) {
        data = newData
        save()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        data.tirePoints.removeAll { it.timeMillis < cutoff }
        data.warningEvents.removeAll { it.timeMillis < cutoff }
        while (data.trips.size > 200) data.trips.removeAt(0)
    }

    private fun load(): History = runCatching {
        if (file.exists()) fromJson(JSONObject(file.readText())) else History()
    }.getOrElse { History() }

    private fun toJson(h: History) = JSONObject().apply {
        put("maintenance", JSONArray(h.maintenance.map {
            JSONObject().put("itemId", it.itemId).put("km", it.doneAtKm).put("t", it.doneAtMillis)
        }))
        put("tirePoints", JSONArray(h.tirePoints.map {
            JSONObject().put("t", it.timeMillis).put("w", it.wheel.name).put("kpa", it.kpa.toDouble())
        }))
        put("warningEvents", JSONArray(h.warningEvents.map {
            JSONObject().put("t", it.timeMillis).put("code", it.code)
        }))
        put("trips", JSONArray(h.trips.map {
            JSONObject().put("t", it.startMillis).put("km", it.distanceKm).put("hb", it.hardBrakes)
        }))
        put("lamps", JSONArray(h.lamps.map {
            JSONObject().put("t", it.timeMillis).put("lamp", it.lamp.name).put("a", it.answer.name)
        }))
        put("checklist", JSONArray(h.checklist.map {
            JSONObject().put("t", it.timeMillis).put("phase", it.phase).put("id", it.itemId).put("ok", it.ok)
        }))
        h.lastDriveEndMillis?.let { put("lastDriveEnd", it) }
        h.driveStartOdometerKm?.let { put("driveStartOdo", it) }
    }

    private fun fromJson(o: JSONObject) = History(
        maintenance = o.objects("maintenance").map {
            MaintenanceRecord(it.getString("itemId"), it.getDouble("km"), it.getLong("t"))
        }.toMutableList(),
        tirePoints = o.objects("tirePoints").map {
            TirePoint(it.getLong("t"), Wheel.valueOf(it.getString("w")), it.getDouble("kpa").toFloat())
        }.toMutableList(),
        warningEvents = o.objects("warningEvents").map {
            WarningEvent(it.getLong("t"), it.getString("code"))
        }.toMutableList(),
        trips = o.objects("trips").map {
            TripLog(it.getLong("t"), it.getDouble("km"), it.getInt("hb"))
        }.toMutableList(),
        lamps = o.objects("lamps").map {
            LampRecord(it.getLong("t"), Lamp.valueOf(it.getString("lamp")), LampAnswer.valueOf(it.getString("a")))
        }.toMutableList(),
        checklist = o.objects("checklist").map {
            ChecklistRecord(it.getLong("t"), it.getString("phase"), it.getString("id"), it.getBoolean("ok"))
        }.toMutableList(),
        lastDriveEndMillis = if (o.has("lastDriveEnd")) o.getLong("lastDriveEnd") else null,
        driveStartOdometerKm = if (o.has("driveStartOdo")) o.getDouble("driveStartOdo") else null,
    )

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
}
