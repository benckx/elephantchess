package io.elephantchess.servicelayer.services

import io.elephantchess.config.AppConfig
import io.elephantchess.db.services.UserDaoService
import io.elephantchess.servicelayer.clients.DigitalOceanSpacesClient
import io.elephantchess.servicelayer.exceptions.NotAcceptableException
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class UserProfilePictureService(
    appConfig: AppConfig,
    private val spacesClient: DigitalOceanSpacesClient,
    private val userDaoService: UserDaoService,
) {

    private val profile = requireValidProfileSegment(appConfig.profile)

    private fun profilePictureKey(userId: String, extension: String): String {
        return "$profile/$PROFILE_PICTURE_FOLDER/$userId.$extension"
    }

    /**
     * Validate and normalize a profile picture upload, store it on the CDN-backed object storage,
     * persist the chosen file extension for the user, and return the public CDN URL.
     *
     * @throws NotAcceptableException when the file is too large, has an unsupported extension,
     * or cannot be decoded into a supported image format.
     */
    suspend fun uploadProfilePicture(userId: String, originalFileName: String, bytes: ByteArray): String {
        if (bytes.size > PROFILE_PICTURE_MAX_BYTES) {
            throw NotAcceptableException("Profile picture limited to ${PROFILE_PICTURE_MAX_BYTES / 1024}KB")
        }

        val extension = requireSupportedExtension(originalFileName)
        val normalizedBytes = normalizeProfilePicture(bytes, extension)
        if (normalizedBytes.size > PROFILE_PICTURE_MAX_BYTES) {
            throw NotAcceptableException("Profile picture limited to ${PROFILE_PICTURE_MAX_BYTES / 1024}KB")
        }

        val key = profilePictureKey(userId, extension)
        val uploaded = spacesClient.uploadBytes(
            bytes = normalizedBytes,
            key = key,
            contentType = contentTypeFor(extension),
            acl = "public-read",
        )

        if (!uploaded) {
            throw IllegalStateException("Unable to upload profile picture")
        }

        userDaoService.updateProfilePictureExtension(userId, extension)
        return profilePictureUrl(profile, userId, extension)!!
    }

    internal fun normalizeProfilePicture(bytes: ByteArray, extension: String): ByteArray {
        val sourceImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw NotAcceptableException("Unable to read image file - the file may be corrupted or in an unsupported format")

        val size = minOf(sourceImage.width, sourceImage.height)
        val cropX = (sourceImage.width - size) / 2
        val cropY = (sourceImage.height - size) / 2
        val isJpeg = isJpegExtension(extension)
        val outputImage = BufferedImage(
            PROFILE_PICTURE_SIZE_PX,
            PROFILE_PICTURE_SIZE_PX,
            if (isJpeg) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB
        )

        val graphics = outputImage.createGraphics()
        try {
            if (isJpeg) {
                graphics.color = Color.WHITE
                graphics.fillRect(0, 0, PROFILE_PICTURE_SIZE_PX, PROFILE_PICTURE_SIZE_PX)
            }
            graphics.drawImage(
                sourceImage,
                0,
                0,
                PROFILE_PICTURE_SIZE_PX,
                PROFILE_PICTURE_SIZE_PX,
                cropX,
                cropY,
                cropX + size,
                cropY + size,
                null
            )
        } finally {
            graphics.dispose()
        }

        val output = ByteArrayOutputStream()
        if (!ImageIO.write(outputImage, imageIoFormatName(extension), output)) {
            throw NotAcceptableException("Unable to process image file for profile picture upload")
        }

        return output.toByteArray()
    }

    private fun requireSupportedExtension(originalFileName: String): String {
        val extension = originalFileName.substringAfterLast('.', "").lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw NotAcceptableException("Only PNG and JPEG profile pictures are supported")
        }
        return extension
    }

    private fun contentTypeFor(extension: String): String {
        return when (extension) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> error("Unsupported file extension for profile picture: $extension")
        }
    }

    private fun imageIoFormatName(extension: String): String {
        return if (extension == "jpg") "jpeg" else extension
    }

    private fun isJpegExtension(extension: String): Boolean {
        return extension == "jpg" || extension == "jpeg"
    }

    companion object {
        const val PROFILE_PICTURE_FOLDER = "profile-pictures"
        const val PROFILE_PICTURE_SIZE_PX = 100
        const val PROFILE_PICTURE_MAX_BYTES = 500 * 1024
        private const val CDN_BASE = "https://cdn.elephantchess.io"
        private val VALID_PROFILE_SEGMENT_REGEX = Regex("^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$")
        private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg")

        private fun requireValidProfileSegment(profile: String): String {
            require(profile.matches(VALID_PROFILE_SEGMENT_REGEX)) {
                "Unsupported profile segment for profile pictures: $profile"
            }
            return profile
        }

        fun profilePictureKey(profile: String, userId: String, extension: String): String {
            val sanitizedProfile = requireValidProfileSegment(profile)
            return "$sanitizedProfile/$PROFILE_PICTURE_FOLDER/$userId.$extension"
        }

        fun profilePictureUrl(profile: String, userId: String, extension: String?): String? {
            val sanitizedExtension = extension?.lowercase()?.takeIf { it in SUPPORTED_EXTENSIONS } ?: return null
            val sanitizedProfile = requireValidProfileSegment(profile)
            return "$CDN_BASE/$sanitizedProfile/$PROFILE_PICTURE_FOLDER/$userId.$sanitizedExtension"
        }
    }
}
