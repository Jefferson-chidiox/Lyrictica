package com.lyrictica.baselineprofile

import android.Manifest
import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.lyrictica"
private const val UI_TIMEOUT_MS = 8_000L

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreNavigation() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME
    ) {
        grantCorePermissions()
        pressHome()
        startActivityAndWait()
        dismissPermissionDialogsIfNeeded()

        waitForObject(By.desc("Home"))
        scrollCurrentScreen()
        if (tapIfPresent(By.desc("Settings"))) {
            device.pressBack()
            device.waitForIdle()
        }

        navigateToDestination("Explore")
        scrollCurrentScreen()
        navigateToDestination("Games")
        scrollCurrentScreen()
        navigateToDestination("Playlists")
        scrollCurrentScreen()
        navigateToDestination("Home")
    }

    private fun MacrobenchmarkScope.navigateToDestination(contentDescription: String) {
        waitForObject(By.desc(contentDescription)).click()
        device.wait(Until.hasObject(By.desc(contentDescription)), UI_TIMEOUT_MS)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollCurrentScreen() {
        val centerX = device.displayWidth / 2
        val startY = (device.displayHeight * 0.78f).toInt()
        val endY = (device.displayHeight * 0.28f).toInt()
        device.swipe(centerX, startY, centerX, endY, 18)
        device.waitForIdle()
        device.swipe(centerX, endY, centerX, startY, 18)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.grantCorePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissions.forEach { permission ->
            runCatching {
                device.executeShellCommand("pm grant $PACKAGE_NAME $permission")
            }
        }
    }

    private fun MacrobenchmarkScope.dismissPermissionDialogsIfNeeded() {
        repeat(3) {
            val allowButton = permissionSelectors
                .firstNotNullOfOrNull { selector -> device.findObject(selector) }
                ?: return
            allowButton.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.waitForObject(selector: BySelector): UiObject2 {
        check(device.wait(Until.hasObject(selector), UI_TIMEOUT_MS)) {
            "Timed out waiting for UI selector: $selector"
        }
        return requireNotNull(device.findObject(selector)) {
            "Selector became visible but could not be resolved: $selector"
        }
    }

    private fun MacrobenchmarkScope.tapIfPresent(selector: BySelector): Boolean {
        val target = device.findObject(selector) ?: return false
        target.click()
        device.waitForIdle()
        return true
    }

    private companion object {
        val permissionSelectors = listOf(
            By.res("com.android.permissioncontroller", "permission_allow_button"),
            By.res("com.android.permissioncontroller", "permission_allow_foreground_only_button"),
            By.res("com.android.packageinstaller", "permission_allow_button"),
            By.text("Allow"),
            By.text("Allow all"),
            By.text("While using the app")
        )
    }
}
