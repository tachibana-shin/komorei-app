package git.shin.komorei.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.CardBorderDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

/**
 * A thin "pill" text input (40dp tall, 20dp radius) for compact filter and
 * form fields. Mirrors the Sources-tab pill recipe: rounded CardDark box with
 * a hairline border plus a placeholder, leading icon and trailing clear
 * button. Uses [BasicTextField] so the pill stays thin — M3's OutlinedTextField
 * hard-pins a 56dp min height.
 *
 * @param interactionSource optional external interaction source (e.g. to
 *   share focus state); when null an internal one is used so the border
 *   still tints [AnimeRed] on focus.
 * @param focusRequester optional [FocusRequester] forwarded onto the inner
 *   [BasicTextField] so callers can autofocus the field (e.g. a search input
 *   that grabs focus on first appearance).
 */
@Composable
fun CompactInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    showClear: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    cursorColor: Color = AnimeRed,
    interactionSource: MutableInteractionSource? = null,
    focusRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    val focusSource = interactionSource ?: remember { MutableInteractionSource() }
    val focused by focusSource.collectIsFocusedAsState()
    val borderColor = when {
        isError -> AnimeRed
        focused -> AnimeRed
        else -> CardBorderDark
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(CardDark)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = if (focusRequester != null) {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .focusRequester(focusRequester)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            },
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(cursorColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = focusSource,
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && hint != null) {
                            Text(
                                text = hint,
                                color = TextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (showClear && value.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier
                                // TV focus highlight (no-op on phones). The pill itself
                                // already signals focus via its red border; this only
                                // covers the clear button as a separate D-pad target.
                                .tvFocus(shape = RoundedCornerShape(10.dp), scale = 1.15f)
                                .size(18.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onValueChange("") },
                        )
                    }
                }
            },
        )
    }
}
