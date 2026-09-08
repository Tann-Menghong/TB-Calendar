package com.khmercalendar.domain

import com.khmercalendar.data.prefs.DashboardCard

/**
 * A ready-made dashboard.
 *
 * Arranging eleven modules from scratch is a chore nobody asked for, and the reorder screen
 * that existed before this assumed everyone would do it. A preset is one tap that produces a
 * dashboard someone would plausibly have built, and it is a starting point rather than a mode
 * - every preset leaves the order and the visibility fully editable afterwards.
 *
 * The cards a preset does not name are hidden rather than dropped: the enum is the source of
 * truth for what exists, and a module that vanished from the saved order would be silently
 * unrecoverable.
 */
enum class DashboardPreset(
    val key: String,
    val labelKm: String,
    val descriptionKm: String,
    val cards: List<DashboardCard>,
) {
    ESSENTIAL(
        key = "essential",
        labelKm = "សាមញ្ញ",
        descriptionKm = "ថ្ងៃនេះ ការងារ និងកាលវិភាគ",
        cards = listOf(
            DashboardCard.TODAY,
            DashboardCard.WORK,
            DashboardCard.STATS,
            DashboardCard.TIMELINE,
        ),
    ),
    WORK(
        key = "work",
        labelKm = "ផ្តោតលើការងារ",
        descriptionKm = "ស្ថានភាពការងារ កិច្ចការ និងកំណត់ចំណាំ",
        cards = listOf(
            DashboardCard.WORK,
            DashboardCard.STATS,
            DashboardCard.TIMELINE,
            DashboardCard.TASKS,
            DashboardCard.WEEK,
            DashboardCard.NOTE,
        ),
    ),
    PLANNER(
        key = "planner",
        labelKm = "រៀបចំផែនការ",
        descriptionKm = "សប្តាហ៍ ព្រឹត្តិការណ៍ខាងមុខ និងបុណ្យជាតិ",
        cards = listOf(
            DashboardCard.TODAY,
            DashboardCard.WEEK,
            DashboardCard.UPCOMING,
            DashboardCard.HOLIDAYS,
            DashboardCard.COUNTDOWN,
            DashboardCard.LUNAR,
        ),
    ),
    EVERYTHING(
        key = "everything",
        labelKm = "ទាំងអស់",
        descriptionKm = "បង្ហាញគ្រប់ម៉ូឌុល តាមលំដាប់ដើម",
        cards = DashboardCard.entries.filterNot { it == DashboardCard.QUICK_ACTIONS },
    ),
}

/** An order and the set of hidden keys, which only ever change together. */
data class Arrangement(
    val order: List<DashboardCard>,
    val hidden: Set<String>,
)

/**
 * The dashboard's layout, as arithmetic.
 *
 * Pulled out of the settings screen so that reordering can be tested without a device. The
 * drag gesture that calls [move] reports indices from a list that is being redrawn underneath
 * it, so an out-of-range index is a normal event rather than a bug - it returns the list
 * unchanged instead of throwing, and the test says so.
 */
object DashboardArrangement {

    /**
     * The modules a user can actually arrange.
     *
     * [DashboardCard.QUICK_ACTIONS] is retired - it stays in the enum so that a saved order
     * containing it still decodes, and it draws nothing. Counting it would tell a user with a
     * factory layout that they were showing thirteen modules when they can see twelve.
     */
    fun arrangeable(order: List<DashboardCard>): List<DashboardCard> =
        order.filterNot { it == DashboardCard.QUICK_ACTIONS }

    /**
     * Moves the item at [from] to [to], shifting everything between them.
     *
     * A swap would be wrong for a drag: dragging the first item to the fifth position should
     * leave the three in between in their existing order, not exchange two of them.
     */
    fun move(order: List<DashboardCard>, from: Int, to: Int): List<DashboardCard> {
        if (from !in order.indices || to !in order.indices || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Applies a preset: its cards first in its own order, everything else hidden behind them. */
    fun apply(preset: DashboardPreset): Arrangement {
        val rest = DashboardCard.entries.filterNot { it in preset.cards }
        return Arrangement(
            order = preset.cards + rest,
            hidden = rest.map { it.key }.toSet(),
        )
    }

    /**
     * The factory dashboard: every module in its declared order.
     *
     * The same thing [DashboardPreset.EVERYTHING] produces, deliberately - a reset that
     * landed on a layout the preset row then called "custom" would be telling the user their
     * dashboard was still altered.
     */
    fun default(): Arrangement = apply(DashboardPreset.EVERYTHING)

    /**
     * Which preset an arrangement corresponds to, if any.
     *
     * Compares the visible cards in order, so a user who has hidden or moved something is
     * told they are on a custom layout rather than being shown a preset that no longer
     * matches what is on their screen.
     */
    fun presetOf(order: List<DashboardCard>, hidden: Set<String>): DashboardPreset? {
        val visible = arrangeable(order).filter { it.key !in hidden }
        return DashboardPreset.entries.firstOrNull { it.cards == visible }
    }
}
