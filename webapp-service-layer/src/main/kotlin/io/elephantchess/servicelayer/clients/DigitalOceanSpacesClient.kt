package io.elephantchess.servicelayer.clients

import io.elephantchess.config.AppConfig
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Suppress("SameParameterValue")
class DigitalOceanSpacesClient(
    appConfig: AppConfig
) {

    private val accessKeyId by lazy { appConfig.loadString("digitalocean.spaces.key.id") }
    private val secretAccessKey by lazy { appConfig.loadString("digitalocean.spaces.key.secret") }
    private val bucketName by lazy { appConfig.loadString("digitalocean.spaces.bucket") }
    private val region = "ams3"
    private val endpoint by lazy { "$bucketName.$region.digitaloceanspaces.com" }

    private val client = HttpClient(CIO) {
        install(Logging) {
            level = LogLevel.INFO
        }
        expectSuccess = false
    }

    /**
     * Upload a file to DigitalOcean Spaces
     * @param file The file to upload
     * @param key The object key (path) in the bucket, e.g., "images/photo.jpg"
     * @param contentType The content type of the file, e.g., "image/jpeg"
     * @param acl Access control list, e.g., "public-read" or "private"
     * @return true if upload was successful, false otherwise
     */
    suspend fun uploadFile(
        file: File,
        key: String,
        contentType: String,
        acl: String
    ): Boolean {
        val fileBytes = file.readBytes()
        return uploadBytes(fileBytes, key, contentType, acl)
    }

    /**
     * Upload bytes to DigitalOcean Spaces
     * @param bytes The byte array to upload
     * @param key The object key (path) in the bucket, e.g., "images/photo.jpg"
     * @param contentType The content type of the file, e.g., "image/jpeg"
     * @param acl Access control list, e.g., "public-read" or "private"
     * @return true if upload was successful, false otherwise
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        key: String,
        contentType: String,
        acl: String
    ): Boolean {
        val url = "https://$endpoint/$key"

        val now = Date()
        val amzDate = getAmzDate(now)
        val dateStamp = getDateStamp(now)

        val contentHash = sha256Hex(bytes)

        val response = client.put(url) {
            header("Host", endpoint)
            header("Content-Type", contentType)
            header("x-amz-date", amzDate)
            header("x-amz-content-sha256", contentHash)
            header("x-amz-acl", acl)

            // Generate authorization header
            val authHeader = generateAuthorizationHeader(
                method = "PUT",
                key = key,
                contentType = contentType,
                contentHash = contentHash,
                amzDate = amzDate,
                dateStamp = dateStamp,
                acl = acl
            )
            header("Authorization", authHeader)

            setBody(bytes)
        }

        return response.status.isSuccess()
    }

    /**
     * Get the most recent date-based folder in the bucket
     * @return The folder name (e.g., "2025-10-26") or null if no folders found
     */
    suspend fun getMostRecentVersionFolder(): String? {
        val url = "https://$endpoint/"

        val now = Date()
        val amzDate = getAmzDate(now)
        val dateStamp = getDateStamp(now)
        val contentHash = sha256Hex(ByteArray(0))

        val queryParams = mapOf(
            "delimiter" to "/",
            "prefix" to ""
        )

        val response = client.get(url) {
            header("Host", endpoint)
            header("x-amz-date", amzDate)
            header("x-amz-content-sha256", contentHash)

            val authHeader = generateAuthorizationHeader(
                method = "GET",
                key = "",
                contentType = "",
                contentHash = contentHash,
                amzDate = amzDate,
                dateStamp = dateStamp,
                acl = null,
                queryParameters = queryParams
            )
            header("Authorization", authHeader)

            parameter("delimiter", "/")
            parameter("prefix", "")
        }

        if (!response.status.isSuccess()) {
            return null
        }

        val responseText = response.bodyAsText()

        // Parse folder names from XML response
        // Supports both date-only folders (yyyy-MM-dd) and date-time folders (yyyy-MM-dd-HH-mm).
        val folderPattern =
            """<CommonPrefixes><Prefix>(\d{4}-\d{2}-\d{2}(?:-\d{2}-\d{2})?)/</Prefix></CommonPrefixes>""".toRegex()
        val folders = folderPattern.findAll(responseText)
            .map { it.groupValues[1] }
            .toList()


        // Return the most recent (lexicographically last since dates are ISO format)
        return folders.maxOrNull()
    }

    private fun generateAuthorizationHeader(
        method: String,
        key: String,
        contentType: String,
        contentHash: String,
        amzDate: String,
        dateStamp: String,
        acl: String?,
        queryParameters: Map<String, String> = emptyMap()
    ): String {
        val service = "s3"
        val algorithm = "AWS4-HMAC-SHA256"
        val credentialScope = "$dateStamp/$region/$service/aws4_request"

        // Create canonical request
        val canonicalUri = if (key.isEmpty()) "/" else "/$key"

        // Create canonical query string (must be sorted by parameter name)
        val canonicalQueryString = queryParameters.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) ->
                if (v.isEmpty()) {
                    "${k.encodeURLParameter()}="
                } else {
                    "${k.encodeURLParameter()}=${v.encodeURLParameter()}"
                }
            }

        val canonicalHeaders = buildString {
            if (contentType.isNotEmpty()) {
                append("content-type:$contentType\n")
            }
            append("host:$endpoint\n")
            if (acl != null) {
                append("x-amz-acl:$acl\n")
            }
            append("x-amz-content-sha256:$contentHash\n")
            append("x-amz-date:$amzDate\n")
        }

        val signedHeaders = buildString {
            if (contentType.isNotEmpty()) {
                append("content-type;")
            }
            append("host;")
            if (acl != null) {
                append("x-amz-acl;")
            }
            append("x-amz-content-sha256;x-amz-date")
        }

        val canonicalRequest =
            "$method\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$contentHash"

        // Create string to sign
        val canonicalRequestHash = sha256Hex(canonicalRequest.toByteArray())
        val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n$canonicalRequestHash"

        // Calculate signature
        val signingKey = getSignatureKey(secretAccessKey, dateStamp, region, service)
        val signature = hmacSha256Hex(stringToSign.toByteArray(), signingKey)

        // Create authorization header
        return "$algorithm Credential=$accessKeyId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kDate = hmacSha256(dateStamp.toByteArray(), "AWS4$key".toByteArray())
        val kRegion = hmacSha256(regionName.toByteArray(), kDate)
        val kService = hmacSha256(serviceName.toByteArray(), kRegion)
        return hmacSha256("aws4_request".toByteArray(), kService)
    }

    private fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hmacSha256Hex(data: ByteArray, key: ByteArray): String {
        return hmacSha256(data, key).toHex()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun getAmzDate(date: Date): String {
        val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(date)
    }

    private fun getDateStamp(date: Date): String {
        val df = SimpleDateFormat("yyyyMMdd")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(date)
    }

    fun close() {
        client.close()
    }

}
