package com.boardgamegeek.util

import android.graphics.drawable.BitmapDrawable
import android.support.annotation.DrawableRes
import android.support.v7.graphics.Palette
import android.widget.ImageView
import com.boardgamegeek.R
import com.boardgamegeek.io.Adapter
import com.boardgamegeek.io.model.Image
import com.squareup.picasso.Picasso
import retrofit2.Call
import retrofit2.Response
import timber.log.Timber
import java.util.*

/**
 * Loading images? This is your huckleberry.
 */
object ImageUtils {
    /**
     * Call back from loading an image.
     */
    interface Callback {
        fun onSuccessfulImageLoad(palette: Palette?)

        fun onFailedImageLoad()
    }

    private const val IMAGE_URL_PREFIX = "https://cf.geekdo-images.com/images/pic"

    /**
     * Loads an image into the [android.widget.ImageView] by attempting various sizes and image formats. Applies
     * fit/center crop and will load a [android.support.v7.graphics.Palette].
     */
    @JvmStatic
    @JvmOverloads
    fun ImageView.safelyLoadImage(imageId: Int, callback: Callback? = null) {
        safelyLoadImage(addDefaultImagesToQueue(imageId), callback)
    }

    /**
     * Loads an image into the [android.widget.ImageView] by attempting various sizes. Applies fit/center crop and
     * will load a [android.support.v7.graphics.Palette].
     */
    @JvmStatic
    @JvmOverloads
    fun ImageView.safelyLoadImage(imageUrl: String, thumbnailUrl: String, heroImageUrl: String? = "", callback: Callback? = null) {
        RemoteConfig.fetch()
        if (heroImageUrl?.isNotEmpty() == true) {
            val queue = LinkedList<String>()
            queue.add(heroImageUrl)
            loadImages(thumbnailUrl, imageUrl, callback, queue)
        } else if (RemoteConfig.getBoolean(RemoteConfig.KEY_FETCH_IMAGE_WITH_API)) {
            val imageId = getImageId(imageUrl)
            if (imageId > 0) {
                val call = Adapter.createGeekdoApi().image(imageId)
                call.enqueue(object : retrofit2.Callback<Image> {
                    override fun onResponse(call: Call<Image>, response: Response<Image>) {
                        val body = response.body()
                        if (response.code() == 200 && body != null) {
                            val queue = LinkedList<String>()
                            queue.add(body.images.medium.url)
                            queue.add(body.images.small.url)
                            loadImages(thumbnailUrl, imageUrl, callback, queue)
                        } else {
                            loadImages(thumbnailUrl, imageUrl, callback)
                        }
                    }

                    override fun onFailure(call: Call<Image>, t: Throwable) {
                        loadImages(thumbnailUrl, imageUrl, callback)
                    }
                })
            } else {
                loadImages(thumbnailUrl, imageUrl, callback)
            }
        } else {
            loadImages(thumbnailUrl, imageUrl, callback)
        }
    }

    private fun ImageView.loadImages(thumbnailUrl: String, imageUrl: String, callback: Callback?, q: Queue<String>? = null) {
        var queue = q
        if (queue == null) queue = LinkedList()
        queue.add(thumbnailUrl)
        queue.add(imageUrl)
        safelyLoadImage(queue, callback)
    }

    fun getImageId(imageUrl: String): Int {
        if (imageUrl.isBlank()) return 0
        var partialUrl = imageUrl
        val imageIdPrefix = "/pic"
        val lastSlashIndex = partialUrl.lastIndexOf(imageIdPrefix)
        if (lastSlashIndex > -1)
            partialUrl = partialUrl.substring(lastSlashIndex + imageIdPrefix.length)
        val lastDotIndex = partialUrl.lastIndexOf('.')
        if (lastDotIndex != -1)
            partialUrl = partialUrl.substring(0, lastDotIndex)
        return try {
            partialUrl.toInt()
        } catch (e: NumberFormatException) {
            Timber.w("Didn't find an image ID in the URL [$imageUrl]")
            0
        }
    }

    @JvmStatic
    fun ImageView.loadThumbnail(imageId: Int) {
        RemoteConfig.fetch()
        if (RemoteConfig.getBoolean(RemoteConfig.KEY_FETCH_IMAGE_WITH_API)) {
            val call = Adapter.createGeekdoApi().image(imageId)
            call.enqueue(object : retrofit2.Callback<Image> {
                override fun onResponse(call: Call<Image>, response: Response<Image>) {
                    val body = response.body()
                    if (response.code() == 200 && body != null) {
                        val queue = LinkedList<String>()
                        queue.add(body.images.small.url)
                        addDefaultImagesToQueue(imageId, queue)
                        safelyLoadThumbnail(queue)
                    } else {
                        safelyLoadThumbnail(addDefaultImagesToQueue(imageId))
                    }
                }

                override fun onFailure(call: Call<Image>, t: Throwable) {
                    safelyLoadThumbnail(addDefaultImagesToQueue(imageId))
                }
            })
        } else {
            safelyLoadThumbnail(addDefaultImagesToQueue(imageId))
        }
    }

    private fun addDefaultImagesToQueue(imageId: Int, q: Queue<String>? = null): Queue<String> {
        var queue = q
        if (queue == null) queue = LinkedList()
        queue.add("$IMAGE_URL_PREFIX$imageId.jpg")
        queue.add("$IMAGE_URL_PREFIX$imageId.png")
        return queue
    }

    @JvmStatic
    fun ImageView.loadThumbnail(vararg paths: String) {
        val queue = LinkedList<String>()
        queue += paths
        safelyLoadThumbnail(queue)
    }

    @JvmStatic
    fun ImageView.loadThumbnail(path: String, @DrawableRes errorResId: Int = R.drawable.thumbnail_image_empty) {
        val queue = LinkedList<String>()
        queue.add(path)
        safelyLoadThumbnail(queue, errorResId)
    }

    private fun ImageView.safelyLoadThumbnail(imageUrls: Queue<String>, @DrawableRes errorResId: Int = R.drawable.thumbnail_image_empty) {
        var url: String? = null
        val savedUrl = getTag(R.id.url) as String?
        if (savedUrl?.isNotEmpty() == true) {
            if (imageUrls.contains(savedUrl)) {
                url = savedUrl
            } else {
                setTag(R.id.url, null)
            }
        }
        if (url.isNullOrEmpty()) {
            url = imageUrls.poll()
        }
        if (url.isNullOrEmpty()) {
            if (imageUrls.isEmpty()) {
                url = null
            } else {
                return
            }
        }
        val imageUrl = url
        Picasso.with(context)
                .load(HttpUtils.ensureScheme(imageUrl))
                .placeholder(errorResId)
                .error(errorResId)
                .fit()
                .centerCrop()
                .into(this, object : com.squareup.picasso.Callback {
                    override fun onSuccess() {
                        setTag(R.id.url, imageUrl)
                    }

                    override fun onError() {
                        safelyLoadThumbnail(imageUrls, errorResId)
                    }
                })
    }

    /**
     * Loads an image into the [android.widget.ImageView] by attempting each URL in the [java.util.Queue]
     * until one is successful. Applies fit/center crop and will load a [android.support.v7.graphics.Palette].
     */
    private fun ImageView.safelyLoadImage(imageUrls: Queue<String>, callback: Callback?) {
        var url: String? = null
        val savedUrl = getTag(R.id.url) as String?
        if (savedUrl?.isNotEmpty() == true) {
            if (imageUrls.contains(savedUrl)) {
                url = savedUrl
            } else {
                setTag(R.id.url, null)
            }
        }
        if (url.isNullOrEmpty()) {
            url = imageUrls.poll()
        }
        if (url.isNullOrEmpty()) {
            callback?.onFailedImageLoad()
            return
        }
        val imageUrl = url
        Picasso.with(context)
                .load(HttpUtils.ensureScheme(imageUrl))
                .transform(PaletteTransformation.instance())
                .into(this, object : com.squareup.picasso.Callback {
                    override fun onSuccess() {
                        setTag(R.id.url, imageUrl)
                        if (callback != null) {
                            val bitmap = (drawable as BitmapDrawable).bitmap
                            val palette = PaletteTransformation.getPalette(bitmap)
                            callback.onSuccessfulImageLoad(palette)
                        }
                    }

                    override fun onError() {
                        safelyLoadImage(imageUrls, callback)
                    }
                })
    }
}
