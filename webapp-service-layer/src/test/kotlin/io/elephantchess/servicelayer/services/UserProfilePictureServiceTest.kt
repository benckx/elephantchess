package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.config.DbConfig
import io.elephantchess.config.PropertiesFile
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.clients.DigitalOceanSpacesClient
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfilePictureServiceTest {

    private val spacesClient = mock<DigitalOceanSpacesClient>()
    private val userDaoService = mock<UserDaoService>()
    private val propertiesFile = mock<PropertiesFile>()
    private fun testAppConfig(profile: String) = AppConfig(
        profile = profile,
        webHost = "localhost",
        isMinificationEnabled = false,
        isGoogleAnalyticsEnabled = false,
        isCookieConsentBannerEnabled = false,
        isDockerized = false,
        sendMailNotifications = false,
        isCachingEnabled = false,
        isEnginePoolEnabled = false,
        enginesThreads = 1,
        pikafishVersion = "",
        fairyStockfishVersion = "",
        dbConfig = DbConfig("test", "jdbc:postgresql://localhost/test", "postgres", "postgres"),
        parseUserAgent = false,
        disabledBatches = emptyList(),
        cdnEnabled = true,
        properties = propertiesFile,
    )

    private val service = UserProfilePictureService(testAppConfig("local-backup"), spacesClient, userDaoService)

    @Test
    fun `profilePictureUrl returns null when extension is missing`() {
        assertNull(UserProfilePictureService.profilePictureUrl("local-backup", "user-1", null))
    }

    @Test
    fun `invalid profile segment is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            UserProfilePictureService(testAppConfig("../prod"), spacesClient, userDaoService)
        }
    }

    @Test
    fun `normalizeProfilePicture crops image to 100 by 100`() {
        val source = BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                source.setRGB(x, y, if (x < 100) Color.RED.rgb else Color.BLUE.rgb)
            }
        }

        val output = ByteArrayOutputStream()
        ImageIO.write(source, "png", output)

        val normalized = service.normalizeProfilePicture(output.toByteArray(), "png")
        val image = ImageIO.read(ByteArrayInputStream(normalized))

        assertEquals(100, image.width)
        assertEquals(100, image.height)
        assertTrue(Color(image.getRGB(10, 50)).red > Color(image.getRGB(10, 50)).blue)
        assertTrue(Color(image.getRGB(90, 50)).blue > Color(image.getRGB(90, 50)).red)
    }

    @Test
    fun `uploadProfilePicture uploads normalized picture and persists extension`() = runTest {
        whenever(spacesClient.uploadBytes(any(), any(), any(), any())).thenReturn(true)

        val source = BufferedImage(140, 90, BufferedImage.TYPE_INT_RGB)
        val output = ByteArrayOutputStream()
        ImageIO.write(source, "png", output)

        val url = service.uploadProfilePicture("user-1", "photo.png", output.toByteArray())

        assertEquals("https://cdn.elephantchess.io/local-backup/profile-pictures/user-1.png", url)
        verify(userDaoService).updateProfilePictureExtension("user-1", "png")
        verify(spacesClient).uploadBytes(
            check { bytes ->
                val image = ImageIO.read(ByteArrayInputStream(bytes))
                assertEquals(100, image.width)
                assertEquals(100, image.height)
            },
            eq("local-backup/profile-pictures/user-1.png"),
            eq("image/png"),
            eq("public-read"),
        )
    }
}
