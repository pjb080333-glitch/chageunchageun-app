package com.chageunchageun.app.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.util.Log
import com.chageunchageun.app.data.ActiveRoute
import com.chageunchageun.app.data.GeoPoint
import com.chageunchageun.app.data.NavigationSource
import com.chageunchageun.app.data.OsmPlaces
import com.chageunchageun.app.data.Place
import com.chageunchageun.app.data.RouteEstimate
import com.chageunchageun.app.data.WaypointResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * NaviHelper SDK 3.0.5 연동.
 * 출처: Pleos 개발자 문서 NaviHelper (initialization, destinations, waypoint-settings, event-listeners)와
 * 공식 샘플 pleos-sample-navi-app (requestTrip → onTripInfo). 시그니처는 SDK 클래스(javap)로 확인.
 *
 *  - 좌표: 일정 설명 칸의 geo:위도,경도 → 없으면 지오코딩(안드로이드 Geocoder → 없거나 실패하면 OpenStreetMap Nominatim)
 *  - estimate: requestTrip(TripRequest) → onTripInfo(distance, eta). 길안내를 시작하지 않고 거리·시간만 받는다.
 *  - startGuidance: requestRoute(RouteInfo)
 *  - currentLocation: getCurrentLocationInfo() → onCurrentLocationInfo
 *  - activeRoute (#003 B7): getRouteStateInfo() → onRouteStateInfo(isRouting 또는 naviMode=ROUTE_GUIDING) → getDestinationInfo() → onDestinationInfo
 *    (remainDistance=m, remainTime=초 — requestTrip 과 같은 단위로 가정, 에뮬레이터 내비 화면과 비교해 확인)
 *  - watchRoute: onRouteStarted 리스너 → activeRoute() 로 다시 읽어 전달
 *  - addWaypoint("주유소" 등): NaviHelper 에 장소 검색이 없어 OpenStreetMap Overpass 로 주변을 찾고
 *    addWaypoint(RequestWaypointInfo, WaypointIndex.FIRST) 로 넣는다. 실패하면 이름·거리만 보여 주고,
 *    검색 결과가 없으면 geo: 검색 인텐트를 받는 지도 앱을 연다.
 */
class PleosNavigationSource(context: Context) : NavigationSource {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val navi = NaviHelper(appContext)
    private val connected = CompletableDeferred<Boolean>()
    private var pendingTrip: CompletableDeferred<TripInfo?>? = null
    private var pendingLocation: CompletableDeferred<CurrentLocationInfo?>? = null
    private var pendingWaypoint: CompletableDeferred<Boolean>? = null
    private var pendingRouteState: CompletableDeferred<RouteStateInfo?>? = null
    private var pendingDestination: CompletableDeferred<DestinationInfo?>? = null
    private var routeWatcher: ((ActiveRoute) -> Unit)? = null
    private var lastLocation: GeoPoint? = null

    private val listener = object : NaviHelperEventListener {
        override fun onTripInfo(tripInfo: TripInfo) {
            Log.i(TAG, "onTripInfo $tripInfo")
            pendingTrip?.complete(tripInfo)
        }
        override fun onRoutingCompleted() {
            Log.i(TAG, "onRoutingCompleted")
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            Log.i(TAG, "onCurrentLocationInfo $currentLocationInfo")
            pendingLocation?.complete(currentLocationInfo)
        }
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            Log.i(TAG, "onWaypointChanged $waypointChangedInfo")
            pendingWaypoint?.complete(true)
        }
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            Log.i(TAG, "onWayPointInfo $waypointInfo")
            if (waypointInfo.isNotEmpty()) pendingWaypoint?.complete(true)
        }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            Log.i(TAG, "onRouteStateInfo $routeStateInfo")
            pendingRouteState?.complete(routeStateInfo)
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.i(TAG, "onDestinationInfo $destinationInfo")
            pendingDestination?.complete(destinationInfo)
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted $routeStartInfo")
            val cb = routeWatcher ?: return
            ioScope.launch {
                val route = activeRoute() ?: ActiveRoute(
                    Place(routeStartInfo.name),
                    routeStartInfo.distance / 1000.0,
                    (routeStartInfo.duration + 59) / 60,
                )
                cb(route)
            }
        }
        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error=$errorCode")
            pendingRouteState?.complete(null)
            pendingDestination?.complete(null)
            pendingTrip?.complete(null)
            pendingLocation?.complete(null)
            pendingWaypoint?.complete(false)
        }
    }

    init {
        runCatching {
            navi.initialize(
                {
                    Log.i(TAG, "NaviHelper 연결됨")
                    runCatching { navi.addListener(listener) }
                    connected.complete(true)
                },
                { t ->
                    Log.w(TAG, "NaviHelper 연결 실패", t)
                    connected.complete(false)
                },
            )
        }.onFailure {
            Log.w(TAG, "NaviHelper initialize 예외", it)
            connected.complete(false)
        }
    }

    private suspend fun ready(): Boolean = withTimeoutOrNull(CONNECT_MS) { connected.await() } == true

    override suspend fun estimate(destination: Place): RouteEstimate? {
        if (!ready()) return null
        val p = locate(destination) ?: return null
        val wait = CompletableDeferred<TripInfo?>()
        pendingTrip = wait
        runCatching {
            navi.requestTrip(TripRequest(latitude = p.lat, longitude = p.lon, poiId = "", poiName = destination.name, poiSubId = "0"))
        }.onFailure {
            Log.w(TAG, "requestTrip 실패", it)
            return null
        }
        val trip = withTimeoutOrNull(TRIP_MS) { wait.await() }
        pendingTrip = null
        trip ?: return null
        // 단위: distance=m, eta=초 (#003 B3 에서 내비 화면 값과 비교해 확인 → DECISIONS)
        val km = trip.distance / 1000.0
        val min = (trip.eta + 59) / 60
        if (km <= 0 || min <= 0 || min > 24 * 60) {
            Log.w(TAG, "trip 값 해석 불가 distance=${trip.distance} eta=${trip.eta}")
            return null
        }
        Log.i(TAG, "estimate '${destination.name}' $p -> ${"%.1f".format(km)}km ${min}분")
        return RouteEstimate(km, min)
    }

    override suspend fun startGuidance(destination: Place): Boolean {
        if (!ready()) return false
        val p = locate(destination) ?: return false
        return runCatching {
            navi.requestRoute(
                RouteInfo(
                    latitude = p.lat,
                    longitude = p.lon,
                    displayLongitude = null,
                    displayLatitude = null,
                    poiName = destination.name,
                    poiId = "",
                    poiSubId = "0",
                    address = destination.name,
                ),
            )
        }.onFailure { Log.w(TAG, "requestRoute 실패", it) }.isSuccess
    }

    override suspend fun currentLocation(): GeoPoint? {
        if (!ready()) return lastLocation
        val wait = CompletableDeferred<CurrentLocationInfo?>()
        pendingLocation = wait
        runCatching { navi.getCurrentLocationInfo() }.onFailure { Log.w(TAG, "getCurrentLocationInfo 실패", it) }
        val info = withTimeoutOrNull(LOCATION_MS) { wait.await() }
        pendingLocation = null
        val p = info?.let { GeoPoint(it.latitude, it.longitude) }?.takeIf { it.lat != 0.0 || it.lon != 0.0 }
        if (p != null) lastLocation = p
        return p ?: lastLocation
    }

    override suspend fun activeRoute(): ActiveRoute? {
        if (!ready()) return null
        val stateWait = CompletableDeferred<RouteStateInfo?>()
        pendingRouteState = stateWait
        runCatching { navi.getRouteStateInfo() }.onFailure { Log.w(TAG, "getRouteStateInfo 실패", it) }
        val state = withTimeoutOrNull(LOCATION_MS) { stateWait.await() }
        pendingRouteState = null
        // 에뮬레이터 확인(#003 B7): 길안내 중에도 isRouting=false, naviMode=ROUTE_GUIDING 이었다.
        // isRouting 은 "경로 계산 중"으로 보이므로 naviMode 로도 판단한다.
        val guiding = state != null && (state.isRouting || state.naviMode == NaviMode.ROUTE_GUIDING || state.naviMode == NaviMode.SIMULATION_GUIDING)
        if (!guiding) return null
        val destWait = CompletableDeferred<DestinationInfo?>()
        pendingDestination = destWait
        runCatching { navi.getDestinationInfo() }.onFailure { Log.w(TAG, "getDestinationInfo 실패", it) }
        val d = withTimeoutOrNull(LOCATION_MS) { destWait.await() }
        pendingDestination = null
        if (d == null || d.remainDistance <= 0) return null
        val point = GeoPoint(d.latitude, d.longitude).takeIf { it.lat != 0.0 || it.lon != 0.0 }
        val name = d.name.ifBlank { d.address }
        return ActiveRoute(Place(name, point), d.remainDistance / 1000.0, (d.remainTime + 59) / 60)
            .also { Log.i(TAG, "activeRoute $it") }
    }

    override fun watchRoute(onStarted: ((ActiveRoute) -> Unit)?) {
        routeWatcher = onStarted
    }

    override suspend fun addWaypoint(query: String, toward: Place?): WaypointResult {
        val here = currentLocation()
        val kind = OsmPlaces.kindOf(query)
        val best = here?.let { OsmPlaces.nearby(kind, it, toward?.let { t -> locate(t) }).firstOrNull() }
        if (best == null) {
            Log.i(TAG, "addWaypoint('$query'): 현재 위치=$here, 주변 검색 결과 없음 → 지도 앱")
            return openMapApp(query)
        }
        Log.i(TAG, "addWaypoint('$query') 후보 ${best.name} ${"%.1f".format(best.distanceKm)}km ${best.point}")
        val ok = ready() && run {
            val wait = CompletableDeferred<Boolean>()
            pendingWaypoint = wait
            val sent = runCatching {
                navi.addWaypoint(
                    RequestWaypointInfo(
                        latitude = best.point.lat,
                        longitude = best.point.lon,
                        poiName = best.name,
                        waypointIndex = WaypointIndex.FIRST,
                        poiId = "",
                        address = best.name,
                        poiSubId = "0",
                    ),
                )
                navi.getWaypointInfo()
            }.onFailure { Log.w(TAG, "addWaypoint 실패", it) }.isSuccess
            val r = sent && withTimeoutOrNull(WAYPOINT_MS) { wait.await() } == true
            pendingWaypoint = null
            r
        }
        return if (ok) WaypointResult.Added(best.name, best.distanceKm) else WaypointResult.ShownOnly(best.name, best.distanceKm)
    }

    /** 표준 지도 검색 인텐트(geo:0,0?q=검색어)를 받는 앱이 있으면 연다. 특정 내비 앱 전용 스킴은 쓰지 않는다. */
    private fun openMapApp(query: String): WaypointResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val handler = appContext.packageManager.resolveActivity(intent, 0)
        Log.i(TAG, "geo 인텐트 처리 앱: ${handler?.activityInfo?.packageName}")
        if (handler == null) return WaypointResult.NotFound
        return if (runCatching { appContext.startActivity(intent) }.isSuccess) WaypointResult.OpenedMapApp else WaypointResult.NotFound
    }

    /** 좌표가 있으면 그대로, 없으면 지오코딩. */
    private suspend fun locate(place: Place): GeoPoint? = place.point ?: geocode(place.name)

    /**
     * 안드로이드 Geocoder(구글 서비스가 없으면 동작하지 않음) → 실패하면 Nominatim.
     * Pleos 에뮬레이터에서 Geocoder 호출이 돌아오지 않은 적이 있어(#003 B3) 별도 작업으로 돌리고 5초만 기다린다.
     */
    @Suppress("DEPRECATION")
    private suspend fun geocode(name: String): GeoPoint? {
        val present = Geocoder.isPresent()
        Log.i(TAG, "Geocoder.isPresent()=$present")
        val android = if (!present) {
            null
        } else {
            val job = ioScope.async {
                runCatching { Geocoder(appContext, Locale.KOREA).getFromLocationName(name, 1)?.firstOrNull() }
                    .onFailure { Log.w(TAG, "Geocoder 실패 '$name': ${it.message}") }
                    .getOrNull()
                    ?.let { GeoPoint(it.latitude, it.longitude) }
            }
            withTimeoutOrNull(GEOCODER_MS) { job.await() }.also {
                if (it == null) Log.i(TAG, "Geocoder '$name' 결과 없음·시간 초과 → Nominatim")
            }
        }
        if (android != null) {
            Log.i(TAG, "Geocoder '$name' -> $android")
            return android
        }
        return OsmPlaces.geocode(name)
    }

    override fun release() {
        routeWatcher = null
        ioScope.cancel()
        runCatching { navi.removeListener(listener) }
        runCatching { navi.release() }
    }

    private companion object {
        const val TAG = "ChageunNavi"
        const val CONNECT_MS = 3_000L
        const val TRIP_MS = 5_000L
        const val LOCATION_MS = 3_000L
        const val WAYPOINT_MS = 3_000L
        const val GEOCODER_MS = 5_000L
    }
}
