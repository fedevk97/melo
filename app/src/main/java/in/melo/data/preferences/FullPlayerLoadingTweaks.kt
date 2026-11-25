package `in`.melo.data.preferences

/**
 * Internal tuning for how the full player fills in as it opens. Nothing is held back by default:
 * the real artwork and controls ride the panel up from the start,
 * rather than placeholders that swap in near the top.
 */
data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = false,
    val delayAlbumCarousel: Boolean = false,
    val delaySongMetadata: Boolean = false,
    val delayProgressBar: Boolean = false,
    val delayControls: Boolean = false,
    val showPlaceholders: Boolean = true,
    val transparentPlaceholders: Boolean = false,
    val applyPlaceholdersOnClose: Boolean = false,
    val switchOnDragRelease: Boolean = true,
    val contentAppearThresholdPercent: Int = 98,
    val contentCloseThresholdPercent: Int = 0
)
