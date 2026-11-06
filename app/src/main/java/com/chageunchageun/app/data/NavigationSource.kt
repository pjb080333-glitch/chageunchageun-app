package com.chageunchageun.app.data

data class RouteEstimate(val distanceKm: Double, val durationMin: Int)

data class GeoPoint(val lat: Double, val lon: Double)

/** 내장 내비에 설정돼 길안내 중인 목적지와 남은 거리·시간 (#003 B7). */
data class ActiveRoute(val place: Place, val remainKm: Double, val remainMin: Int)

/** 목적지·경유지. point 가 없으면 이름으로 좌표를 찾는다(지오코딩). */
data class Place(val name: String, val point: GeoPoint? = null)

/** "주유소·정비소 경유" 결과. 화면 안내 문구가 결과마다 다르다. */
sealed interface WaypointResult {
    /** 내비 경유지로 넣었다. distanceKm = 현재 위치에서 직선거리(모르면 null). */
    data class Added(val name: String, val distanceKm: Double?) : WaypointResult

    /** 장소는 찾았지만 경유지로 넣지 못해 화면에 이름·거리만 보여 준다. */
    data class ShownOnly(val name: String, val distanceKm: Double?) : WaypointResult

    /** 장소 검색을 못 해 설치된 지도·내비 앱을 "주유소" 같은 검색어로 열었다. */
    data object OpenedMapApp : WaypointResult

    /** 아무것도 못 했다 → "내비게이션에서 '주유소'를 검색해 주세요". */
    data object NotFound : WaypointResult
}

/**
 * 내비 연동 계층.
 *  - pleos 플레이버: NaviHelper SDK (requestTrip·requestRoute·addWaypoint·getCurrentLocationInfo)
 *    + 장소 검색은 외부 공개 데이터(OpenStreetMap)
 *  - mock 플레이버: 고정 값
 */
interface NavigationSource {
    suspend fun estimate(destination: Place): RouteEstimate?
    suspend fun startGuidance(destination: Place): Boolean

    /** @param query "주유소", "자동차 정비소" 같은 검색어 @param toward 목적지(있으면 그 방향에 있는 곳을 우선) */
    suspend fun addWaypoint(query: String, toward: Place?): WaypointResult

    /** 차량 현재 위치. 모르면 null. */
    suspend fun currentLocation(): GeoPoint? = null

    /** 내장 내비가 길안내 중이면 그 목적지. 안내 중이 아니거나 알 수 없으면 null. */
    suspend fun activeRoute(): ActiveRoute? = null

    /** 운전자가 내비에서 목적지를 정해 길안내가 시작될 때마다 호출. null 이면 해제. */
    fun watchRoute(onStarted: ((ActiveRoute) -> Unit)?) = Unit

    fun release() = Unit
}

class MockNavigationSource : NavigationSource {
    /** 데모 시나리오에 맞춘 고정 경로: 약 32km, 45분. */
    override suspend fun estimate(destination: Place) = RouteEstimate(distanceKm = 32.0, durationMin = 45)
    override suspend fun startGuidance(destination: Place) = true
    override suspend fun addWaypoint(query: String, toward: Place?): WaypointResult = WaypointResult.Added(query, null)
}
