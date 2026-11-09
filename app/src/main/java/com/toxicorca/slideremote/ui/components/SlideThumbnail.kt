package com.toxicorca.slideremote.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}

/**
 * Shows the slide picture at [file] if it exists, otherwise [placeholder].
 * [version] busts the cache when a deck's pictures are replaced.
 */
@Composable
fun SlideThumbnail(
    file: File?,
    version: Long,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
) {
    val key = file?.let { "${it.absolutePath}@$version" }
    val bitmap by produceState<Bitmap?>(initialValue = key?.let { ThumbCache.get(it) }, key) {
        val f = file
        if (key == null || f == null) {
            value = null
            return@produceState
        }
        value = ThumbCache.get(key) ?: withContext(Dispatchers.IO) {
            if (f.exists()) {
                runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
                    ?.also { ThumbCache.put(key, it) }
            } else {
                null
            }
        }
    }
    Box(modifier) {
        val bmp = bitmap
        if (bmp != null) {
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            placeholder()
        }
    }
}
