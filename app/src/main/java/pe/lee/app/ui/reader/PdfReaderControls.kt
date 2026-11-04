package pe.lee.app.ui.reader

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.app.R

@Composable
internal fun PdfReaderTopBar(
    visible: Boolean,
    title: String,
    loading: Boolean,
    pageCount: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onAnnotations: () -> Unit,
    onHideControls: () -> Unit,
) {
    if (!visible) return
    Surface(color = Color(0xFF0A1423)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.pdf_back), tint = Color.White)
            }
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFF75DFFF), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSearch, enabled = !loading && pageCount > 0) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.pdf_search_title), tint = Color.White)
            }
            IconButton(onClick = onAnnotations, enabled = !loading && pageCount > 0) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.pdf_annotations_content_description),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onHideControls, enabled = !loading) {
                Icon(Icons.Default.VisibilityOff, contentDescription = stringResource(R.string.pdf_hide_controls), tint = Color.White)
            }
        }
    }
}

@Composable
internal fun PdfReaderBottomControls(
    visible: Boolean,
    pageCount: Int,
    pageIndex: Int,
    continuousScroll: Boolean,
    selectionMode: Boolean,
    sliderPage: Float,
    zoomScale: Float,
    onSelectionModeToggle: () -> Unit,
    onContinuousScrollChange: (Boolean) -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomIn: () -> Unit,
    onRotate: () -> Unit,
    onPrevious: () -> Unit,
    onRequestPage: () -> Unit,
    onNext: () -> Unit,
) {
    if (!visible) return
    Surface(color = Color(0xFF0A1423), shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pdf_continuous_scroll), color = Color.White, fontSize = 14.sp)
                    if (Build.VERSION.SDK_INT >= 35 && !continuousScroll) {
                        TextButton(onClick = onSelectionModeToggle) {
                            Text(
                                if (selectionMode) {
                                    stringResource(R.string.pdf_selection_tap_or_drag)
                                } else {
                                    stringResource(R.string.pdf_selection_select)
                                },
                            )
                        }
                    }
                }
                Switch(checked = continuousScroll, onCheckedChange = onContinuousScrollChange)
            }
            if (pageCount > 1) {
                Slider(
                    value = sliderPage,
                    onValueChange = onSliderChange,
                    onValueChangeFinished = onSliderFinished,
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!continuousScroll) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onZoomOut, enabled = zoomScale > 1f) {
                        Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.pdf_zoom_out), tint = Color.White)
                    }
                    TextButton(onClick = onZoomReset) {
                        Text(
                            stringResource(R.string.pdf_zoom_reset, (zoomScale * 100).toInt()),
                            color = Color.White,
                        )
                    }
                    IconButton(onClick = onZoomIn, enabled = zoomScale < 3f) {
                        Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.pdf_zoom_in), tint = Color.White)
                    }
                    IconButton(onClick = onRotate) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.pdf_rotate_page),
                            tint = Color.White,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = pageIndex > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.pdf_previous_page), tint = Color.White)
                }
                TextButton(onClick = onRequestPage) {
                    Text(
                        if (pageCount > 0) {
                            stringResource(R.string.pdf_page_of, pageIndex + 1, pageCount)
                        } else {
                            stringResource(R.string.pdf_preparing_pdf)
                        },
                        color = Color.White,
                    )
                }
                IconButton(onClick = onNext, enabled = pageIndex < pageCount - 1) {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.pdf_next_page), tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun PdfReaderRestoreControls(visible: Boolean, onShowControls: () -> Unit, modifier: Modifier = Modifier) {
    if (visible) return
    Surface(
        modifier = modifier.padding(12.dp),
        color = Color(0xE60A1423),
        shape = RoundedCornerShape(14.dp),
    ) {
        IconButton(onClick = onShowControls) {
            Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.pdf_show_controls), tint = Color.White)
        }
    }
}
