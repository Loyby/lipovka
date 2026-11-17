package app.lipovka.ui.art

import app.lipovka.domain.Emblem

/**
 * A certificate's medal, the size of an item icon: a striped ribbon and a brass disc with the emblem of what
 * it was given for. One not given yet hangs as a pale outline, so the empty places on the page can be seen.
 */
object HonorArt {
    fun draw(e: Emblem, got: Boolean): Buf {
        val b = Buf(22, 30)
        val (r1, r2) = RIBBONS[e.ordinal % RIBBONS.size]
        if (got) {
            // the ribbon, folded to a V under the medal
            for (y in 0 until 12) {
                val inset = if (y > 8) y - 8 else 0
                for (x in 5 + inset until 17 - inset) b.p(x, y, if ((x - 5) / 3 % 2 == 0) r1 else r2)
                b.p(4 + inset, y, R.INK); b.p(17 - inset, y, R.INK)
            }
            b.r(4, 0, 14, 1, R.INK)
            b.disc(11, 19, 9, R.INK)
            b.disc(11, 19, 8, rgb(0xa86e22))
            b.disc(11, 19, 7, rgb(0xe8bf58))
            b.disc(11, 19, 5, rgb(0xd9973a))
            // a glint on the brass, top left
            b.p(7, 15, rgb(0xfff0b0)); b.p(8, 14, rgb(0xfff0b0)); b.p(6, 16, rgb(0xf8d070))
            glyph(b, e, rgb(0x5a3410))
        } else {
            val line = rgb(0xa88a68)
            for (y in 0 until 12) {
                val inset = if (y > 8) y - 8 else 0
                b.p(4 + inset, y, line); b.p(17 - inset, y, line)
            }
            b.r(4, 0, 14, 1, line)
            b.disc(11, 19, 9, line)
            b.disc(11, 19, 8, rgb(0xe1cb9b))
            glyph(b, e, rgb(0xc2a872))
        }
        return b
    }

    private fun glyph(b: Buf, e: Emblem, c: Int) {
        val rows = GLYPHS[e] ?: return
        for ((j, row) in rows.withIndex()) for ((i, ch) in row.withIndex()) if (ch == '#') b.p(8 + i, 16 + j, c)
    }

    private val RIBBONS = listOf(
        rgb(0xa33a2c) to rgb(0xf2e2bb), rgb(0x26375e) to rgb(0xe8bf58), rgb(0x3d5e43) to rgb(0xf2e2bb),
        rgb(0x6e1f1f) to rgb(0xd9973a), rgb(0x5a74aa) to rgb(0xfbf3dc),
    )

    /** 7×7 marks stamped into the medal. */
    private val GLYPHS = mapOf(
        Emblem.LETTER to listOf("       ", "#######", "##   ##", "# # # #", "#  #  #", "#######", "       "),
        Emblem.ROAD to listOf("   #   ", "  ###  ", "   #   ", "  # #  ", " #   # ", "#     #", "       "),
        Emblem.STAMP to listOf("# # # #", "       ", "# ### #", "  # #  ", "# ### #", "       ", "# # # #"),
        Emblem.STREAK to listOf("   #   ", "  ##   ", "  ###  ", " ## ## ", " #   # ", " ## ## ", "  ###  "),
        Emblem.PEOPLE to listOf(" #   # ", "### ###", " #   # ", "### ###", "### ###", "# # # #", "       "),
        Emblem.TELEGRAM to listOf("    ## ", "   ##  ", "  ##   ", " ##### ", "   ##  ", "  ##   ", " ##    "),
        Emblem.GIFT to listOf("  # #  ", "   #   ", "#######", "#  #  #", "#######", "#  #  #", "#######"),
        Emblem.SEASONS to listOf("   #   ", " # # # ", "  ###  ", "### ###", "  ###  ", " # # # ", "   #   "),
        Emblem.DAY to listOf("       ", "  ###  ", " #   # ", " # # # ", " #  ## ", " #   # ", "  ###  "),
        Emblem.DOG to listOf("#    # ", "##  ## ", "###### ", "# ## # ", "###### ", " #### #", "  ## # "),
        Emblem.MAP to listOf("#  #  #", "## ## #", "# ## ##", "#  #  #", "## ## #", "# ## ##", "#  #  #"),
    )
}
