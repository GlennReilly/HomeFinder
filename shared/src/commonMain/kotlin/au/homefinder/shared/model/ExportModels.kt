package au.homefinder.shared.model

import kotlinx.serialization.Serializable

/**
 * What the page sends to make the PDF of the suburbs its filters shade (ADR 0080): the matching suburbs' ids, the switched-on filters as the lines the
 * panel shows ("Homes rented: At most 45%"), and the Suburb Card layout kept in this browser (as `CardLayout.encode()` writes it; empty is the default
 * order), so the PDF lists the rows in your card's order and groups.
 */
@Serializable
data class SuburbExportRequest(
    val suburbIds: List<String>,
    val filters: List<String> = emptyList(),
    val cardLayout: String = "",
)
