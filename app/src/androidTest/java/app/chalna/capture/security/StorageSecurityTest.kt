package app.chalna.capture.security

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSecurityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fileProviderRejectsFilesOutsideNarrowVaultPath() {
        val outside = File(context.filesDir, "outside.mp4").apply { writeBytes(byteArrayOf(1)) }
        try {
            val result = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.files", outside)
            }
            assertTrue(result.isFailure)
        } finally {
            outside.delete()
        }
    }

    @Test fun manifestDoesNotGrantBroadMediaOrNetworkAccess() {
        val requested = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_PERMISSIONS.toLong()),
        ).requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.INTERNET" !in requested)
        assertTrue("android.permission.READ_MEDIA_VIDEO" !in requested)
        assertTrue("android.permission.MANAGE_EXTERNAL_STORAGE" !in requested)
    }
}
