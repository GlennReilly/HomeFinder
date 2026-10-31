package au.homefinder.app.selection

/** A row with whether the layout hides it. */
data class ArrangedRow(val row: CardRow, val hidden: Boolean)

/**
 * Which rows a card shows and in what order (ADR 0039), kept by row label. Rows the layout has never heard of (a row added by a
 * later release) follow the ones it has, in their default order; labels it knows that no longer exist are ignored.
 */
data class CardLayout(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    /** Every row in the layout's order, hidden ones included (the customise view shows them dimmed). */
    fun arrange(rows: List<CardRow>): List<ArrangedRow> {
        val byLabel = rows.associateBy { it.label }
        val known = order.distinct().mapNotNull { byLabel[it] }
        val knownLabels = known.map { it.label }.toSet()
        return (known + rows.filter { it.label !in knownLabels }).map { ArrangedRow(it, it.label in hidden) }
    }

    /** What the card shows normally: the rows that are not hidden, in order. */
    fun visible(rows: List<CardRow>): List<CardRow> = arrange(rows).filterNot { it.hidden }.map { it.row }

    fun toggle(label: String): CardLayout = copy(hidden = if (label in hidden) hidden - label else hidden + label)

    fun moveUp(label: String, rows: List<CardRow>): CardLayout = move(label, rows, -1)

    fun moveDown(label: String, rows: List<CardRow>): CardLayout = move(label, rows, 1)

    private fun move(label: String, rows: List<CardRow>, step: Int): CardLayout {
        val labels = arrange(rows).map { it.row.label }.toMutableList()
        val from = labels.indexOf(label)
        val to = from + step
        if (from < 0 || to !in labels.indices) return this
        labels[from] = labels[to].also { labels[to] = labels[from] }
        return copy(order = labels)
    }

    val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty()

    /**
     * One line per label: "+label" for a place in the order, "!label" for a hidden row (which keeps its place, if it has one). The
     * order lines come first, so decoding gives the same layout back.
     */
    fun encode(): String = (order.distinct().map { "+$it" } + hidden.sorted().map { "!$it" }).joinToString("\n")

    companion object {
        /** Reads what [encode] wrote; anything missing or unreadable gives the default layout. */
        fun decode(text: String?): CardLayout {
            val entries = text.orEmpty().lines().filter { it.length > 1 && (it[0] == '+' || it[0] == '!') }
            return CardLayout(
                order = entries.filter { it[0] == '+' }.map { it.substring(1) },
                hidden = entries.filter { it[0] == '!' }.map { it.substring(1) }.toSet(),
            )
        }
    }
}
