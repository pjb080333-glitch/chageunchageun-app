package com.chageunchageun.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.chageunchageun.app.di.VehicleProvider
import com.chageunchageun.app.ui.MainScreen
import com.chageunchageun.app.ui.MainViewModel
import com.chageunchageun.app.ui.theme.ChageunTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VehicleProvider.hasLive &&
            checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST_CALENDAR)
        }
        setContent { ChageunTheme { MainScreen(vm) } }
        if (savedInstanceState == null) handleDemoIntent(intent)
    }

    // 화면이 보이는 동안만 문·창문·등 상태 리스너를 등록한다(#003 A4).
    override fun onStart() {
        super.onStart()
        vm.onVisible(true)
    }

    override fun onStop() {
        vm.onVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDemoIntent(intent)
    }

    /**
     * 시연 녹화용(debug 빌드 전용). release 빌드에서는 무시한다.
     * adb shell am start -n <패키지>/com.chageunchageun.app.MainActivity
     *     --es demo_scenario WINTER_MORNING --es demo_action IGNITION_ON --es demo_tab HOME
     */
    private fun handleDemoIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val scenario = intent.getStringExtra(EXTRA_SCENARIO)
        val action = intent.getStringExtra(EXTRA_ACTION)
        val tab = intent.getStringExtra(EXTRA_TAB)
        // 녹화용: --ez demo_clean true 면 debug 상태 칩("말하는 중" 등)을 숨긴다(#004).
        val clean = intent.getBooleanExtra(EXTRA_CLEAN, false)
        if (scenario == null && action == null && tab == null && !clean) return
        vm.runDemo(scenario, action, tab, clean)
    }

    private companion object {
        const val REQUEST_CALENDAR = 1
        const val EXTRA_SCENARIO = "demo_scenario"
        const val EXTRA_ACTION = "demo_action"
        const val EXTRA_TAB = "demo_tab"
        const val EXTRA_CLEAN = "demo_clean"
    }
}
