package au.homefinder.app.selection

/** A named set of card rows that folds open and shut (ADR 0064). [labels] are row labels; [openByDefault] says whether it starts open. */
data class CardGroup(val title: String, val labels: List<String>, val openByDefault: Boolean = false)

/** A group with its rows in the layout's order, hidden ones included. */
data class ArrangedGroup(val title: String, val rows: List<ArrangedRow>, val openByDefault: Boolean)

/** The groups of each card. A row no group lists (one added by a later release) lands in a final "More" group, so nothing is lost. */
object CardGroups {
    const val OTHER_TITLE: String = "More"

    val SUBURB: List<CardGroup> = listOf(
        CardGroup(
            "Prices and blocks",
            listOf(SuburbCardRows.MEDIAN_HOUSE_LABEL, SuburbCardRows.MEDIAN_UNIT_LABEL, SuburbCardRows.PRICE_CHANGE_LABEL, "Typical block size"),
            openByDefault = true,
        ),
        CardGroup(
            "People and area",
            listOf("Socio-economic advantage", "Disadvantage (SEIFA)", "Economic resources (SEIFA)", "Education and occupation (SEIFA)", "Homes rented", "Houses and units"),
        ),
        CardGroup("Crime", listOf("Violent crime", "Break-ins, car and home theft")),
        CardGroup("Location", listOf("Distance to the beach", "Parks", "Shopping", "Transport")),
        CardGroup("Schools", listOf(SuburbCardRows.SCHOOLS_LABEL)),
    )

    val LOT: List<CardGroup> = listOf(
        CardGroup(
            "The block",
            listOf(LotCardRows.BLOCK_SIZE_LABEL, "Backyard aspect", "Slope", "Height above sea level", "Height above drainage"),
            openByDefault = true,
        ),
        CardGroup("Nearby", listOf("Distance to the beach", SuburbCardRows.SCHOOLS_LABEL, "Parks", "Shopping", "Transport")),
    )
}
