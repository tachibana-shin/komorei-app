package git.shin.komorei.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.Player as ComposePlayer
import coil.compose.AsyncImage
import git.shin.komorei.R
import git.shin.komorei.ui.player.components.*
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Vertical-drag gesture regions on the video surface. */
private const val DRAG_NONE = 0
private const val DRAG_VOLUME = 1
private const val DRAG_BRIGHTNESS = 2

/** Bottom 20% of the video is reserved for control gestures (slider, buttons). */
private const val BOTTOM_CONTROL_ZONE_RATIO = 0.8f

/**
 * Fraction of fillZoom a sub-fill pinch must reach on release to "commit" to
 * filling the screen. Below it the pinch bounces back to fit (scale 1), like
 * YouTube's adaptive-zoom ingress threshold.
 */
private const val ZOOM_COMMIT_FRACTION = 0.9f

/**
 * Exponential-approach factor for pinch-zoom scale per pointer event. 1 = raw
 * 1:1 finger tracking (jittery on a TextureView — every frame re-samples the
 * surface); <1 damps finger jitter into smooth motion without the steppy feel
 * of a hard deadband. 0.4 stays responsive to fast pinches while removing
 * frame-to-frame finger noise.
 */
private const val ZOOM_SMOOTHING_FACTOR = 0.4f

/** Minimum scale change that still triggers a graphicsLayer render during pinch. */
private const val ZOOM_EPSILON = 0.004f

/** A 1-finger drag pans the video only when zoomed at least this far beyond fill. */
private const val ZOOM_PAN_MARGIN = 0.01f

/** Maximum pinch-zoom scale (YouTube-style cap). */
private const val ZOOM_MAX_SCALE = 2.5f

/** Transient HUD shown while swiping volume (left half) or brightness (right half). */
private data class DragHud(val isVolume: Boolean, val level: Float)

/** YouTube-style double-tap seek HUD: remembered seek amount + where the tap landed. */
private data class SeekHud(val deltaMs: Long, val xFraction: Float)

@Composable
fun PlayerVideoArea(
    player: Player,
    title: String,
    episodeTitle: String,
    posterUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    isFullscreen: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    currentSpeed: Float,
    currentQuality: String?,
    isSubtitleEnabled: Boolean,
    introRange: LongRange?,
    outroRange: LongRange?,
    streamData: git.shin.komorei.model.StreamData?,
    playbackError: String?,
    initialBrightness: Float,
    isLocked: Boolean,
    onMinimizeClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStartFastForward: () -> Unit,
    onStopFastForward: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenServers: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    skipHint: SkipHint? = null,
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // Only show poster background if we don't have stream data yet (initial load)
        val showPosterBackground = streamData == null && !isLocked

        var showControls by remember { mutableStateOf(true) }
        var interactionCounter by remember { mutableStateOf(0) }
        // Measured height of the bottom controls footer (px), so the skip pill can
        // float just above it instead of using a fixed offset tuned for fullscreen —
        // on the short 16:9 sheet box that offset put the pill mid-video.
        var footerHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        var pointerDown by remember { mutableStateOf(false) }
        var seekHud by remember { mutableStateOf<SeekHud?>(null) }
        var fastForwarding by remember { mutableStateOf(false) }
        var dragHud by remember { mutableStateOf<DragHud?>(null) }
        // Pinch-zoom level pill (0 = hidden). Written every pinch event; the auto-clear
        // effect below restarts its delay on each write and hides it after the pinch ends.
        var zoomHud by remember { mutableStateOf(0f) }

        // Always-current brightness for the volume/brightness drag base. MUST NOT be
        // used as a pointerInput key: brightness changes while dragging (via
        // onBrightnessChange) would otherwise cancel the drag coroutine mid-gesture
        // and skip onDragEnd, leaving the HUD pill stuck on screen.
        val currentInitialBrightness by rememberUpdatedState(initialBrightness)

        // --- Zoom state (YouTube-style) ---
        // Track the video's intrinsic aspect ratio so letterboxed videos know how much
        // they actually cover at each scale — and when panning would reveal black bars.
        var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
        LaunchedEffect(player) {
            fun VideoSize.renderedAspectRatio(): Float {
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return 16f / 9f
                val rotated = unappliedRotationDegrees % 180 == 90
                // In media3 1.11 width/height are already the post-rotation dimensions and
                // pixelWidthHeightRatio holds the true sample aspect — without it anamorphic
                // content (e.g. SD MPEG-2) gets a WRONG content width that is larger than
                // what is actually rendered, and panning slides into the black bars.
                val pixelAspect = pixelWidthHeightRatio.takeIf { it > 0f && it.isFinite() } ?: 1f
                return ((if (rotated) h / w else w / h) * pixelAspect)
                    .takeIf { it.isFinite() }
                    ?: (16f / 9f)
            }
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoAspect = videoSize.renderedAspectRatio()
                }
            }
            player.addListener(listener)
            try {
                videoAspect = player.videoSize.renderedAspectRatio()
                awaitCancellation()
            } finally {
                player.removeListener(listener)
            }
        }

        // Fit geometry of the rendered video content (centered letterbox inside the view).
        // media3's Compose Player() renders into a raw TextureView that ALWAYS aspect-fits
        // (codec SCALE_TO_FIT into the view-sized surface buffer). So the content rect is
        // always the FIT rect — the aspect-ratio picker is gone (pinch-zoom replaces it);
        // treating the rect as anything but FIT made `contentW` equal the full box width
        // (bigger than the real rendered content) and panning drifted into the bars.
        val viewAspect = widthPx / heightPx
        val contentW =
            if (videoAspect >= viewAspect) widthPx else heightPx * videoAspect
        val contentH =
            if (videoAspect >= viewAspect) widthPx / videoAspect else heightPx
        // Smallest zoom where the content covers the whole screen (no bars anywhere).
        // Below it the video is still letterboxed — YouTube's "adaptive" zoom animates
        // back to 1 and keeps the player centered/immovable; only above it panning is allowed.
        val fillZoom = (widthPx / contentW)
            .coerceAtLeast(heightPx / contentH)
            .coerceAtLeast(1f)
            .let { if (it < 1.02f) 1f else it }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        // Smooth adaptive snap-back: Animatable-driven animation used ONLY after the
        // user releases a sub-fill pinch. While no snap-back is running the display
        // reads the raw gesture state directly (no async state dance mid-gesture).
        var snapBackActive by remember { mutableStateOf(false) }
        var snapBackJob by remember { mutableStateOf<Job?>(null) }
        val zoomScope = rememberCoroutineScope()
        val animScale = remember { Animatable(1f) }
        val animOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        val displayScale = if (snapBackActive) animScale.value else scale
        val displayOffset = if (snapBackActive) animOffset.value else offset

        // --- Zoom across fullscreen ⇄ normal toggles ---
        // Both modes can zoom, but a zoom only survives a mode switch when it is exactly 1
        // (fit) or the FILL zoom of the mode being left — any other value resets to 1.
        // fillZoom differs per mode (a 16:9 video fills a 16:9 sheet box at 1.0 but a
        // 20:9 fullscreen at ~1.25), so remember each mode's fill and compare against the
        // mode the user is LEAVING (at toggle time fillZoom has already recomputed for the
        // NEW geometry, so the leaving-mode value must come from this map).
        val fillByMode = remember { mutableStateMapOf<Boolean, Float>() }
        LaunchedEffect(isFullscreen, fillZoom) {
            fillByMode[isFullscreen] = fillZoom
        }
        LaunchedEffect(isFullscreen) {
            // Adopt any running snap-back first so the keep-check sees what's displayed.
            snapBackJob?.cancel()
            snapBackJob = null
            if (snapBackActive) {
                snapBackActive = false
                scale = animScale.value
                offset = animOffset.value
            }
            val leavingFill = fillByMode[!isFullscreen] ?: 1f
            val isFitZoom = scale <= 1f + ZOOM_EPSILON
            val isFillZoom = abs(scale - leavingFill) < ZOOM_EPSILON
            if (!isFitZoom && !isFillZoom) {
                scale = 1f
                offset = Offset.Zero
            }
        }

        // Fresh references for the shared gesture handler below. The pointerInput
        // block is keyed on (player, isLocked) only, so geometry that changes with
        // the video (aspect ratio, letterbox) must be read through updated-state refs.
        val currentFillZoom by rememberUpdatedState(fillZoom)
        val currentContentW by rememberUpdatedState(contentW)
        val currentContentH by rememberUpdatedState(contentH)
        // The shared gesture handler is keyed only on (player, isLocked), so it does NOT
        // restart when the surface resizes (sheet drag ⇄ fullscreen toggle). Capturing the
        // box size once at launch would freeze the pan/pinch clamps at the SHEET size while
        // the surface is fullscreen: maxX = (contentW·scale − staleW)/2 becomes much larger
        // than the real video edges, letting a drag pull the letterbox bars onto the screen.
        // Read the live box size through updated-state refs instead.
        val currentViewWidth by rememberUpdatedState(widthPx)
        val currentViewHeight by rememberUpdatedState(heightPx)
        // Whether the control overlay is visible — read through an updated-state ref
        // because the pointerInput block is keyed only on (player, isLocked).
        val currentShowControls by rememberUpdatedState(showControls)
        // Volume/brightness swipes only work in fullscreen — read through an
        // updated-state ref (the pointerInput coroutine isn't restarted by a toggle).
        val currentIsFullscreen by rememberUpdatedState(isFullscreen)

        // Render-time safety net: keep the offset inside what the CURRENT content rect can
        // cover — a mode toggle or snap-back adoption can briefly land it out of bounds,
        // which would pull the letterbox bars into view until the next gesture re-clamps it.
        LaunchedEffect(scale, offset, currentContentW, currentContentH, isFullscreen) {
            val mx = ((currentContentW * scale - currentViewWidth) / 2f).coerceAtLeast(0f)
            val my = ((currentContentH * scale - currentViewHeight) / 2f).coerceAtLeast(0f)
            val clamped = Offset(offset.x.coerceIn(-mx, mx), offset.y.coerceIn(-my, my))
            if (clamped != offset) offset = clamped
        }

        // Auto-hide controls after 3.5s of inactivity while playing (paused while touching).
        LaunchedEffect(showControls, interactionCounter, isPlaying, pointerDown, isLocked, isLoading) {
            if (showControls && isPlaying && !pointerDown && !isLocked && !isLoading) {
                delay(3500)
                showControls = false
            }
        }

        // Auto-clear the double-tap seek HUD.
        LaunchedEffect(seekHud) {
            if (seekHud != null) {
                delay(900)
                seekHud = null
            }
        }

        // Watchdog: clear the volume/brightness pill even if onDragEnd /
        // onDragCancel is skipped (e.g. the drag coroutine is cancelled),
        // so it never lingers after the finger lifts.
        LaunchedEffect(dragHud) {
            if (dragHud != null) {
                delay(250)
                dragHud = null
            }
        }

        // Auto-clear the pinch-zoom level pill shortly after the last zoom update.
        LaunchedEffect(zoomHud) {
            if (zoomHud > 0f) {
                delay(900)
                zoomHud = 0f
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = displayScale,
                        scaleY = displayScale,
                        translationX = displayOffset.x,
                        translationY = displayOffset.y
                    )
            ) {
                ComposePlayer(
                    player = player,
                    showControls = false, // We use our own controls
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier
                        .fillMaxSize()
                        .playerGestures(
                            onToggleControls = {
                                showControls = !showControls
                                interactionCounter++
                            },
                            onSeekBy = { delta, xFraction ->
                                if (!isLocked) {
                                    seekRelative(player, delta)
                                    // Repeated double-taps accumulate in the HUD (like
                                    // YouTube: +10, +20, +30...) while each tap seeks.
                                    seekHud = SeekHud((seekHud?.deltaMs ?: 0L) + delta, xFraction)
                                    interactionCounter++
                                }
                            },
                            onFastForwardStart = {
                                if (!isLocked) {
                                    fastForwarding = true
                                    onStartFastForward()
                                    interactionCounter++
                                }
                            },
                            onFastForwardEnd = {
                                if (!isLocked) {
                                    fastForwarding = false
                                    onStopFastForward()
                                }
                            },
                            onPointerDownChange = { pointerDown = it },
                            onPointerMove = { interactionCounter++ }
                        )
                        .pointerInput(player, isLocked) {
                            if (isLocked) return@pointerInput
                            val touchSlop = viewConfiguration.touchSlop

                            // Single combined gesture handler:
                            //  - 1 finger, zoomed beyond fill → drag PANS the video.
                            //  - 1 finger, fit/scale-1 state → vertical swipe adjusts volume
                            //    (right half) / brightness (left half), only active AFTER the
                            //    drag crosses touch slop so a plain tap never adjusts anything.
                            //  - 2 fingers → pinch zoom + pan, following the fingers directly
                            //    (scale exponentially smoothed per event to kill jitter). The
                            //    adaptive fill clamp / ingress is applied only on finger lift,
                            //    never mid-gesture (that caused the snapping-while-zooming jank).
                            // NOTE: we do NOT consume the DOWN — playerGestures (outer modifier)
                            // needs an unconsumed DOWN for tap detection.
                            awaitEachGesture {
                                // The returned Down is not needed — the call itself
                                // is what suspends until the first pointer event.
                                awaitFirstDown(requireUnconsumed = false)

                                // Cancel any in-flight snap-back from a previous gesture. If the
                                // animation was still running (snapBackActive), adopt its current
                                // animated position so this gesture starts where the video appears;
                                // a fully finished snap-back already settled at scale=1, no adopt.
                                snapBackJob?.cancel()
                                snapBackJob = null
                                if (snapBackActive) {
                                    snapBackActive = false
                                    scale = animScale.value
                                    offset = animOffset.value
                                }

                                var regionLocked = false
                                var region = DRAG_NONE
                                var baseLevel = 0f
                                var dragActive = false
                                var preSlopDrag = 0f
                                var accumulated = 0f

                                var zooming = false
                                var zoomedInGesture = false
                                var initialSpan = 0f
                                var initialZoomScale = 1f

                                // 1-finger pan (zoomed beyond fill) uses the same touch-slop
                                // gate as the volume/brightness swipe: the DOWN and micro-moves
                                // are never consumed, so a plain tap still toggles controls.
                                var panActive = false
                                var panPreSlop = Offset.Zero

                                fun lockRegion(pos: Offset) {
                                    // Reserve the bottom band ONLY while the control overlay is
                                    // visible (that's where the slider/buttons live). When hidden
                                    // (immersive fullscreen) the whole surface responds — otherwise
                                    // the natural thumb position near the bottom edge would land in
                                    // a dead zone and volume/brightness swipes never activate.
                                    // A swipe that STARTS in the band is not hard-locked: the region
                                    // keeps re-evaluating until the finger leaves it (or controls
                                    // auto-hide), so it can pick up volume/brightness mid-gesture.
                                    val bandActive =
                                        currentShowControls && pos.y > currentViewHeight * BOTTOM_CONTROL_ZONE_RATIO
                                    region = when {
                                        bandActive -> DRAG_NONE
                                        pos.x < currentViewWidth / 2f -> DRAG_BRIGHTNESS
                                        else -> DRAG_VOLUME
                                    }
                                    if (region != DRAG_NONE) {
                                        regionLocked = true
                                        baseLevel = when (region) {
                                            DRAG_VOLUME -> player.volume
                                            DRAG_BRIGHTNESS -> currentInitialBrightness
                                            else -> 0f
                                        }
                                    }
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        // Two fingers → zoom + pan. Follow the fingers exactly;
                                        // no fillZoom clamp while the user is still zooming.
                                        dragHud = null
                                        regionLocked = false
                                        dragActive = false
                                        preSlopDrag = 0f
                                        // A pinch supersedes any in-flight 1-finger pan — re-arm
                                        // the slop gate so the eventual 2→1 tail starts a clean
                                        // drag path instead of auto-panning from the old state.
                                        panActive = false
                                        panPreSlop = Offset.Zero
                                        val p0 = pressed[0]
                                        val p1 = pressed[1]
                                        val span = (p0.position - p1.position).getDistance()
                                        if (!zooming) {
                                            zooming = true
                                            zoomedInGesture = true
                                            initialSpan = span
                                            initialZoomScale = scale
                                            interactionCounter++ // once per pinch (keeps controls awake)
                                        } else if (initialSpan > 0f) {
                                            // Smooth the raw finger ratio: exponential approach to
                                            // the target each event damps finger jitter (which
                                            // re-sampled the TextureView every frame = the "player
                                            // size flickers" jank) WITHOUT the steppy feel of a
                                            // hard deadband — slow pinches stay fluid.
                                            val target = (initialZoomScale * span / initialSpan)
                                                .coerceIn(1f, ZOOM_MAX_SCALE)
                                            val smoothed = scale + (target - scale) * ZOOM_SMOOTHING_FACTOR
                                            if (abs(smoothed - scale) >= ZOOM_EPSILON) {
                                                scale = smoothed
                                            }
                                            zoomHud = scale
                                            val pan = Offset(
                                                x = (p0.position.x - p0.previousPosition.x +
                                                    p1.position.x - p1.previousPosition.x) / 2f,
                                                y = (p0.position.y - p0.previousPosition.y +
                                                    p1.position.y - p1.previousPosition.y) / 2f
                                            )
                                            val maxX = ((currentContentW * scale - currentViewWidth) / 2f)
                                                .coerceAtLeast(0f)
                                            val maxY = ((currentContentH * scale - currentViewHeight) / 2f)
                                                .coerceAtLeast(0f)
                                            offset = Offset(
                                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else {
                                        // One finger (or the tail of a pinch).
                                        if (zooming) {
                                            zooming = false
                                            initialSpan = 0f
                                            // No volume/brightness drag for the rest of this
                                            // gesture — a momentary 2→1 flicker while pinching
                                            // must not leak volume changes mid-zoom.
                                            regionLocked = false
                                            dragActive = false
                                            preSlopDrag = 0f
                                        }
                                        val drag = pressed.firstOrNull() ?: break

                                        if (scale > currentFillZoom + ZOOM_PAN_MARGIN) {
                                            // Zoomed beyond fullscreen-fill: a one-finger drag
                                            // PANS the video (YouTube-style) instead of adjusting
                                            // volume/brightness — at this zoom the whole surface
                                            // is video, so swipes must move it, not change levels.
                                            // Slop-gated like the volume/brightness swipe: the DOWN
                                            // and micro-moves are never consumed, so a plain tap
                                            // still toggles controls (detectTapGestures needs an
                                            // unconsumed DOWN).
                                            val pan = drag.position - drag.previousPosition
                                            if (!panActive) {
                                                panPreSlop += pan
                                                if (abs(panPreSlop.x) >= touchSlop ||
                                                    abs(panPreSlop.y) >= touchSlop
                                                ) {
                                                    panActive = true
                                                }
                                            }
                                            if (panActive) {
                                                // First applied delta absorbs the accumulated
                                                // pre-slop so the video doesn't lurch forward by
                                                // slop pixels the instant the pan activates.
                                                val slopOffset = panPreSlop
                                                panPreSlop = Offset.Zero
                                                val applied = Offset(
                                                    x = pan.x - slopOffset.x,
                                                    y = pan.y - slopOffset.y
                                                )
                                                val panMaxX = ((currentContentW * scale - currentViewWidth) / 2f)
                                                    .coerceAtLeast(0f)
                                                val panMaxY = ((currentContentH * scale - currentViewHeight) / 2f)
                                                    .coerceAtLeast(0f)
                                                offset = Offset(
                                                    x = (offset.x + applied.x).coerceIn(-panMaxX, panMaxX),
                                                    y = (offset.y + applied.y).coerceIn(-panMaxY, panMaxY)
                                                )
                                                drag.consume()
                                            }
                                        } else if (!zoomedInGesture && currentIsFullscreen) {
                                            val dragAmount = drag.position.y - drag.previousPosition.y

                                            if (!regionLocked) {
                                                lockRegion(drag.position)
                                            }
                                            if (region != DRAG_NONE) {
                                                if (!dragActive) {
                                                    // Ignore micro-movements (a tap) — only start
                                                    // adjusting once the swipe crosses touch slop.
                                                    preSlopDrag += dragAmount
                                                    if (abs(preSlopDrag) >= touchSlop) {
                                                        dragActive = true
                                                        accumulated = 0f
                                                        dragHud = DragHud(
                                                            isVolume = region == DRAG_VOLUME,
                                                            level = baseLevel
                                                        )
                                                    }
                                                }
                                                if (dragActive) {
                                                    accumulated -= dragAmount
                                                    // IMPORTANT: base stays fixed at the value
                                                    // captured when the drag started. Re-reading
                                                    // the previous output as the base double-counts
                                                    // the cumulative `accumulated` (super-linear
                                                    // growth, and the value sticks at 0/100% once
                                                    // clamped — swiping back would do nothing).
                                                    // Full-range swipe = 2x view height: a
                                                    // half-screen swipe = ±25%.
                                                    val level =
                                                        (baseLevel + accumulated / (currentViewHeight * 2f))
                                                            .coerceIn(0f, 1f)
                                                    if (region == DRAG_VOLUME) {
                                                        player.volume = level
                                                    } else {
                                                        onBrightnessChange(level)
                                                    }
                                                    dragHud = DragHud(isVolume = region == DRAG_VOLUME, level = level)
                                                    drag.consume()
                                                }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                // Full gesture ended (all fingers lifted).
                                dragHud = null
                                if (scale > 1.001f && scale < currentFillZoom) {
                                    // Adaptive + ingress (YouTube-style): a sub-fill release that got
                                    // CLOSE to filling the screen (>= ~90% of fill) COMMITS by
                                    // animating UP to fill mode instead of bouncing; a weak pinch
                                    // bounces back to fit. Applied only on release — never
                                    // mid-gesture. The next gesture's DOWN cancels any in-flight
                                    // animation and adopts its position.
                                    val targetScale = if (scale >= currentFillZoom * ZOOM_COMMIT_FRACTION) {
                                        minOf(currentFillZoom, ZOOM_MAX_SCALE)
                                    } else {
                                        1f
                                    }
                                    snapBackActive = true
                                    snapBackJob = zoomScope.launch {
                                        animScale.snapTo(scale)
                                        animOffset.snapTo(offset)
                                        animOffset.animateTo(Offset.Zero, animationSpec = tween(180))
                                        animScale.animateTo(targetScale, animationSpec = tween(260, easing = EaseOutCubic))
                                        scale = targetScale
                                        offset = Offset.Zero
                                        snapBackJob = null
                                        snapBackActive = false
                                    }
                                }
                            }
                        }
                )
            }

            // Backdrop dim when controls shown
            AnimatedVisibility(
                visible = (showControls || showPosterBackground) && !isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                    if (showPosterBackground) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(0.6f)
                        )
                    }
                }
            }

            // Custom Overlays
            if (!isLocked) {
                // Header
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    PlayerControlHeader(
                        title = title,
                        episodeTitle = episodeTitle,
                        isSubtitleEnabled = isSubtitleEnabled,
                        isFullscreen = isFullscreen,
                        onBackClick = onMinimizeClick,
                        onSubtitleToggle = onToggleSubtitles,
                        onSubtitleLongClick = onOpenSubtitleMenu,
                        onSettingsClick = onOpenSettings
                    )
                }

                // Center
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CenterPlayerControl(
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        isFullscreen = isFullscreen,
                        onPlayPause = {
                            if (isPlaying) player.pause() else player.play()
                            interactionCounter++
                        },
                        onSeekRelative = {
                            seekRelative(player, it)
                            // Center buttons accumulate in the HUD too.
                            seekHud = SeekHud((seekHud?.deltaMs ?: 0L) + it, if (it < 0) 0.25f else 0.75f)
                            interactionCounter++
                        }
                    )
                }

                // Footer
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    PlayerControlFooter(
                        positionMs = currentPositionMs,
                        durationMs = durationMs,
                        bufferedPositionMs = bufferedPositionMs,
                        introRange = introRange,
                        outroRange = outroRange,
                        isFullscreen = isFullscreen,
                        isPlaying = isPlaying,
                        currentSpeed = currentSpeed,
                        currentQuality = currentQuality,
                        onSeek = { player.seekTo(it); interactionCounter++ },
                        onToggleFullscreen = onToggleFullscreen,
                        onNextEpisode = onNextEpisode,
                        onOpenEpisodes = onOpenEpisodes,
                        onOpenServers = onOpenServers,
                        onOpenSettings = onOpenSettings,
                        onInteraction = { interactionCounter++ },
                        modifier = Modifier.fillMaxWidth().onSizeChanged { footerHeightPx = it.height }
                    )
                }

                // Skip intro/outro pill (SponsorBlock-style) — shown whenever the
                // playhead is inside a range, even when the controls are hidden.
                // It floats just above the bottom controls when those are visible,
                // and hugs the bottom edge when they're not: the old fixed offset
                // (104dp) was tuned for fullscreen and landed the pill mid-video on
                // the short 16:9 sheet box (the "50% y" bug). bottom is measured
                // from the real footer height so it adapts to any box size.
                if (skipHint != null) {
                    val pillBottom by animateDpAsState(
                        targetValue = when {
                            showControls && footerHeightPx > 0 ->
                                with(density) { footerHeightPx.toDp() } + 12.dp
                            showControls -> if (isFullscreen) 132.dp else 104.dp
                            else -> 16.dp
                        },
                        label = "skipPillBottom"
                    )
                    SkipSegmentPill(
                        kind = skipHint.kind,
                        onClick = onSkip,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = 16.dp,
                                bottom = pillBottom
                            )
                    )
                }
            } else {
                // Unlock button only
                IconButton(
                    onClick = onToggleLock,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        // TV focus highlight (no-op on phones). Sibling overlay of the
                        // player surface — not an ancestor of its TextureView, so the
                        // graphicsLayer is safe (same rule as the skip pill).
                        .tvFocus(shape = CircleShape, scale = 1.1f)
                        .padding(24.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.player_unlock),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Gesture HUDs — YouTube-style double-tap seek indicator: a small icon
            // popping in at the tap position (no background circle/pill), then fading
            // away. The whole overlay only fades; the icon scales in place so it never
            // drifts from the tap position during the pop animation.
            AnimatedVisibility(
                visible = seekHud != null,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(220)),
                modifier = Modifier.fillMaxSize()
            ) {
                seekHud?.let { hud ->
                    // The indicator pops in on the SIDE where the tap/drag happened instead
                    // of the center: its centre is shifted to xFraction * width, so a left-
                    // half double-tap (rewind) shows on the left, right-half (forward) on
                    // the right, and a drag-seek follows the finger. Clamped a bit from the
                    // edges so the pill can't clip off-screen. Pop animation runs once on
                    // entry (remember survives hud changes); repeated double-tap
                    // accumulations only update the text without replaying it.
                    val popScale = remember { Animatable(0.55f) }
                    LaunchedEffect(Unit) {
                        popScale.animateTo(1f, animationSpec = tween(220, easing = EaseOutBack))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = popScale.value
                                scaleY = popScale.value
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset {
                                // Shift the centred column from 0.5 to (xFraction) of the width.
                                IntOffset(
                                    x = (widthPx * (hud.xFraction.coerceIn(0.15f, 0.85f) - 0.5f)).roundToInt(),
                                    y = 0
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (hud.deltaMs < 0) {
                                    Icons.Default.Replay10
                                } else {
                                    Icons.Default.Forward10
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (hud.deltaMs < 0) {
                                    stringResource(R.string.player_seek_backward, abs(hud.deltaMs / 1000))
                                } else {
                                    stringResource(R.string.player_seek_forward, abs(hud.deltaMs / 1000))
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Volume / brightness swipe HUD — horizontal flex pill (icon | bar | value)
            // near the top of the screen, rounded + translucent like YouTube.
            AnimatedVisibility(
                visible = dragHud != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                dragHud?.let { hud ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hud.isVolume) {
                                Icons.AutoMirrored.Filled.VolumeUp
                            } else {
                                Icons.Filled.BrightnessMedium
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        LinearProgressIndicator(
                            progress = { hud.level },
                            modifier = Modifier
                                .width(110.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50)),
                            color = AnimeRed,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Fixed-width value column so digits changing mid-drag
                        // (42% -> 43% -> ...) don't wiggle the whole pill.
                        Text(
                            text = "${(hud.level * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            }

            // Pinch-zoom level pill — a small fixed-center HUD showing the current
            // scale (e.g. "1.5x") while zooming, capped at ZOOM_MAX_SCALE. Mirrors the
            // volume/brightness pill style, text-only.
            AnimatedVisibility(
                visible = zoomHud > 0f,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = stringResource(R.string.player_zoom_level, zoomHud),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            // Global loading indicator (always visible when buffering)
            if ((isLoading || streamData == null) && !isLocked) {
                CircularProgressIndicator(
                    color = AnimeRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
    }
}

private fun seekRelative(player: Player, deltaMs: Long) {
    val max = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
    player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, max))
}
