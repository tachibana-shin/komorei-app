package git.shin.komorei.ui.screens.sources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import git.shin.komorei.R
import java.util.Locale

/**
 * "Đa ngôn ngữ" for a multi-language source, otherwise the localized display
 * name of the (single) language tag (falls back to the raw tag).
 */
@Composable
fun sourceLanguageLabel(languages: List<String>): String {
    if (languages.isEmpty()) return ""
    if (languages.size > 1) return stringResource(R.string.sources_language_multi)
    val code = languages.first()
    return remember(code) { Locale.forLanguageTag(code).displayLanguage }.ifBlank { code }
}

/** Subtitle line for a source row: "v1.0.0 · Tiếng Việt". */
@Composable
fun sourceVersionSubtitle(
    version: String,
    languages: List<String>,
): String =
    buildString {
        append(stringResource(R.string.sources_version_format, version))
        if (languages.isNotEmpty()) {
            append(" · ")
            append(sourceLanguageLabel(languages))
        }
    }
