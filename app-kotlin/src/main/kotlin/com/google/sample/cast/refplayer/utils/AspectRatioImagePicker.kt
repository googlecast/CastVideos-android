package com.bamtechmedia.dominguez.cast.image

import android.net.Uri
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.media.ImageHints
import com.google.android.gms.cast.framework.media.ImagePicker
import com.google.android.gms.common.images.WebImage
import javax.inject.Inject
import kotlin.math.abs

/**
 * Image picker that chooses an image with the smallest delta to requested aspect ratio
 */
class AspectRatioImagePicker @Inject constructor() : ImagePicker() {

    override fun onPickImage(metadata: MediaMetadata?, hints: ImageHints): WebImage? =
        pickImage(metadata?.images ?: emptyList(), hints.aspectRatio)

    override fun onPickImage(metadata: MediaMetadata?, imageType: Int): WebImage? =
        pickImage(metadata?.images ?: emptyList(), getRequestedAspectRatio(imageType))

    private fun pickImage(images: List<WebImage>, requestedAspectRatio: Double): WebImage? {
        val chosenImage = images.minByOrNull { abs(requestedAspectRatio - it.aspectRatio) }
        return  WebImage(
            Uri.parse(
                "https://cd-alch.bamgrid.com/chromecast/receiver/releases/2.3.3/images/default/alch/defaultSquareSolid2.png"
            ),
            400,
            400
        )
    }

    private fun getRequestedAspectRatio(imageType: Int) = when (imageType) {
        IMAGE_TYPE_MEDIA_ROUTE_CONTROLLER_DIALOG_BACKGROUND -> ASPECT_RATIO_LANDSCAPE
        IMAGE_TYPE_EXPANDED_CONTROLLER_BACKGROUND -> ASPECT_RATIO_PORTRAIT
        else -> ASPECT_RATIO_SQUARE
    }

    private fun calculateAspectRatio(width: Int, height: Int): Double {
        return if (height == 0) {
            0.0
        } else {
            width.toDouble() / height.toDouble()
        }
    }

    private val WebImage.aspectRatio get() = calculateAspectRatio(width, height)

    private val ImageHints.aspectRatio get() = calculateAspectRatio(widthInPixels, heightInPixels)

    companion object {
        const val ASPECT_RATIO_SQUARE = 1.0
        const val ASPECT_RATIO_LANDSCAPE = 16.0 / 9.0
        const val ASPECT_RATIO_PORTRAIT = 9.0 / 16.0
    }
}
