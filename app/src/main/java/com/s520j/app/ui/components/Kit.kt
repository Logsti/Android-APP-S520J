package com.s520j.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.s520j.app.data.model.Visibility
import com.s520j.app.ui.theme.CardCorner
import com.s520j.app.ui.theme.Dimens
import com.s520j.app.ui.theme.S520JColors

@Composable
fun PageContainer(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(S520JColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.PageMaxWidth)
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = Dimens.PageHorizontalPadding),
            content = content
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = S520JColors.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardCorner,
        color = containerColor,
        border = BorderStroke(1.dp, S520JColors.Divider.copy(alpha = 0.6f)),
        shadowElevation = 1.dp,
        onClick = { onClick?.invoke() }
    ) {
        Column(modifier = Modifier.padding(Dimens.CardPadding), content = content)
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = S520JColors.TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = S520JColors.TextSecondary
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = S520JColors.Primary)
            }
        }
    }
}

@Composable
fun VisibilityChip(visibility: Visibility, modifier: Modifier = Modifier) {
    val isPrivate = visibility == Visibility.PRIVATE
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (isPrivate) S520JColors.Sand else S520JColors.PrimarySoft
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPrivate) Icons.Filled.Lock else Icons.Filled.People,
                contentDescription = null,
                tint = S520JColors.TextSecondary,
                modifier = Modifier.height(14.dp)
            )
            Text(
                text = visibility.shortLabel,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = S520JColors.TextSecondary
            )
        }
    }
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = S520JColors.Background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = S520JColors.TextSecondary
        )
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) { Text(actionLabel, color = S520JColors.Primary) }
        }
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = S520JColors.Primary,
            contentColor = Color.White
        )
    ) {
        Text(text)
    }
}

@Composable
fun SoftTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = S520JColors.Primary,
            unfocusedBorderColor = S520JColors.Divider,
            focusedLabelColor = S520JColors.Primary,
            unfocusedLabelColor = S520JColors.TextSecondary,
            focusedContainerColor = S520JColors.Surface,
            unfocusedContainerColor = S520JColors.Surface,
            cursorColor = S520JColors.Primary
        )
    )
}

@Composable
fun BackBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = S520JColors.TextPrimary)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = S520JColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        action?.invoke()
    }
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextSecondary
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = S520JColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
