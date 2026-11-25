package `in`.melo.presentation.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import `in`.melo.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFAirplayAudio
import com.composables.sfsymbols.monochrome.SFAntennaRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFAntennaRadiowavesLeftAndRightSlash
import com.composables.sfsymbols.monochrome.SFArchivebox
import com.composables.sfsymbols.monochrome.SFArrowClockwise
import com.composables.sfsymbols.monochrome.SFArrowCounterclockwise
import com.composables.sfsymbols.monochrome.SFArrowDown
import com.composables.sfsymbols.monochrome.SFArrowDownCircle
import com.composables.sfsymbols.monochrome.SFArrowDownCircleFill
import com.composables.sfsymbols.monochrome.SFArrowBackward
import com.composables.sfsymbols.monochrome.SFArrowForward
import com.composables.sfsymbols.monochrome.SFArrowLeftArrowRight
import com.composables.sfsymbols.monochrome.SFArrowUpArrowDown
import com.composables.sfsymbols.monochrome.SFArrowUpDocument
import com.composables.sfsymbols.monochrome.SFArrowUturnBackward
import com.composables.sfsymbols.monochrome.SFBackwardEndFill
import com.composables.sfsymbols.monochrome.SFBattery100percent
import com.composables.sfsymbols.monochrome.SFBubbleLeftAndBubbleRight
import com.composables.sfsymbols.monochrome.SFCableConnectorSlash
import com.composables.sfsymbols.monochrome.SFCpu
import com.composables.sfsymbols.monochrome.SFCellularbars
import com.composables.sfsymbols.monochrome.SFCharacterBubble
import com.composables.sfsymbols.monochrome.SFChartLineUptrendXyaxis
import com.composables.sfsymbols.monochrome.SFChecklist
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.composables.sfsymbols.monochrome.SFCheckmarkCircle
import com.composables.sfsymbols.monochrome.SFCheckmarkCircleFill
import com.composables.sfsymbols.monochrome.SFChevronBackward
import com.composables.sfsymbols.monochrome.SFChevronDown
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.composables.sfsymbols.monochrome.SFChevronUp
import com.composables.sfsymbols.monochrome.SFChevronUpChevronDown
import com.composables.sfsymbols.monochrome.SFClock
import com.composables.sfsymbols.monochrome.SFClockArrowTriangleheadCounterclockwiseRotate90
import com.composables.sfsymbols.monochrome.SFDotRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFDrop
import com.composables.sfsymbols.monochrome.SFEllipsis
import com.composables.sfsymbols.monochrome.SFExclamationmarkCircle
import com.composables.sfsymbols.monochrome.SFEye
import com.composables.sfsymbols.monochrome.SFEyeSlash
import com.composables.sfsymbols.monochrome.SFFolder
import com.composables.sfsymbols.monochrome.SFForwardEndFill
import com.composables.sfsymbols.monochrome.SFGearshape
import com.composables.sfsymbols.monochrome.SFGlobe
import com.composables.sfsymbols.monochrome.SFHandPointLeft
import com.composables.sfsymbols.monochrome.SFHandTap
import com.composables.sfsymbols.monochrome.SFHeadphones
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFHifispeaker
import com.composables.sfsymbols.monochrome.SFHouse
import com.composables.sfsymbols.monochrome.SFHouseFill
import com.composables.sfsymbols.monochrome.SFIcloudAndArrowUp
import com.composables.sfsymbols.monochrome.SFIcloudSlash
import com.composables.sfsymbols.monochrome.SFInternaldrive
import com.composables.sfsymbols.monochrome.SFIphone
import com.composables.sfsymbols.monochrome.SFIphoneRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFLadybug
import com.composables.sfsymbols.monochrome.SFLine3Horizontal
import com.composables.sfsymbols.monochrome.SFLink
import com.composables.sfsymbols.monochrome.SFListBullet
import com.composables.sfsymbols.monochrome.SFListBulletRectangle
import com.composables.sfsymbols.monochrome.SFLock
import com.composables.sfsymbols.monochrome.SFMacbookAndIphone
import com.composables.sfsymbols.monochrome.SFMacwindow
import com.composables.sfsymbols.monochrome.SFPaintpalette
import com.composables.sfsymbols.monochrome.SFPhotoArtframe
import com.composables.sfsymbols.monochrome.SFSquareBottomhalfFilled
import com.composables.sfsymbols.monochrome.SFPhotoOnRectangle
import com.composables.sfsymbols.monochrome.SFTag
import com.composables.sfsymbols.monochrome.SFMagnifyingglass
import com.composables.sfsymbols.monochrome.SFMicrophone
import com.composables.sfsymbols.monochrome.SFMusicMicrophone
import com.composables.sfsymbols.monochrome.SFMusicNote
import com.composables.sfsymbols.monochrome.SFMusicNoteHouse
import com.composables.sfsymbols.monochrome.SFNosign
import com.composables.sfsymbols.monochrome.SFPaintbrushPointed
import com.composables.sfsymbols.monochrome.SFPaperclip
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFPencil
import com.composables.sfsymbols.monochrome.SFPerson
import com.composables.sfsymbols.monochrome.SFPersonCropCircle
import com.composables.sfsymbols.monochrome.SFPhoto
import com.composables.sfsymbols.monochrome.SFPin
import com.composables.sfsymbols.monochrome.SFPinFill
import com.composables.sfsymbols.monochrome.SFPlayCircle
import com.composables.sfsymbols.monochrome.SFPlayFill
import com.composables.sfsymbols.monochrome.SFPlus
import com.composables.sfsymbols.monochrome.SFPower
import com.composables.sfsymbols.monochrome.SFQuoteBubble
import com.composables.sfsymbols.monochrome.SFRectangle3Group
import com.composables.sfsymbols.monochrome.SFRectangleSplit3x1
import com.composables.sfsymbols.monochrome.SFRectangleStack
import com.composables.sfsymbols.monochrome.SFRepeat
import com.composables.sfsymbols.monochrome.SFRepeat1
import com.composables.sfsymbols.monochrome.SFScope
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSliderHorizontal3
import com.composables.sfsymbols.monochrome.SFSparkles
import com.composables.sfsymbols.monochrome.SFSpeakerWave1
import com.composables.sfsymbols.monochrome.SFSpeakerWave2
import com.composables.sfsymbols.monochrome.SFSquareAndArrowDown
import com.composables.sfsymbols.monochrome.SFSquareAndArrowUp
import com.composables.sfsymbols.monochrome.SFSquareGrid2x2
import com.composables.sfsymbols.monochrome.SFSquareStack
import com.composables.sfsymbols.monochrome.SFSquareStackFill
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFStarFill
import com.composables.sfsymbols.monochrome.SFSunMax
import com.composables.sfsymbols.monochrome.SFTablecells
import com.composables.sfsymbols.monochrome.SFTextAligncenter
import com.composables.sfsymbols.monochrome.SFTextAlignleft
import com.composables.sfsymbols.monochrome.SFTextAlignright
import com.composables.sfsymbols.monochrome.SFTextBadgePlus
import com.composables.sfsymbols.monochrome.SFTextLineFirstAndArrowtriangleForward
import com.composables.sfsymbols.monochrome.SFTextMagnifyingglass
import com.composables.sfsymbols.monochrome.SFTextformat
import com.composables.sfsymbols.monochrome.SFTimer
import com.composables.sfsymbols.monochrome.SFTrash
import com.composables.sfsymbols.monochrome.SFTv
import com.composables.sfsymbols.monochrome.SFWaveform
import com.composables.sfsymbols.monochrome.SFWifi
import com.composables.sfsymbols.monochrome.SFWifiSlash
import com.composables.sfsymbols.monochrome.SFXmark
import com.composables.sfsymbols.monochrome.SFXmarkCircle

/**
 * The app's icons: Apple's SF Symbols, from the Jetpack_SF_Symbols port, at Bold weight. Each keeps the Material name its call sites used, so the
 * choice of symbol lives here.
 */
object MeloIcons {
    val Abc: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextformat) }
    val AccountCircle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPersonCropCircle) }
    val Add: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlus) }
    val Album: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStack) }
    val ArrowBack: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronBackward) }
    val ArrowDownward: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowDown) }
    val ArrowBackward: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowBackward) }
    val ArrowForward: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowForward) }
    val Artist: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicMicrophone) }
    val Attach: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPaperclip) }
    val Backup: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArchivebox) }
    val BatteryFull: ImageVector by lazy { bold(SfSymbols.Monochrome.SFBattery100percent) }
    val Block: ImageVector by lazy { bold(SfSymbols.Monochrome.SFNosign) }
    val Bluetooth: ImageVector by lazy { bold(SfSymbols.Monochrome.SFAntennaRadiowavesLeftAndRight) }
    val Phone: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIphone) }
    val BluetoothDisabled: ImageVector by lazy { bold(SfSymbols.Monochrome.SFAntennaRadiowavesLeftAndRightSlash) }
    val BlurOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDrop) }
    val BrightnessHigh: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSunMax) }
    val BrokenImage: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPhoto) }
    val BugReport: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLadybug) }
    val Cancel: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmarkCircle) }
    val Carousel: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRectangleStack) }
    val Cast: ImageVector by lazy { bold(SfSymbols.Monochrome.SFAirplayAudio) }
    val Check: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmark) }
    val CheckCircle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmarkCircleFill) }
    val Clear: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val ClearAll: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val Close: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmark) }
    val CloudOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIcloudSlash) }
    val CloudUpload: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIcloudAndArrowUp) }
    val Columns: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRectangleSplit3x1) }
    val Delete: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTrash) }
    val DeleteSweep: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTrash) }
    val Devices: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMacbookAndIphone) }
    val Cpu: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCpu) }
    val Disconnect: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCableConnectorSlash) }
    val DoneAll: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChecklist) }
    val Download: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowDownCircle) }
    val DownloadDone: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCheckmarkCircle) }
    val DownloadForOffline: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowDownCircle) }
    val Downloaded: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowDownCircleFill) }
    val DragHandle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLine3Horizontal) }
    val DragIndicator: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLine3Horizontal) }
    val Edit: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPencil) }
    val ErrorOutline: ImageVector by lazy { bold(SfSymbols.Monochrome.SFExclamationmarkCircle) }
    val ExpandMore: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronDown) }
    val Explore: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareGrid2x2) }
    val Favorite: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeartFill) }
    val FavoriteBorder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeart) }
    val Folder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFFolder) }
    val FormatAlignCenter: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextAligncenter) }
    val FormatAlignLeft: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextAlignleft) }
    val FormatAlignRight: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextAlignright) }
    val Forum: ImageVector by lazy { bold(SfSymbols.Monochrome.SFBubbleLeftAndBubbleRight) }
    val GraphicEq: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWaveform) }
    val Headphones: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHeadphones) }
    val History: ImageVector by lazy { bold(SfSymbols.Monochrome.SFClockArrowTriangleheadCounterclockwiseRotate90) }
    val Home: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHouse) }
    val HomeFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHouseFill) }
    val Image: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPhoto) }
    val KeyboardArrowDown: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronDown) }
    val KeyboardArrowRight: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronRight) }
    val KeyboardArrowUp: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronUp) }
    val Language: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGlobe) }
    val Library: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStack) }
    val LibraryFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareStackFill) }
    val LibraryMusic: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNoteHouse) }
    val LightMode: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSunMax) }
    val Link: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLink) }
    val Lock: ImageVector by lazy { bold(SfSymbols.Monochrome.SFLock) }
    val Lyrics: ImageVector by lazy { bold(SfSymbols.Monochrome.SFQuoteBubble) }
    val ManageSearch: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextMagnifyingglass) }
    val Mic: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMicrophone) }
    val MoreHoriz: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEllipsis) }
    val MoreVert: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEllipsis) }
    val MusicNote: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMusicNote) }
    val MyLocation: ImageVector by lazy { bold(SfSymbols.Monochrome.SFScope) }
    val Palette: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPaintbrushPointed) }
    val Pause: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPauseFill) }
    val Person: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPerson) }
    val PlayArrow: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlayFill) }
    val PlayCircle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPlayCircle) }
    val PlaylistAdd: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextBadgePlus) }
    val PlaylistPlay: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTextLineFirstAndArrowtriangleForward) }
    val PowerSettingsNew: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPower) }
    val PushPin: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPin) }
    val PushPinFill: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPinFill) }
    val Quality: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSparkles) }
    val QueueMusic: ImageVector by lazy { bold(SfSymbols.Monochrome.SFListBullet) }
    val Radio: ImageVector by lazy { bold(SfSymbols.Monochrome.SFDotRadiowavesLeftAndRight) }
    val Refresh: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowClockwise) }
    val RemoveDone: ImageVector by lazy { bold(SfSymbols.Monochrome.SFXmarkCircle) }
    val Repeat: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRepeat) }
    val RepeatOne: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRepeat1) }
    val Restore: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowCounterclockwise) }
    val Rows: ImageVector by lazy { bold(SfSymbols.Monochrome.SFListBulletRectangle) }
    val Save: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareAndArrowDown) }
    val Schedule: ImageVector by lazy { bold(SfSymbols.Monochrome.SFClock) }
    val Search: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMagnifyingglass) }
    val SearchOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMagnifyingglass) }
    val Settings: ImageVector by lazy { bold(SfSymbols.Monochrome.SFGearshape) }
    val Share: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareAndArrowUp) }
    val ShowChart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChartLineUptrendXyaxis) }
    val Shuffle: ImageVector by lazy { bold(SfSymbols.Monochrome.SFShuffle) }
    val SignalCellularAlt: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCellularbars) }
    val SkipNext: ImageVector by lazy { bold(SfSymbols.Monochrome.SFForwardEndFill) }
    val SkipPrevious: ImageVector by lazy { bold(SfSymbols.Monochrome.SFBackwardEndFill) }
    val Sort: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUpArrowDown) }
    val Speaker: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHifispeaker) }
    val Star: ImageVector by lazy { bold(SfSymbols.Monochrome.SFStarFill) }
    val StarBorder: ImageVector by lazy { bold(SfSymbols.Monochrome.SFStar) }
    val Storage: ImageVector by lazy { bold(SfSymbols.Monochrome.SFInternaldrive) }
    val Style: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPaintbrushPointed) }
    val ColorPalette: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPaintpalette) }
    val Artwork: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPhotoArtframe) }
    val Gradient: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSquareBottomhalfFilled) }
    val Backdrop: ImageVector by lazy { bold(SfSymbols.Monochrome.SFPhotoOnRectangle) }
    val Tag: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTag) }
    val SurroundSound: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHifispeaker) }
    val SwapHoriz: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowLeftArrowRight) }
    val SwapVert: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUpArrowDown) }
    val SwipeLeft: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHandPointLeft) }
    val Tab: ImageVector by lazy { bold(SfSymbols.Monochrome.SFMacwindow) }
    val TableChart: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTablecells) }
    val Timer: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTimer) }
    val TouchApp: ImageVector by lazy { bold(SfSymbols.Monochrome.SFHandTap) }
    val Translate: ImageVector by lazy { bold(SfSymbols.Monochrome.SFCharacterBubble) }
    val Tune: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSliderHorizontal3) }
    val Tv: ImageVector by lazy { bold(SfSymbols.Monochrome.SFTv) }
    val Undo: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUturnBackward) }
    val UnfoldMore: ImageVector by lazy { bold(SfSymbols.Monochrome.SFChevronUpChevronDown) }
    val UploadFile: ImageVector by lazy { bold(SfSymbols.Monochrome.SFArrowUpDocument) }
    val Vibration: ImageVector by lazy { bold(SfSymbols.Monochrome.SFIphoneRadiowavesLeftAndRight) }
    val ViewQuilt: ImageVector by lazy { bold(SfSymbols.Monochrome.SFRectangle3Group) }
    val Visibility: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEye) }
    val VisibilityOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFEyeSlash) }
    val VolumeDown: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSpeakerWave1) }
    val VolumeUp: ImageVector by lazy { bold(SfSymbols.Monochrome.SFSpeakerWave2) }
    val Wifi: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWifi) }
    val WifiOff: ImageVector by lazy { bold(SfSymbols.Monochrome.SFWifiSlash) }

    /** The icon drawables still named by resource id (tab bar, player toggles), drawn as their symbol. */
    private val drawables: Map<Int, () -> ImageVector> by lazy {
        mapOf(
            R.drawable.home_24_rounded_filled to { HomeFill },
            R.drawable.ic_explore_24 to { Explore },
            R.drawable.outline_high_quality_24 to { Quality },
            R.drawable.outline_restart_alt_24 to { Restore },
            R.drawable.outline_save_24 to { Save },
            R.drawable.round_favorite_24 to { Favorite },
            R.drawable.round_library_music_24 to { LibraryFill },
            R.drawable.rounded_album_24 to { Album },
            R.drawable.rounded_artist_24 to { Artist },
            R.drawable.rounded_attach_file_24 to { Attach },
            R.drawable.rounded_bluetooth_24 to { Bluetooth },
            R.drawable.rounded_broken_image_24 to { BrokenImage },
            R.drawable.rounded_cast_24 to { Cast },
            R.drawable.rounded_close_24 to { Close },
            R.drawable.rounded_favorite_24 to { FavoriteBorder },
            R.drawable.rounded_headphones_24 to { Headphones },
            R.drawable.rounded_home_24 to { Home },
            R.drawable.rounded_keyboard_arrow_down_24 to { KeyboardArrowDown },
            R.drawable.rounded_library_music_24 to { Library },
            R.drawable.rounded_lyrics_24 to { Lyrics },
            R.drawable.rounded_manage_search_24 to { ManageSearch },
            R.drawable.rounded_mimo_disconnect_24 to { Disconnect },
            R.drawable.rounded_music_note_24 to { MusicNote },
            R.drawable.rounded_queue_music_24 to { QueueMusic },
            R.drawable.rounded_repeat_24 to { Repeat },
            R.drawable.rounded_repeat_one_24 to { RepeatOne },
            R.drawable.rounded_search_24 to { Search },
            R.drawable.rounded_settings_24 to { Settings },
            R.drawable.rounded_shuffle_24 to { Shuffle },
            R.drawable.rounded_touch_app_24 to { TouchApp },
            R.drawable.rounded_upload_file_24 to { UploadFile },
            R.drawable.rounded_view_carousel_24 to { Carousel },
            R.drawable.rounded_view_column_24 to { Columns },
            R.drawable.rounded_view_week_24 to { Rows },
            R.drawable.rounded_volume_down_24 to { VolumeDown },
            R.drawable.rounded_volume_up_24 to { VolumeUp },
            R.drawable.rounded_wifi_24 to { Wifi },
            R.drawable.tab_24 to { Tab },
        )
    }

    fun forDrawable(@DrawableRes id: Int): ImageVector? = drawables[id]?.invoke()

    // ---- Bold weight ----

    /** Added to every line, in the symbols' own units: Regular's ~1.7 to Bold's ~2.6. */
    private const val BOLD_STROKE = 0.9f

    /**
     * The port cuts every symbol to its own bounds; drawn as it comes, each would stretch to fill
     * its box. Apple sets them all at one point size instead, and so does this: every symbol sits
     * centred on one square canvas of [CANVAS] units, at the units it was drawn in, and only one
     * too large for the canvas is scaled to fit.
     */
    private const val CANVAS = 31f

    private fun bold(regular: ImageVector): ImageVector {
        val pad = BOLD_STROKE / 2f
        val width = regular.viewportWidth + pad * 2f
        val height = regular.viewportHeight + pad * 2f
        val fit = minOf(1f, CANVAS / width, CANVAS / height)
        return ImageVector.Builder(
            name = regular.name + ".bold",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = CANVAS, viewportHeight = CANVAS,
            autoMirror = regular.autoMirror,
        ).apply {
            addGroup(
                translationX = (CANVAS - width * fit) / 2f + pad * fit,
                translationY = (CANVAS - height * fit) / 2f + pad * fit,
                scaleX = fit, scaleY = fit,
            )
            copyInto(this, regular.root)
            clearGroup()
        }.build()
    }

    /** Every node of [group], fully opaque and traced with [BOLD_STROKE] more line in its own colour. */
    private fun copyInto(builder: ImageVector.Builder, group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        name = node.name, rotate = node.rotation, pivotX = node.pivotX, pivotY = node.pivotY,
                        scaleX = node.scaleX, scaleY = node.scaleY,
                        translationX = node.translationX, translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    copyInto(builder, node)
                    builder.clearGroup()
                }
                is VectorPath -> builder.addPath(
                    pathData = node.pathData, pathFillType = node.pathFillType, name = node.name,
                    fill = node.fill, fillAlpha = 1f,
                    stroke = node.stroke ?: node.fill, strokeAlpha = 1f,
                    strokeLineWidth = node.strokeLineWidth + BOLD_STROKE,
                    strokeLineCap = node.strokeLineCap, strokeLineJoin = StrokeJoin.Round,
                    strokeLineMiter = node.strokeLineMiter,
                )
            }
        }
    }
}

/** [painterResource], except an icon drawable [MeloIcons] knows is drawn as its SF Symbol. */
@Composable
fun meloPainter(@DrawableRes id: Int): Painter {
    val symbol = remember(id) { MeloIcons.forDrawable(id) }
    return if (symbol != null) rememberVectorPainter(symbol) else painterResource(id)
}
