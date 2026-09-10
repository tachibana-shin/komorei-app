package git.shin.komorei.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

object AppIcons {
    fun getSourceIcon(sourceId: String): ImageVector {
        return when (sourceId) {
            "all" -> Icons.Default.Public
            "animevietsub" -> Icons.Default.Bolt
            "vuighe" -> Icons.Default.LocalFireDepartment
            "gogoanime" -> Icons.Default.Language
            "hidive" -> Icons.Default.Diamond
            else -> Icons.Default.Movie
        }
    }

    fun getGenreIcon(genreId: String): ImageVector {
        return when (genreId) {
            "action" -> Icons.Default.FlashOn
            "isekai" -> Icons.Default.AutoAwesome
            "romance" -> Icons.Default.Favorite
            "comedy" -> Icons.Default.SentimentVerySatisfied
            "fantasy" -> Icons.Default.Shield
            "sci-fi" -> Icons.Default.RocketLaunch
            "mystery" -> Icons.Default.Psychology
            "school" -> Icons.Default.School
            else -> Icons.Default.Movie
        }
    }

    fun getCategoryIcon(category: String): ImageVector {
        return when {
            category.contains("Thịnh hành", ignoreCase = true) || category.contains(
                "Trending",
                ignoreCase = true
            ) -> Icons.Default.LocalFireDepartment

            category.contains("Mới", ignoreCase = true) || category.contains(
                "New",
                ignoreCase = true
            ) -> Icons.Default.AutoAwesome

            category.contains("Hành động", ignoreCase = true) || category.contains(
                "Action",
                ignoreCase = true
            ) -> Icons.Default.FlashOn

            else -> Icons.Default.Movie
        }
    }
}
