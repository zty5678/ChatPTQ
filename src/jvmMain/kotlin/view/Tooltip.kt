package example.imageviewer.view

import androidx.compose.foundation.BoxWithTooltip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tooltip(
    text: String = "Tooltip",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipArea(
        modifier = modifier,
        tooltip = {
            Surface(
                color = Color(210, 210, 210),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.caption
                )
            }

        }
    ) {
        content()
    }
}
