package app.lipovka.domain

import app.lipovka.data.Content
import app.lipovka.data.Kind
import app.lipovka.data.St
import app.lipovka.data.Tx
import java.time.Instant
import java.time.ZoneId

/**
 * Days in a row on the round: a day counts when at least one letter was handed over at a gate. Nothing is
 * taken away for a missed day except the run itself; the best run is kept for good. A day still under way
 * doesn't break the run: until its first letter, the run is the one that ended yesterday.
 */
data class Streak(val now: Int, val best: Int, val today: Boolean)

/** What a certificate is given for, and the picture on its medal. */
enum class Emblem { LETTER, ROAD, STAMP, STREAK, PEOPLE, TELEGRAM, GIFT, SEASONS, DAY, DOG, MAP }

/**
 * A certificate of the post: [goal] of something, [have] of it so far. Given once [have] reaches [goal], and
 * never taken back.
 */
data class Honor(val key: String, val emblem: Emblem, val title: Tx, val text: Tx, val have: Long, val goal: Long) {
    val got get() = have >= goal
}

object Honors {
    /** The mail days (as [Game.mailDay]) on which something was handed over to a resident. */
    fun days(s: GameState, zone: ZoneId = ZoneId.systemDefault()): Set<Long> =
        s.mail.asSequence()
            .filter { (it.state == St.DELIVERED || it.state == St.SEEN) && it.deliveredAt > 0 && it.to != "me" }
            .map { Instant.ofEpochMilli(it.deliveredAt).atZone(zone).toLocalDateTime().minusHours(5).toLocalDate().toEpochDay() }
            .toSet()

    fun streak(days: Set<Long>, today: Long): Streak {
        val done = today in days
        var n = 0
        var d = if (done) today else today - 1
        while (d in days) { n++; d-- }
        var best = 0
        var run = 0
        var last = Long.MIN_VALUE
        for (x in days.sorted()) {
            run = if (x == last + 1) run + 1 else 1
            best = maxOf(best, run)
            last = x
        }
        return Streak(n, maxOf(best, n), done)
    }

    fun streak(s: GameState, today: Long): Streak = streak(days(s), today)

    /** Every certificate, given or still to come, in the order they hang in the album. */
    fun all(s: GameState, content: Content, today: Long): List<Honor> {
        val handed = s.mail.filter { (it.state == St.DELIVERED || it.state == St.SEEN) && it.to != "me" }
        val letters = handed.size.toLong()
        val km = s.km.toLong()
        val best = streak(s, today).best.toLong()
        val people = handed.map { it.to }.distinct().count { content.resident(it) != null }.toLong()
        val residents = content.residents.size.toLong().coerceAtLeast(1)
        val telegrams = handed.count { it.kind == Kind.TELEGRAM.name }.toLong()
        val stamps = s.stamps.size.toLong()
        val gifts = s.items.size.toLong()
        val bestDay = (s.days.values.maxOrNull() ?: 0).toLong()
        val zone = ZoneId.systemDefault()
        val seasons = handed.filter { it.deliveredAt > 0 }
            .map { Cal.season(Instant.ofEpochMilli(it.deliveredAt).atZone(zone).monthValue) }.distinct().size.toLong()
        val districts = (1..5).count { s.has("d$it") }.toLong()
        val dog = if (s.has("dog")) 1L else 0L

        fun h(key: String, e: Emblem, ru: String, en: String, tru: String, ten: String, have: Long, goal: Long) =
            Honor(key, e, Tx(ru, en), Tx(tru, ten), have, goal)

        return listOf(
            h("letters1", Emblem.LETTER, "Первое письмо", "The first letter", "Отдать первое письмо у калитки.", "Hand over the first letter at a gate.", letters, 1),
            h("letters50", Emblem.LETTER, "Письмоносец", "Letter carrier", "Разнести 50 писем.", "Deliver 50 letters.", letters, 50),
            h("letters200", Emblem.LETTER, "Почтальон района", "District postman", "Разнести 200 писем.", "Deliver 200 letters.", letters, 200),
            h("letters500", Emblem.LETTER, "Ударник почтовой связи", "Pride of the post", "Разнести 500 писем.", "Deliver 500 letters.", letters, 500),
            h("letters1000", Emblem.LETTER, "Тысяча писем", "A thousand letters", "Разнести 1000 писем.", "Deliver 1000 letters.", letters, 1000),
            h("km10", Emblem.ROAD, "Первые вёрсты", "The first miles", "Пройти 10 км.", "Walk 10 km.", km, 10),
            h("km100", Emblem.ROAD, "Сто километров", "A hundred kilometres", "Пройти 100 км.", "Walk 100 km.", km, 100),
            h("km500", Emblem.ROAD, "Пятьсот километров", "Five hundred kilometres", "Пройти 500 км.", "Walk 500 km.", km, 500),
            h("km1000", Emblem.ROAD, "Тысяча вёрст", "A thousand kilometres", "Пройти 1000 км.", "Walk 1000 km.", km, 1000),
            h("streak7", Emblem.STREAK, "Неделя без выходных", "A week without a break", "7 дней подряд отдавать хоть одно письмо.", "Hand over at least one letter 7 days in a row.", best, 7),
            h("streak30", Emblem.STREAK, "Месяц на маршруте", "A month on the round", "30 дней подряд на маршруте.", "30 days in a row on the round.", best, 30),
            h("streak100", Emblem.STREAK, "Сто дней подряд", "A hundred days running", "100 дней подряд на маршруте.", "100 days in a row on the round.", best, 100),
            h("streak365", Emblem.STREAK, "Год без пропусков", "A year without a gap", "365 дней подряд на маршруте.", "365 days in a row on the round.", best, 365),
            h("day10k", Emblem.DAY, "Долгий день", "A long day", "Пройти 10 000 шагов за день.", "Walk 10,000 steps in a day.", bestDay, 10_000),
            h("day20k", Emblem.DAY, "Скороход", "Seven-league boots", "Пройти 20 000 шагов за день.", "Walk 20,000 steps in a day.", bestDay, 20_000),
            h("people", Emblem.PEOPLE, "Знает всех в округе", "Knows everyone", "Побывать у калитки каждого жителя.", "Stop at every resident's gate.", people, residents),
            h("telegrams", Emblem.TELEGRAM, "Срочная доставка", "Express delivery", "Доставить 10 телеграмм.", "Deliver 10 telegrams.", telegrams, 10),
            h("stamps25", Emblem.STAMP, "Филателист", "Philatelist", "Собрать 25 марок.", "Collect 25 stamps.", stamps, 25),
            h("stamps100", Emblem.STAMP, "Знаток марок", "Stamp connoisseur", "Собрать 100 марок.", "Collect 100 stamps.", stamps, 100),
            h("gifts", Emblem.GIFT, "Полная комната", "A full room", "Получить 20 подарков от жителей.", "Get 20 gifts from the residents.", gifts, 20),
            h("seasons", Emblem.SEASONS, "Круглый год", "All year round", "Разносить почту зимой, весной, летом и осенью.", "Carry the post in winter, spring, summer and autumn.", seasons, 4),
            h("dog", Emblem.DOG, "Верный друг", "A faithful friend", "Бублик стал ходить с вами.", "Bublik walks the round with you.", dog, 1),
            h("districts", Emblem.MAP, "Вся округа", "The whole district", "Открыть все пять мест за рекой и лесом.", "Open all five places across the river and the forest.", districts, 5),
        )
    }
}
