package com.nivukx.music.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nivukx motion system.
 *
 * Motion is fast, coordinated and state-driven. Shared UI surfaces should animate
 * from one source of truth instead of stacking independent enter/exit animations.
 * Frame-critical work stays inside graphicsLayer/draw transforms.
 */
object NivukxMotion {
    const val Micro = 120
    const val Standard = 220
    const val Emphasis = 320
    const val Content = 240
    const val PlayerMorph = 280
    const val PremiumScreen = 300
    const val Navigation = 300
    const val MicroSettle = 180

    val StandardEasing = FastOutSlowInEasing
    val PremiumEasing = CubicBezierEasing(0.18f, 1f, 0.32f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val StandardTween = tween<Float>(
        durationMillis = Standard,
        easing = StandardEasing,
    )

    val ContentTween = tween<Float>(
        durationMillis = Content,
        easing = StandardEasing,
    )

    val PlayerMorphTween = tween<Float>(
        durationMillis = PlayerMorph,
        easing = StandardEasing,
    )

    val SoftSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val PlayerSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val HighEndSpring = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 480f,
    )

    val FluidSpring = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = 420f,
    )

    val MagneticSpring = spring<Float>(
        dampingRatio = 0.94f,
        stiffness = 700f,
    )

    val PrecisionSpring = spring<Float>(
        dampingRatio = 0.92f,
        stiffness = 620f,
    )

    val GestureSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 520f,
    )

    val NavigationEnterForward: EnterTransition =
        fadeIn(tween(Navigation, easing = PremiumEasing)) +
            slideInHorizontally(
                initialOffsetX = { it / 12 },
                animationSpec = tween(Navigation, easing = PremiumEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Navigation, easing = PremiumEasing),
            )

    val NavigationExitForward: ExitTransition =
        fadeOut(tween(210, easing = ExitEasing)) +
            slideOutHorizontally(
                targetOffsetX = { -it / 18 },
                animationSpec = tween(210, easing = ExitEasing),
            ) +
            scaleOut(
                targetScale = 0.992f,
                animationSpec = tween(210, easing = ExitEasing),
            )

    val NavigationEnterBackward: EnterTransition =
        fadeIn(tween(Navigation, easing = PremiumEasing)) +
            slideInHorizontally(
                initialOffsetX = { -it / 12 },
                animationSpec = tween(Navigation, easing = PremiumEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Navigation, easing = PremiumEasing),
            )

    val NavigationExitBackward: ExitTransition =
        fadeOut(tween(210, easing = ExitEasing)) +
            slideOutHorizontally(
                targetOffsetX = { it / 18 },
                animationSpec = tween(210, easing = ExitEasing),
            ) +
            scaleOut(
                targetScale = 0.992f,
                animationSpec = tween(210, easing = ExitEasing),
            )

    val Enter: EnterTransition =
        fadeIn(
            animationSpec = tween(Standard, easing = StandardEasing),
        ) +
            slideInVertically(
                initialOffsetY = { (it * 0.018f).toInt() },
                animationSpec = tween(Standard, easing = StandardEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Standard, easing = StandardEasing),
            )

    val Exit: ExitTransition =
        fadeOut(
            animationSpec = tween(170, easing = ExitEasing),
        ) +
            slideOutVertically(
                targetOffsetY = { -(it * 0.012f).toInt() },
                animationSpec = tween(170, easing = ExitEasing),
            ) +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = tween(170, easing = ExitEasing),
            )

    val SheetEnter: EnterTransition =
        fadeIn(
            animationSpec = tween(180, easing = PremiumEasing),
        ) +
            slideInVertically(
                initialOffsetY = { (it * 0.035f).toInt() },
                animationSpec = tween(Standard, easing = PremiumEasing),
            ) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(Standard, easing = PremiumEasing),
            )

    val SheetExit: ExitTransition =
        fadeOut(
            animationSpec = tween(160, easing = StandardEasing),
        ) +
            slideOutVertically(
                targetOffsetY = { (it * 0.025f).toInt() },
                animationSpec = tween(180, easing = StandardEasing),
            )

    fun contentTransform(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(Content, easing = PremiumEasing),
            ) +
                slideInVertically(
                    initialOffsetY = { (it * 0.014f).toInt() },
                    animationSpec = tween(Content, easing = PremiumEasing),
                ) +
                scaleIn(
                    initialScale = 0.992f,
                    animationSpec = tween(Content, easing = PremiumEasing),
                )
            ).togetherWith(
                fadeOut(
                    animationSpec = tween(150, easing = ExitEasing),
                ) +
                    slideOutVertically(
                        targetOffsetY = { -(it * 0.008f).toInt() },
                        animationSpec = tween(150, easing = ExitEasing),
                    )
            )

    fun artworkBackgroundTransform(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(620, easing = PremiumEasing),
            ) +
                scaleIn(
                    initialScale = 1.025f,
                    animationSpec = tween(620, easing = PremiumEasing),
                )
            ).togetherWith(
                fadeOut(
                    animationSpec = tween(420, easing = ExitEasing),
                )
            )

    fun playerContentTransform(): ContentTransform =
        (
            fadeIn(
                animationSpec = tween(PlayerMorph, easing = PremiumEasing),
            ) +
                slideInVertically(
                    initialOffsetY = { (it * 0.012f).toInt() },
                    animationSpec = tween(PlayerMorph, easing = PremiumEasing),
                ) +
                scaleIn(
                    initialScale = 0.99f,
                    animationSpec = tween(PlayerMorph, easing = PremiumEasing),
                )
            ).togetherWith(
                fadeOut(
                    animationSpec = tween(160, easing = ExitEasing),
                ) +
                    slideOutVertically(
                        targetOffsetY = { -(it * 0.006f).toInt() },
                        animationSpec = tween(160, easing = ExitEasing),
                    )
            )
}

@Composable
fun EchoAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = NivukxMotion.Enter,
        exit = NivukxMotion.Exit,
    ) {
        content()
    }
}

@Composable
fun <S> EchoAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String = "EchoAnimatedContent",
    playerTransition: Boolean = false,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (playerTransition) {
                NivukxMotion.playerContentTransform()
            } else {
                NivukxMotion.contentTransform()
            }
        },
        label = label,
    ) { state ->
        content(state)
    }
}

/**
 * Premium title identity motion. The title remains readable throughout the transition.
 */
@Composable
fun Modifier.echoTitleMotion(
    visible: Boolean,
    emphasis: Float = 1f,
): Modifier {
    val visibility = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = NivukxMotion.Standard,
            easing = NivukxMotion.StandardEasing,
        ),
        label = "echoTitleVisibility",
    )
    val scale = animateFloatAsState(
        targetValue = if (visible) 1f else 0.965f,
        animationSpec = NivukxMotion.HighEndSpring,
        label = "echoTitleScale",
    )

    return graphicsLayer {
        val progress = visibility.value
        alpha = progress
        translationX = (1f - progress) * -12f * emphasis
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Lightweight press treatment. Uses graphicsLayer so it does not trigger layout work.
 */
@Composable
fun Modifier.echoPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NivukxMotion.HighEndSpring,
        label = "echoPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}


/**
 * High-end surface reveal used for screen and content entrances.
 *
 * The entire transform stays in graphicsLayer so the animation does not cause
 * repeated layout passes while it is running.
 */
@Composable
fun Modifier.nivukxHighEndReveal(
    offsetY: Float = 14f,
    initialScale: Float = 0.985f,
): Modifier {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = NivukxMotion.PremiumScreen,
                easing = NivukxMotion.PremiumEasing,
            ),
        )
    }

    return graphicsLayer {
        val p = progress.value
        alpha = 0.82f + (0.18f * p)
        translationY = offsetY * (1f - p)
        scaleX = initialScale + ((1f - initialScale) * p)
        scaleY = initialScale + ((1f - initialScale) * p)
    }
}

/**
 * High-end press depth for controls and floating surfaces.
 * It combines scale, depth, translation and a tiny rotational response.
 */
@Composable
fun Modifier.nivukxPressDepth(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.972f,
    pressedTranslationY: Float = 1.5f,
    pressedRotationZ: Float = 0.35f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = NivukxMotion.MagneticSpring,
        label = "nivukxPressScale",
    )
    val translationY by animateFloatAsState(
        targetValue = if (pressed) pressedTranslationY else 0f,
        animationSpec = NivukxMotion.MagneticSpring,
        label = "nivukxPressTranslation",
    )
    val rotationZ by animateFloatAsState(
        targetValue = if (pressed) pressedRotationZ else 0f,
        animationSpec = NivukxMotion.MagneticSpring,
        label = "nivukxPressRotation",
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.translationY = translationY
        this.rotationZ = rotationZ
    }
}

/**
 * Adds physically coherent depth while a surface is being horizontally swiped.
 * The translation itself remains owned by the caller.
 */
fun Modifier.nivukxSwipeDepth(
    horizontalOffset: Float,
    maxOffset: Float = 220f,
): Modifier = graphicsLayer {
    val progress = (kotlin.math.abs(horizontalOffset) / maxOffset).coerceIn(0f, 1f)
    val direction = if (horizontalOffset >= 0f) 1f else -1f
    val depth = progress * progress
    scaleX = 1f + (0.012f * depth)
    scaleY = 1f + (0.006f * depth)
    rotationZ = direction * 1.1f * depth
    alpha = 1f - (0.04f * depth)
}

/**
 * High-end list/grid item entrance with a restrained spatial settle.
 * This is intentionally short and low-amplitude so dense music libraries remain
 * responsive instead of turning every scroll into a fireworks display.
 */
@Composable
fun Modifier.nivukxItemReveal(
    offsetY: Float = 8f,
    initialScale: Float = 0.992f,
): Modifier {
    // A transition state avoids launching one coroutine per list item. The visual
    // result stays identical, but dense libraries no longer create an animation job
    // for every composed row/card.
    val visibilityState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val transition = updateTransition(
        targetState = visibilityState,
        label = "nivukxItemRevealTransition",
    )
    val progress = transition.animateFloat(
        transitionSpec = { tween(NivukxMotion.Standard, easing = NivukxMotion.PremiumEasing) },
        label = "nivukxItemRevealProgress",
    ) { state ->
        if (state) 1f else 0f
    }.value

    return graphicsLayer {
        alpha = 0.9f + (0.1f * progress)
        translationY = offsetY * (1f - progress)
        scaleX = initialScale + ((1f - initialScale) * progress)
        scaleY = initialScale + ((1f - initialScale) * progress)
    }
}

/**
 * State-driven icon morph treatment. Keeps the icon node stable while the
 * surrounding UI responds to playback state changes.
 */
@Composable
fun Modifier.nivukxStateMorph(
    active: Boolean,
    activeScale: Float = 1.06f,
    activeRotation: Float = 0f,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (active) activeScale else 1f,
        animationSpec = NivukxMotion.PrecisionSpring,
        label = "nivukxStateMorphScale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (active) activeRotation else 0f,
        animationSpec = NivukxMotion.PrecisionSpring,
        label = "nivukxStateMorphRotation",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationZ = rotation
    }
}

/**
 * Gesture-following depth that can be driven directly from a drag fraction.
 * No coroutine is launched per frame.
 */
fun Modifier.nivukxGestureDepth(
    progress: Float,
    maxScale: Float = 1.018f,
    maxRotation: Float = 1.2f,
): Modifier = graphicsLayer {
    val p = progress.coerceIn(-1f, 1f)
    val magnitude = kotlin.math.abs(p)
    val direction = if (p >= 0f) 1f else -1f
    val depth = magnitude * magnitude
    scaleX = 1f + ((maxScale - 1f) * depth)
    scaleY = 1f + ((maxScale - 1f) * depth * 0.65f)
    rotationZ = direction * maxRotation * depth
}

/**
 * Coordinated fade/scale for loading-to-content and empty/error state changes.
 */
fun stateContentTransform(): ContentTransform =
    (
        fadeIn(tween(260, easing = NivukxMotion.PremiumEasing)) +
            scaleIn(
                initialScale = 0.988f,
                animationSpec = tween(260, easing = NivukxMotion.PremiumEasing),
            )
    ).togetherWith(
        fadeOut(tween(140, easing = NivukxMotion.ExitEasing)) +
            scaleOut(
                targetScale = 0.996f,
                animationSpec = tween(140, easing = NivukxMotion.ExitEasing),
            )
    )

/**
 * Premium artwork identity transition for track changes.
 * A very small 3D rotation and scale shift makes consecutive artwork frames
 * read as one continuous morph rather than a hard swap.
 */
@Composable
fun Modifier.nivukxArtworkTransition(
    identity: Any?,
    intensity: Float = 1f,
): Modifier {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(identity) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 420,
                easing = NivukxMotion.PremiumEasing,
            ),
        )
    }

    return graphicsLayer {
        val p = progress.value
        val settle = 1f - p
        alpha = 0.82f + (0.18f * p)
        scaleX = 0.965f + (0.035f * p)
        scaleY = 0.965f + (0.035f * p)
        rotationY = -1.7f * settle * intensity
        rotationZ = 0.25f * settle * intensity
    }
}

/**
 * State-reactive ambient glow that follows the selected theme colors.
 */
@Composable
fun Modifier.nivukxReactiveGlow(
    active: Boolean,
    cornerRadius: Dp = 28.dp,
    alpha: Float = 0.11f,
): Modifier {
    val glowColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        } else {
            Color.Transparent
        },
        animationSpec = tween(
            durationMillis = NivukxMotion.Emphasis,
            easing = NivukxMotion.PremiumEasing,
        ),
        label = "nivukxReactiveGlow",
    )

    return drawBehind {
        if (glowColor.alpha > 0f) {
            drawRoundRect(
                color = glowColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    cornerRadius.toPx(),
                    cornerRadius.toPx(),
                ),
            )
        }
    }
}

/**
 * Lightweight magnetic focus motion for search fields and active destinations.
 */
@Composable
fun Modifier.nivukxFocusMotion(active: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.008f else 1f,
        animationSpec = NivukxMotion.FluidSpring,
        label = "nivukxFocusScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.985f,
        animationSpec = tween(
            durationMillis = NivukxMotion.MicroSettle,
            easing = NivukxMotion.PremiumEasing,
        ),
        label = "nivukxFocusAlpha",
    )

    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

@Composable
fun Modifier.nivukxThemeReactiveGlow(active: Boolean): Modifier =
    nivukxReactiveGlow(active = active)

@Composable
fun Modifier.nivukxSearchFocusMotion(active: Boolean): Modifier =
    nivukxFocusMotion(active = active)
