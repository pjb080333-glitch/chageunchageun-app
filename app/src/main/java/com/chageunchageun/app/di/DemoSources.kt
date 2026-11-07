package com.chageunchageun.app.di

import android.content.Context
import com.chageunchageun.app.data.DataSources
import com.chageunchageun.app.data.HistoryFiles
import com.chageunchageun.app.data.HistoryStore
import com.chageunchageun.app.data.MockNavigationSource
import com.chageunchageun.app.data.MockScheduleSource
import com.chageunchageun.app.data.MockVehicleDataSource
import com.chageunchageun.app.data.NoWeather

/**
 * 데모 모드 데이터 묶음 (#005): 차량·일정·내비·날씨를 시나리오 목업으로. 이전 데모 APK 와 같은 구성.
 * 이력은 별도 파일(history_demo.json) — 연동 이력과 섞지 않는다.
 */
fun demoSources(context: Context): DataSources = DataSources(
    vehicle = MockVehicleDataSource(),
    navigation = MockNavigationSource(),
    schedule = MockScheduleSource(),
    weather = NoWeather,
    history = HistoryStore(context, HistoryFiles.DEMO),
    isDemo = true,
)
