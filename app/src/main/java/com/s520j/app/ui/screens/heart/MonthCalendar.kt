package com.s520j.app.ui.screens.heart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.s520j.app.ui.theme.S520JColors
import com.s520j.app.ui.util.monthTitle
import com.s520j.app.ui.util.today
import com.s520j.app.ui.util.weekdayName
import java.time.LocalDate
import java.time.YearMonth

private val weekHeaders = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 完整月份日历：星期标题、完整日期网格、今日高亮、有事项标记、日期可选、前后月切换、返回今天。
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    eventCounts: Map<LocalDate, Int>,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit
) {
    val todayLocal = today()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = month.atDay(1).monthTitle(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = S520JColors.TextPrimary
            )
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月", tint = S520JColors.TextSecondary)
            }
            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月", tint = S520JColors.TextSecondary)
            }
            TextButton(onClick = onToday) {
                Text("今天", color = S520JColors.Primary, style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth()) {
            weekHeaders.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = S520JColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val first = month.atDay(1)
        val leadingBlanks = first.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7

        for (week in 0 until totalCells / 7) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (weekday in 0 until 7) {
                    val cellIndex = week * 7 + weekday
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            isToday = date == todayLocal,
                            isSelected = date == selectedDate,
                            eventCount = eventCounts[date] ?: 0,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(S520JColors.Primary) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> androidx.compose.ui.graphics.Color.White
                    isToday -> S520JColors.Primary
                    else -> S520JColors.TextPrimary
                }
            )
            if (eventCount > 0) {
                Box(
                    Modifier
                        .size(4.dp)
                        .background(
                            if (isSelected) androidx.compose.ui.graphics.Color.White else S520JColors.Primary,
                            CircleShape
                        )
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
