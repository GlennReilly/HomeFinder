package au.homefinder.app.selection

/** A row with whether the layout hides it. */
data class ArrangedRow(val row: CardRow, val hidden: Boolean)

/**
 * Which rows a card shows and in what order (ADR 0039), kept by row label. Rows the layout has never heard of (a row added by a
 * later release) follow the ones it has, in their default order; labels it knows that no longer exist are ignored.
 */
data class CardLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val groupOrder: List<String> = emptyList(),
) {
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

    /**
     * The rows in their groups (ADR 0064): groups in the layout's group order (unknown ones follow in default order), rows inside a group in the
     * layout's row order. Groups with no rows are left out.
     */
    fun arrangeGroups(rows: List<CardRow>, groups: List<CardGroup>): List<ArrangedGroup> {
        val arranged = arrange(rows)
        val listed = groups.flatMap { it.labels }.toSet()
        val built = (groups + CardGroup(CardGroups.OTHER_TITLE, emptyList())).map { group ->
            val members = arranged.filter { if (group.title == CardGroups.OTHER_TITLE) it.row.label !in listed else it.row.label in group.labels }
            ArrangedGroup(group.title, members, group.openByDefault)
        }.filter { it.rows.isNotEmpty() }
        val known = groupOrder.distinct().mapNotNull { title -> built.firstOrNull { it.title == title } }
        return known + built.filter { group -> known.none { it.title == group.title } }
    }

    /** Moves a row one place within its own group; a row never leaves its group. */
    fun moveRowInGroup(label: String, rows: List<CardRow>, groups: List<CardGroup>, step: Int): CardLayout {
        val arranged = arrangeGroups(rows, groups)
        val group = arranged.firstOrNull { g -> g.rows.any { it.row.label == label } } ?: return this
        val labels = group.rows.map { it.row.label }.toMutableList()
        val from = labels.indexOf(label)
        val to = from + step
        if (to !in labels.indices) return this
        labels[from] = labels[to].also { labels[to] = labels[from] }
        val order = arranged.flatMap { g -> if (g === group) labels else g.rows.map { it.row.label } }
        return copy(order = order)
    }

    /** Moves a whole group one place up or down. */
    fun moveGroup(title: String, rows: List<CardRow>, groups: List<CardGroup>, step: Int): CardLayout {
        val titles = arrangeGroups(rows, groups).map { it.title }.toMutableList()
        val from = titles.indexOf(title)
        val to = from + step
        if (from < 0 || to !in titles.indices) return this
        titles[from] = titles[to].also { titles[to] = titles[from] }
        return copy(groupOrder = titles)
    }

    val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty() && groupOrder.isEmpty()

    /**
     * One line per label: "+label" for a place in the order, "!label" for a hidden row (which keeps its place, if it has one), "&group" for a place in the
     * group order (ADR 0064). The
     * order lines come first, so decoding gives the same layout back.
     */
    fun encode(): String = (order.distinct().map { "+$it" } + hidden.sorted().map { "!$it" } + groupOrder.distinct().map { "&$it" }).joinToString("\n")

    companion object {
        /** Reads what [encode] wrote; anything missing or unreadable gives the default layout. */
        fun decode(text: String?): CardLayout {
            val entries = text.orEmpty().lines().filter { it.length > 1 && (it[0] == '+' || it[0] == '!' || it[0] == '&') }
            return CardLayout(
                order = entries.filter { it[0] == '+' }.map { it.substring(1) },
                hidden = entries.filter { it[0] == '!' }.map { it.substring(1) }.toSet(),
                groupOrder = entries.filter { it[0] == '&' }.map { it.substring(1) },
            )
        }
    }
}
