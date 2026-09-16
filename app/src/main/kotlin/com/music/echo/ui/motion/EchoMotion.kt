package echo.music.iad1tya.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Echo-Music motion system. Motion is deliberately short and low-amplitude so it
 * matches the existing Material/glass UI instead of introducing a new visual style.
 * Visual-only state is animated in the draw phase where possible.
 */
object EchoMotion {
    const val Micro = 120
    const val Standard = 220
    const val Emphasis = 320
    const val Content = 240

    val StandardEasing = FastOutSlowInEasing
    val StandardTween = tween<Float>(Standard, easing = StandardEasing)
    val ContentTween = tween<Float>(Content, easing = StandardEasing)
    val SoftSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val Enter: EnterTransition =
        fadeIn(tween(Standard, easing = StandardEasing)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(Standard, easing = StandardEasing))

    val Exit: ExitTransition =
        fadeOut(tween(170, easing = StandardEasing)) +
            scaleOut(targetScale = 0.99f, animationSpec = tween(170, easing = StandardEasing))

    val SheetEnter: EnterTransition =
        fadeIn(tween(180, easing = StandardEasing)) +
            slideInVertically(
                initialOffsetY = { (it * 0.035f).toInt() },
                animationSpec = tween(Standard, easing = StandardEasing),
            )

    val SheetExit: ExitTransition =
        fadeOut(tween(160, easing = StandardEasing)) +
            slideOutVertically(
                targetOffsetY = { (it * 0.025f).toInt() },
                animationSpec = tween(180, easing = StandardEasing),
            )

    fun contentTransform(): ContentTransform =
        fadeIn(tween(Content, easing = StandardEasing))
            .togetherWith(fadeOut(tween(150, easing = StandardEasing)))
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
        enter = EchoMotion.Enter,
        exit = EchoMotion.Exit,
    ) {
        content()
    }
}

@Composable
fun <S> EchoAnimatedContent(
    targetState: S,
    modifier: Modifier = Modifier,
    label: String = "EchoAnimatedContent",
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = { EchoMotion.contentTransform() },
        label = label,
    ) { state ->
        content(state)
    }
}

/**
 * Small tactile press treatment. It only changes scale in graphicsLayer, avoiding
 * layout/recomposition work while the finger is down.
 */
@Composable
fun Modifier.echoPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = EchoMotion.SoftSpring,
        label = "echoPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
