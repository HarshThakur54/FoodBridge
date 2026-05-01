package com.example.foodbridge.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

private const val FIREBASE_IMAGE_UPLOAD_TAG = "FirebaseImageUpload"
private const val INLINE_IMAGE_MAX_DIMENSION_PX = 1280
private const val INLINE_IMAGE_TARGET_BYTES = 350_000
private const val INLINE_IMAGE_MIN_QUALITY = 45

private fun detectImageExtension(context: Context, sourceUri: Uri): String {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(sourceUri)
    val extensionFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    val extensionFromPath = MimeTypeMap.getFileExtensionFromUrl(sourceUri.toString())

    return (
        extensionFromMime?.takeIf { it.isNotBlank() }
            ?: extensionFromPath.takeIf { it.isNotBlank() }
            ?: "jpg"
        ).lowercase(Locale.US)
}

private fun buildImageMetadata(context: Context, sourceUri: Uri): StorageMetadata {
    val mimeType = context.contentResolver.getType(sourceUri)
        ?: when (detectImageExtension(context, sourceUri)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            else -> "image/*"
        }

    return StorageMetadata.Builder().apply {
        if (!mimeType.isNullOrBlank()) {
            setContentType(mimeType)
        }
    }.build()
}

private fun candidateStorageBuckets(): List<String?> {
    val configuredBucket = FirebaseApp.getInstance().options.storageBucket
        ?.trim()
        ?.removePrefix("gs://")
        ?.ifBlank { null }
    val projectId = FirebaseApp.getInstance().options.projectId
        ?.trim()
        ?.ifBlank { null }

    val derivedBuckets = buildList {
        if (configuredBucket != null) {
            add(configuredBucket)
            when {
                configuredBucket.endsWith(".firebasestorage.app") -> {
                    add(configuredBucket.removeSuffix(".firebasestorage.app") + ".appspot.com")
                }

                configuredBucket.endsWith(".appspot.com") -> {
                    add(configuredBucket.removeSuffix(".appspot.com") + ".firebasestorage.app")
                }
            }
        }

        if (projectId != null) {
            add("$projectId.firebasestorage.app")
            add("$projectId.appspot.com")
        }
    }

    return (listOf<String?>(null) + derivedBuckets)
        .distinct()
}

private fun cacheUploadSource(
    context: Context,
    sourceUri: Uri,
    extension: String
): File {
    val stagedFile = File.createTempFile("firebase_upload_", ".$extension", context.cacheDir)
    context.contentResolver.openInputStream(sourceUri)?.use { input ->
        stagedFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw FileNotFoundException("Could not read selected image")
    return stagedFile
}

private fun decodeScaledBitmap(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    var sampleSize = 1
    while (
        bounds.outWidth / sampleSize > INLINE_IMAGE_MAX_DIMENSION_PX * 2 ||
        bounds.outHeight / sampleSize > INLINE_IMAGE_MAX_DIMENSION_PX * 2
    ) {
        sampleSize *= 2
    }

    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
    ) ?: throw IllegalStateException("Could not decode selected image")

    val maxDimension = maxOf(decoded.width, decoded.height)
    if (maxDimension <= INLINE_IMAGE_MAX_DIMENSION_PX) {
        return decoded
    }

    val scale = INLINE_IMAGE_MAX_DIMENSION_PX.toFloat() / maxDimension.toFloat()
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        (decoded.width * scale).toInt().coerceAtLeast(1),
        (decoded.height * scale).toInt().coerceAtLeast(1),
        true
    )
    if (scaled != decoded) {
        decoded.recycle()
    }
    return scaled
}

private fun inlineImageDataUrl(stagedFile: File): String {
    val bitmap = decodeScaledBitmap(stagedFile)
    try {
        var quality = 82
        var imageBytes: ByteArray

        do {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            imageBytes = output.toByteArray()
            quality -= 7
        } while (imageBytes.size > INLINE_IMAGE_TARGET_BYTES && quality >= INLINE_IMAGE_MIN_QUALITY)

        return "data:image/jpeg;base64," +
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    } finally {
        bitmap.recycle()
    }
}

suspend fun uploadImageToFirebaseStorage(
    context: Context,
    sourceUri: Uri,
    remoteDirectory: String,
    fileNamePrefix: String
): String = withContext(Dispatchers.IO) {
    val extension = detectImageExtension(context, sourceUri)
    val metadata = buildImageMetadata(context, sourceUri)
    val remotePath = "$remoteDirectory/${fileNamePrefix}_${System.currentTimeMillis()}.$extension"
    val stagedFile = cacheUploadSource(
        context = context.applicationContext,
        sourceUri = sourceUri,
        extension = extension
    )
    val stagedUri = Uri.fromFile(stagedFile)

    var lastError: Exception? = null
    try {
        for (bucket in candidateStorageBuckets()) {
            val storage = if (bucket == null) {
                FirebaseStorage.getInstance()
            } else {
                FirebaseStorage.getInstance("gs://$bucket")
            }
            val storageRef = storage.reference.child(remotePath)

            try {
                storageRef.putFile(stagedUri, metadata).await()
                return@withContext storageRef.downloadUrl.await().toString().ifBlank {
                    throw IllegalStateException("Image uploaded but no download URL was returned")
                }
            } catch (e: Exception) {
                Log.w(
                    FIREBASE_IMAGE_UPLOAD_TAG,
                    "Upload attempt failed for bucket=${bucket ?: "default"} path=$remotePath",
                    e
                )
                lastError = e
            }
        }

        if (lastError is StorageException) {
            Log.w(
                FIREBASE_IMAGE_UPLOAD_TAG,
                "Firebase Storage unavailable, using inline image fallback",
                lastError
            )
            return@withContext inlineImageDataUrl(stagedFile)
        }
    } finally {
        stagedFile.delete()
    }

    throw (lastError ?: IllegalStateException("Firebase Storage upload failed"))
}
