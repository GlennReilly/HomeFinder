package au.homefinder.app.ui

import au.homefinder.app.selection.LotCardRows
import au.homefinder.app.selection.SuburbCardRows
import au.homefinder.shared.model.LotReport
import au.homefinder.shared.model.SuburbReport
import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.dd
import kotlinx.html.dt
import kotlinx.html.div
import kotlinx.html.p

/** A line of links out to the listing sites under a card row: what they lead to, and where (ADR 0061). */
data class ListingBlock(val heading: String, val links: List<ExternalLinks.ListingLink>)

object ListingLinks {
    const val HOUSES_HEADING: String = "Houses for sale"
    const val UNITS_HEADING: String = "Units for sale"
    const val ADDRESS_HEADING: String = "This address"

    /** The links that belong under the suburb card row with this label, or null when the row has none or there is not enough to build them. */
    fun forSuburbRow(label: String, report: SuburbReport): ListingBlock? = when (label) {
        SuburbCardRows.MEDIAN_HOUSE_LABEL -> block(HOUSES_HEADING, ExternalLinks.forSaleLinks(ExternalLinks.ListingKind.HOUSES, report.name, report.postcode))
        SuburbCardRows.MEDIAN_UNIT_LABEL -> block(UNITS_HEADING, ExternalLinks.forSaleLinks(ExternalLinks.ListingKind.UNITS, report.name, report.postcode))
        else -> null
    }

    /** The links under the lot card's block size row: the lot's first address on each site. */
    fun forLotRow(label: String, report: LotReport): ListingBlock? =
        if (label == LotCardRows.BLOCK_SIZE_LABEL) block(ADDRESS_HEADING, ExternalLinks.addressLinks(report.addresses.firstOrNull(), report.suburbName, report.postcode)) else null

    private fun block(heading: String, links: List<ExternalLinks.ListingLink>): ListingBlock? = if (links.isEmpty()) null else ListingBlock(heading, links)

    /** "Hamilton houses on realestate.com.au" read aloud; the visible text is just the site name. */
    fun title(block: ListingBlock, link: ExternalLinks.ListingLink): String = "${block.heading} on ${link.site} (opens in a new tab)"
}

/** The links as a line "Houses for sale: realestate.com.au · domain.com.au", each opening in a new tab without handing over the page. */
internal fun FlowContent.listingLinksLine(block: ListingBlock) {
    p(ListingLinkClasses.LINKS) {
        +"${block.heading}: "
        block.links.forEachIndexed { i, link ->
            if (i > 0) +" · "
            externalLink(link.url, link.site, ListingLinks.title(block, link))
        }
    }
}

/** The same links as a row of their own, used where the price or block row itself is left off (no value) but the links are still good. */
internal fun DL.listingLinksRow(block: ListingBlock) {
    val list = this
    div(ListingLinkClasses.ROW) {
        list.dt { +block.heading }
        list.dd(ListingLinkClasses.VALUE) {
            block.links.forEachIndexed { i, link ->
                if (i > 0) +" · "
                externalLink(link.url, link.site, ListingLinks.title(block, link))
            }
        }
    }
}
