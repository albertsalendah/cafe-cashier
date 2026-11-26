package com.albertsalendah.cafecashier.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// Keeps product photos in the app's own storage. The database stores only the bare file name
// (Product.imagePath); fileFor() turns it back into a File.
//
// A picked photo is copied in as a downscaled JPEG, so a café with many products does not fill the
// phone with full-size camera pictures, and the picture keeps working if the original is deleted.
class ProductImageStore(private val context: Context) {

    val dir: File get() = File(context.filesDir, "product_images")

    fun fileFor(name: String): File = File(dir, name)

    // Removes a stored photo. Safe to call for a null or already-missing name.
    fun delete(name: String?) {
        if (name == null) return
        fileFor(name).delete()
    }

    // Copies the picked photo (a content Uri from the photo picker) into app storage.
    // Returns the new file name, or null if the photo could not be read or saved.
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // 1) Read only the size, to decide how far the photo can be shrunk while decoding.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // 2) Decode at the reduced size.
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            // 3) Camera photos are often stored sideways with a rotation flag; apply it.
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val upright = applyOrientation(decoded, orientation)

            // 4) Final size, then flatten onto white (JPEG has no transparency; a transparent PNG would turn black).
            val (width, height) = fitWithin(upright.width, upright.height)
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(result).apply {
                drawColor(Color.WHITE)
                drawBitmap(upright, null, android.graphics.Rect(0, 0, width, height), null)
            }

            // 5) Save under a fresh name, so a replaced photo never shows a stale cached copy.
            dir.mkdirs()
            val name = UUID.randomUUID().toString() + ".jpg"
            val file = fileFor(name)
            val saved = try {
                file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            } catch (e: Exception) {
                false
            }
            if (!saved) {
                file.delete()
                return@withContext null
            }
            name
        } catch (e: Exception) {
            // Unreadable file, out of memory, out of space...: the screen shows "couldn't use that photo".
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
