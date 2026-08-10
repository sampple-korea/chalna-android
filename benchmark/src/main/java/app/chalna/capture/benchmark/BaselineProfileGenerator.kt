package app.chalna.capture.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun criticalUserJourneys() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
            filterPredicate = { profileRule -> profileRule.contains(APP_PROFILE_PREFIX) },
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "app.chalna.capture"
        const val APP_PROFILE_PREFIX = "Lapp/chalna/capture/"
    }
}
