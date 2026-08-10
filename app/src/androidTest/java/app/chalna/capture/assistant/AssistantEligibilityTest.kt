package app.chalna.capture.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.service.voice.VoiceInteractionService
import android.speech.RecognitionService
import androidx.test.platform.app.InstrumentationRegistry
import app.chalna.capture.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class AssistantEligibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun packagePublishesCompleteAssistantRoleMetadata() {
        val service = packageManager.queryIntentServices(
            Intent(VoiceInteractionService.SERVICE_INTERFACE).setPackage(context.packageName),
            PackageManager.GET_META_DATA,
        ).single().serviceInfo

        assertEquals(Manifest.permission.BIND_VOICE_INTERACTION, service.permission)
        val metadata = requireNotNull(
            service.loadXmlMetaData(packageManager, VoiceInteractionService.SERVICE_META_DATA),
        )
        metadata.use { parser ->
            while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                parser.next()
            }
            assertEquals("voice-interaction-service", parser.name)
            assertTrue(parser.attribute("sessionService").isNotBlank())
            assertTrue(parser.attribute("recognitionService").isNotBlank())
            assertTrue(parser.getAttributeBooleanValue(ANDROID_NAMESPACE, "supportsAssist", false))
        }
    }

    @Test
    fun declaredRecognitionServiceIsSystemBindOnly() {
        val query = Intent(RecognitionService.SERVICE_INTERFACE)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .setPackage(context.packageName)
        val service = packageManager.queryIntentServices(
            query,
            PackageManager.GET_META_DATA,
        ).single().serviceInfo

        assertEquals(BIND_SPEECH_RECOGNITION_SERVICE, service.permission)
        assertEquals(ChalnaRecognitionService::class.java.name, service.name)
        requireNotNull(service.loadXmlMetaData(packageManager, RecognitionService.SERVICE_META_DATA)).use { metadata ->
            while (metadata.eventType != XmlPullParser.START_TAG && metadata.eventType != XmlPullParser.END_DOCUMENT) {
                metadata.next()
            }
            assertEquals("recognition-service", metadata.name)
            assertEquals(
                MainActivity::class.java.name,
                metadata.getAttributeValue(ANDROID_NAMESPACE, "settingsActivity"),
            )
        }
    }

    @Test
    fun assistantRoleExistsOnSupportedAndroid() {
        assertTrue(context.getSystemService(RoleManager::class.java).isRoleAvailable(RoleManager.ROLE_ASSISTANT))
    }

    private fun XmlPullParser.attribute(name: String): String =
        getAttributeValue(ANDROID_NAMESPACE, name).orEmpty()

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val BIND_SPEECH_RECOGNITION_SERVICE = "android.permission.BIND_SPEECH_RECOGNITION_SERVICE"
    }
}
