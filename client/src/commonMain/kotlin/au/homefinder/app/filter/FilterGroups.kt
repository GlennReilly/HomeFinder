package au.homefinder.app.filter

/** A named set of filter criteria that folds open and shut on the Filters tab (ADR 0066). [openByDefault] says whether it starts open. */
data class FilterGroup(val title: String, val criteria: List<FilterCriterion>, val openByDefault: Boolean = false)

/** A group as drawn: [open] already takes switched-on criteria into account. */
data class ArrangedFilterGroup(val title: String, val criteria: List<FilterCriterion>, val open: Boolean) {
    /** The element id of the group's `details`. */
    val id: String get() = FilterGroups.idOf(title)

    /** The element id of the "N on" text in the group's heading. */
    val countId: String get() = "$id-count"
}

/** The groups of the Filters tab, mirroring the card row groups (ADR 0064). A criterion no group lists lands in a final "More" group. */
object FilterGroups {
    const val OTHER_TITLE: String = "More"

    val DEFAULT: List<FilterGroup> = listOf(
        FilterGroup("People and area", listOf(FilterCriterion.SEIFA, FilterCriterion.RENTED), openByDefault = true),
        FilterGroup("Crime", listOf(FilterCriterion.VIOLENT, FilterCriterion.PROPERTY)),
        FilterGroup("Location", listOf(FilterCriterion.BEACH, FilterCriterion.PARKS)),
        FilterGroup("Schools", listOf(FilterCriterion.ICSEA, FilterCriterion.ZONED)),
    )

    fun idOf(title: String): String = "filter-group-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * The groups to draw. A group with a criterion in [on] starts open, so a switched-on filter is never hidden; otherwise it opens only if
     * [FilterGroup.openByDefault]. A criterion [groups] does not list goes to a final "More" group; a group with no criteria is left out.
     */
    fun arrange(on: Set<FilterCriterion> = emptySet(), groups: List<FilterGroup> = DEFAULT): List<ArrangedFilterGroup> {
        val listed = groups.flatMap { it.criteria }.toSet()
        val unlisted = FilterCriterion.entries.filter { it !in listed }
        val all = groups + if (unlisted.isEmpty()) emptyList() else listOf(FilterGroup(OTHER_TITLE, unlisted))
        return all.filter { it.criteria.isNotEmpty() }
            .map { ArrangedFilterGroup(it.title, it.criteria, it.openByDefault || it.criteria.any { c -> c in on }) }
    }

    /** The text beside a group's title: how many of its criteria are switched on ("2 on"), or empty when none are. */
    fun onText(group: ArrangedFilterGroup, on: Set<FilterCriterion>): String =
        group.criteria.count { it in on }.let { if (it == 0) "" else "$it on" }
}
