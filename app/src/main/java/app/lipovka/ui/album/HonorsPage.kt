package app.lipovka.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lipovka.R
import app.lipovka.domain.Honor
import app.lipovka.domain.Honors
import app.lipovka.ui.Home
import app.lipovka.ui.Words
import app.lipovka.ui.art.HonorArt
import app.lipovka.ui.components.BufImage
import app.lipovka.ui.components.Segments
import app.lipovka.ui.components.pixelBox
import app.lipovka.ui.theme.C
import app.lipovka.ui.theme.T

/** The postman's certificates: the run of days on the round, then every certificate, the given ones first. */
@Composable
fun HonorsPage(home: Home) {
    // The state is changed in place as the steps come, so these are worked out afresh with each look: a page's worth.
    val all = Honors.all(home.s, home.content, home.today)
    val streak = Honors.streak(home.s, home.today)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.honors_title), style = T.Display.copy(color = C.paper))
        Text("${all.count { it.got }} / ${all.size}", style = T.Sub.copy(color = C.lamp))
        Column(Modifier.fillMaxWidth().pixelBox(C.lamp).padding(12.dp)) {
            Text(if (streak.now == 0) stringResource(R.string.streak_none) else pluralStringResource(R.plurals.streak_days, streak.now, streak.now), style = T.Heading)
            Text(
                stringResource(if (streak.today) R.string.streak_today_done else if (streak.now == 0) R.string.streak_start else R.string.streak_today_open) + " " +
                    pluralStringResource(R.plurals.streak_best, streak.best, streak.best),
                style = T.Sub.copy(color = C.ink),
            )
        }
        for (h in all.sortedBy { if (it.got) 0 else 1 }) HonorRow(home, h)
        Text(stringResource(R.string.honors_hint), style = T.Sub.copy(color = C.paper2))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HonorRow(home: Home, h: Honor) {
    Row(Modifier.fillMaxWidth().pixelBox(if (h.got) C.paper else C.paper2).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        BufImage(remember(h.emblem, h.got) { HonorArt.draw(h.emblem, h.got) }, scale = 2.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(h.title(home.ru), style = T.Heading.copy(color = if (h.got) C.redDeep else C.muted))
            Text(h.text(home.ru), style = T.Small)
            if (!h.got) {
                Spacer(Modifier.height(4.dp))
                Segments(h.have.toFloat() / h.goal)
                Text("${Words.num(h.have.toInt())} / ${Words.num(h.goal.toInt())}", style = T.Small, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
