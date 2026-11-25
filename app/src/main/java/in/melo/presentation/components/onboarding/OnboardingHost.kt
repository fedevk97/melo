package `in`.melo.presentation.components.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.melo.R
import `in`.melo.data.Onboarding
import `in`.melo.data.OnboardingStep
import `in`.melo.data.TourTarget
import `in`.melo.data.UsageAnalytics
import `in`.melo.data.preferences.AppSettings
import `in`.melo.data.preferences.SettingsStore
import `in`.melo.presentation.components.MeloMark
import `in`.melo.presentation.components.PRIVACY_POLICY_URL
import `in`.melo.presentation.icons.MeloIcons
import `in`.melo.ui.glass.MeloAlertShape
import `in`.melo.ui.glass.MeloSegmentedControl
import `in`.melo.ui.glass.MeloSurfaceStyle
import `in`.melo.ui.glass.meloMenuColors
import `in`.melo.ui.glass.meloPopupPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long after launch the first screen waits, so it arrives over a settled Home rather than its entrance. */
private const val START_DELAY_MS = 700L

/** How often a step looks for the control it points at: it may still be on its way in. */
private const val ANCHOR_POLL_MS = 120L

/** The dim behind every card; deeper when a step points at something, so the ring stands out. */
private val PLAIN_SCRIM = Color.Black.copy(alpha = 0.38f)
private val SPOT_SCRIM = Color.Black.copy(alpha = 0.55f)

/** The room between a pointed-at control and its ring, and the ring's weight. */
private val SPOT_MARGIN = 6.dp
private val RING_WIDTH = 2.dp

/** Controls smaller than this get a round spotlight; a larger one, like the speed dial, a rounded frame. */
private val ROUND_SPOT_MAX = 72.dp
private val FRAME_CORNER = 24.dp

/** How far above the bottom of the screen a step's card sits, clear of the navigation bar. */
private val CARD_BOTTOM_GAP = 104.dp

private val CARD_MAX_WIDTH = 340.dp
private val PRIMARY_BUTTON_HEIGHT = 50.dp

/**
 * Where the controls the tour points at are on screen, as each last laid itself out. Plain, not
 * state: the tour reads it while a step is up, and nothing else should recompose because a button moved.
 */
object TourAnchors {
    val bounds = HashMap<TourTarget, Rect>()
}

/** Marks a control as one the first-run tour points at; see [OnboardingHost]. */
fun Modifier.tourAnchor(target: TourTarget): Modifier =
    onGloballyPositioned { TourAnchors.bounds[target] = it.boundsInRoot() }

/**
 * Melo's first run, over the app itself: whether to share usage statistics, a welcome with the
 * choice of theme and style, then a card per part of the app with a ring round the control it is
 * about. Shown once, existing installs included, so everyone is asked about analytics.
 * [onShowLibrary] puts the Library tab (true) or Home (false) behind the card.
 */
@Composable
fun OnboardingHost(onShowLibrary: (Boolean) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf<OnboardingStep?>(null) }
    LaunchedEffect(Unit) {
        if (step != null) return@LaunchedEffect
        val due = withContext(Dispatchers.IO) { Onboarding.shouldShow(context) }
        if (!due) return@LaunchedEffect
        delay(START_DELAY_MS)
        step = OnboardingStep.CONSENT
    }
    val current = step ?: return

    val showLibrary by rememberUpdatedState(onShowLibrary)
    LaunchedEffect(current) {
        if (current.ordinal > OnboardingStep.WELCOME.ordinal) showLibrary(current.onLibrary)
    }
    val finish = {
        Onboarding.markSeen(context)
        if (current.onLibrary) showLibrary(false)
        step = null
    }
    // Back is a step back, and never out: leaving is what Skip is for.
    BackHandler { current.previous()?.let { step = it } }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var spot by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(current) {
        spot = null
        val target = current.target ?: return@LaunchedEffect
        while (true) {
            spot = TourAnchors.bounds[target]?.takeIf { it.width > 0f && it.height > 0f }
            delay(ANCHOR_POLL_MS)
        }
    }
    val ringColor = MaterialTheme.colorScheme.primary
    val ringAlpha by rememberInfiniteTransition(label = "tourRing").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "tourRingAlpha",
    )
    val scrimTarget = if (current.target != null) SPOT_SCRIM.alpha else PLAIN_SCRIM.alpha
    val scrimAlpha by animateFloatAsState(scrimTarget, tween(260), label = "tourScrim")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(320)) }
    val centred = current.target == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Its own layer, for the hole cut out of the dim below.
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = appear.value
                },
        ) {
            drawRect(Color.Black.copy(alpha = scrimAlpha))
            val rect = spot?.translate(-origin) ?: return@Canvas
            val margin = SPOT_MARGIN.toPx()
            val ring = ringColor.copy(alpha = ringAlpha)
            if (maxOf(rect.width, rect.height) <= ROUND_SPOT_MAX.toPx()) {
                val radius = maxOf(rect.width, rect.height) / 2f + margin
                drawCircle(Color.Black, radius, rect.center, blendMode = BlendMode.Clear)
                drawCircle(ring, radius, rect.center, style = Stroke(RING_WIDTH.toPx()))
            } else {
                val frame = rect.inflate(margin)
                val corner = CornerRadius(FRAME_CORNER.toPx())
                drawRoundRect(Color.Black, frame.topLeft, frame.size, corner, blendMode = BlendMode.Clear)
                drawRoundRect(ring, frame.topLeft, frame.size, corner, style = Stroke(RING_WIDTH.toPx()))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .then(if (centred) Modifier else Modifier.navigationBarsPadding().padding(bottom = CARD_BOTTOM_GAP)),
            contentAlignment = if (centred) Alignment.Center else Alignment.BottomCenter,
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(280), initialScale = 0.94f)) togetherWith
                        fadeOut(tween(140)) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                label = "onboardingCard",
                modifier = Modifier.graphicsLayer {
                    val p = appear.value
                    alpha = p
                    val s = 0.92f + 0.08f * p
                    scaleX = s
                    scaleY = s
                },
            ) { shown ->
                OnboardingCard(
                    step = shown,
                    onAgree = {
                        setAnalytics(context, true)
                        step = OnboardingStep.WELCOME
                    },
                    onDecline = {
                        setAnalytics(context, false)
                        step = OnboardingStep.WELCOME
                    },
                    onNext = { shown.next()?.let { step = it } ?: finish() },
                    onSkip = finish,
                )
            }
        }
    }
}

private fun setAnalytics(context: android.content.Context, enabled: Boolean) {
    SettingsStore.get(context).update { it.copy(analyticsEnabled = enabled) }
    UsageAnalytics.setEnabled(context, enabled)
}

@Composable
private fun OnboardingCard(
    step: OnboardingStep,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = meloMenuColors()
    Column(
        modifier = Modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .fillMaxWidth()
            .meloPopupPanel(MeloAlertShape, frost = false, colors = colors)
            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            OnboardingStep.CONSENT -> ConsentHeader()
            OnboardingStep.WELCOME -> MeloMark(
                refreshing = false,
                pullFraction = { 0f },
                animate = true,
                height = 44.dp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            OnboardingStep.FINISH -> CardIcon(MeloIcons.CheckCircle)
            else -> Text(
                text = stringResource(R.string.onboarding_step_count, step.number, OnboardingStep.count),
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryLabel,
            )
        }
        Text(
            text = stringResource(step.titleRes()),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(step.bodyRes()),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (step == OnboardingStep.WELCOME) LookChoices(colors.secondaryLabel)
        Box(Modifier.height(20.dp))
        when (step) {
            OnboardingStep.CONSENT -> {
                PrimaryButton(stringResource(R.string.onboarding_consent_agree), onAgree)
                QuietButton(stringResource(R.string.onboarding_consent_decline), colors.secondaryLabel, onDecline)
                PolicyLink(colors.secondaryLabel)
            }
            OnboardingStep.WELCOME -> {
                PrimaryButton(stringResource(R.string.onboarding_take_tour), onNext)
                QuietButton(stringResource(R.string.onboarding_skip_tour), colors.secondaryLabel, onSkip)
            }
            OnboardingStep.FINISH -> {
                PrimaryButton(stringResource(R.string.onboarding_finish), onSkip)
                Box(Modifier.height(8.dp))
            }
            else -> {
                PrimaryButton(stringResource(R.string.onboarding_next), onNext)
                QuietButton(stringResource(R.string.onboarding_skip), colors.secondaryLabel, onSkip)
            }
        }
    }
}

@Composable
private fun ConsentHeader() {
    CardIcon(MeloIcons.ShowChart)
}

@Composable
private fun CardIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .padding(bottom = 14.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
    }
}

/** The one filled button on a card: the way forward. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = if (primary.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PRIMARY_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(PRIMARY_BUTTON_HEIGHT / 2))
            .background(primary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = ink)
    }
}

/** The quiet way out under the filled button: plain text, still a full-width target. */
@Composable
private fun QuietButton(label: String, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = ink)
    }
}

@Composable
private fun PolicyLink(ink: Color) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.privacy_policy_link),
        style = MaterialTheme.typography.labelMedium,
        color = ink.copy(alpha = ink.alpha * 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * The welcome's two choices of look, the theme and the app style: the same settings as in
 * Settings, so the app behind the card, and the card itself, change as they are picked. On the
 * welcome rather than a step of the tour, so skipping the tour doesn't skip them.
 */
@Composable
private fun LookChoices(secondary: Color) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore.get(context) }
    val settings by store.settings.collectAsStateWithLifecycle()
    val themes = listOf(AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK)
    val styles = MeloSurfaceStyle.entries

    LookHeading(stringResource(R.string.settings_app_theme_title), secondary, top = 18.dp)
    MeloSegmentedControl(
        options = listOf(
            stringResource(R.string.onboarding_theme_system),
            stringResource(R.string.onboarding_theme_light),
            stringResource(R.string.onboarding_theme_dark),
        ),
        selectedIndex = themes.indexOf(settings.themeMode),
        onSelect = { i -> store.update { it.copy(themeMode = themes[i]) } },
        modifier = Modifier.fillMaxWidth(),
    )
    LookHeading(stringResource(R.string.settings_app_style_title), secondary, top = 14.dp)
    MeloSegmentedControl(
        options = styles.map {
            stringResource(
                when (it) {
                    MeloSurfaceStyle.GLASS -> R.string.onboarding_style_glass
                    MeloSurfaceStyle.FROST -> R.string.settings_app_style_frost
                    MeloSurfaceStyle.SOLID -> R.string.settings_app_style_solid
                },
            )
        },
        selectedIndex = styles.indexOf(MeloSurfaceStyle.of(settings.surfaceStyle)),
        onSelect = { i -> store.update { it.copy(surfaceStyle = styles[i].key) } },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.onboarding_look_note),
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = secondary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LookHeading(text: String, color: Color, top: Dp) {
    Row(Modifier.fillMaxWidth().padding(top = top, bottom = 8.dp), horizontalArrangement = Arrangement.Start) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

private fun OnboardingStep.titleRes(): Int = when (this) {
    OnboardingStep.CONSENT -> R.string.onboarding_consent_title
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
    OnboardingStep.SPEED_DIAL -> R.string.onboarding_speed_dial_title
    OnboardingStep.PINS -> R.string.onboarding_pins_title
    OnboardingStep.HOME_MENU -> R.string.onboarding_home_menu_title
    OnboardingStep.HISTORY -> R.string.onboarding_history_title
    OnboardingStep.PROFILE -> R.string.onboarding_profile_title
    OnboardingStep.LIBRARY -> R.string.onboarding_library_title
    OnboardingStep.FINISH -> R.string.onboarding_finish_title
}

private fun OnboardingStep.bodyRes(): Int = when (this) {
    OnboardingStep.CONSENT -> R.string.onboarding_consent_body
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_body
    OnboardingStep.SPEED_DIAL -> R.string.onboarding_speed_dial_body
    OnboardingStep.PINS -> R.string.onboarding_pins_body
    OnboardingStep.HOME_MENU -> R.string.onboarding_home_menu_body
    OnboardingStep.HISTORY -> R.string.onboarding_history_body
    OnboardingStep.PROFILE -> R.string.onboarding_profile_body
    OnboardingStep.LIBRARY -> R.string.onboarding_library_body
    OnboardingStep.FINISH -> R.string.onboarding_finish_body
}
