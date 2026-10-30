package au.homefinder.server

import au.homefinder.shared.api.ApiContract
import au.homefinder.shared.model.GeoBounds
import au.homefinder.shared.model.SearchResult
import au.homefinder.shared.model.SearchResultKind
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Suggests suburbs and addresses for a typed query. A fake stands in for it in route tests. */
interface SearchIndex {
    suspend fun search(query: SearchQuery, limit: Int): List<SearchResult>
}

/**
 * Searches the `suburb` and `address` tables (ADR 0026). Suburbs match by name (the whole name or any word of it) or by
 * postcode prefix, exact names first and bigger suburbs before smaller ones with the same name. Addresses match when every
 * typed word begins a word of the address, through the full-text index of migration V12; the closest reading of what was
 * typed comes first (the address text starts with it), then shorter addresses, then street and number order.
 */
class PostgisSearchIndex(private val dataSource: DataSource) : SearchIndex {
    override suspend fun search(query: SearchQuery, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isEmpty) return@withContext emptyList()
        dataSource.connection.use { connection ->
            val suburbs = suburbs(connection, query, minOf(SUBURB_LIMIT, limit))
            suburbs + addresses(connection, query, limit - suburbs.size)
        }
    }

    private fun suburbs(connection: java.sql.Connection, query: SearchQuery, limit: Int): List<SearchResult> {
        val text = query.words.joinToString(" ")
        // A postcode is searched only when one word of digits was typed ("23" finds 2300, 2304 ...).
        val postcode = query.words.singleOrNull()?.takeIf { word -> word.all { it.isDigit() } }
        return connection.prepareStatement(SUBURB_SQL).use { statement ->
            // The placeholders in SUBURB_SQL, in order: name prefix, any word prefix, postcode (twice), exact name, name prefix.
            statement.setString(1, text)
            statement.setString(2, text)
            statement.setString(3, postcode.orEmpty())
            statement.setString(4, postcode.orEmpty())
            statement.setString(5, text)
            statement.setString(6, text)
            statement.setInt(7, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val bounds = GeoBounds(rows.getDouble("west"), rows.getDouble("south"), rows.getDouble("east"), rows.getDouble("north"))
                        add(
                            SearchResult(
                                kind = SearchResultKind.SUBURB, id = rows.getString("suburb_id"), label = rows.getString("name"),
                                detail = rows.getString("postcode")?.let { "Suburb, NSW $it" } ?: "Suburb, NSW",
                                latitude = (bounds.south + bounds.north) / 2, longitude = (bounds.west + bounds.east) / 2, bounds = bounds,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun addresses(connection: java.sql.Connection, query: SearchQuery, limit: Int): List<SearchResult> {
        if (limit <= 0) return emptyList()
        return connection.prepareStatement(ADDRESS_SQL).use { statement ->
            statement.setString(1, query.tsQuery())
            statement.setString(2, query.normalised)
            statement.setInt(3, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            SearchResult(
                                kind = SearchResultKind.ADDRESS, id = rows.getString("gnaf_pid"),
                                label = AddressFormat.format(
                                    flatNumber = rows.getString("flat_number"), numberFirst = rows.getString("number_first"),
                                    streetName = rows.getString("street_name"), streetType = rows.getString("street_type"),
                                    locality = rows.getString("locality_name"), state = rows.getString("state"), postcode = rows.getString("postcode"),
                                ),
                                detail = null, latitude = rows.getDouble("lat"), longitude = rows.getDouble("lon"),
                                lotId = rows.getString("lot_id"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** At most this many suburbs are suggested, so a short query still leaves room for addresses. */
        const val SUBURB_LIMIT = 4

        val SUBURB_SQL = """
            SELECT s.suburb_id, s.name, s.postcode, ST_XMin(s.geom) AS west, ST_YMin(s.geom) AS south, ST_XMax(s.geom) AS east, ST_YMax(s.geom) AS north
            FROM suburb s LEFT JOIN suburb_label l ON l.suburb_id = s.suburb_id
            WHERE lower(s.name) LIKE ? || '%' OR lower(s.name) LIKE '% ' || ? || '%' OR (? <> '' AND s.postcode LIKE ? || '%')
            ORDER BY (lower(s.name) = ?) DESC, (lower(s.name) LIKE ? || '%') DESC, l.area_km2 DESC NULLS LAST, s.name
            LIMIT ?
        """.trimIndent()

        val ADDRESS_SQL = """
            SELECT a.gnaf_pid, a.flat_number, a.number_first, a.street_name, a.street_type, a.locality_name, a.state, a.postcode,
                   ST_Y(a.geom) AS lat, ST_X(a.geom) AS lon, al.lot_id
            FROM address a LEFT JOIN address_lot al ON al.gnaf_pid = a.gnaf_pid
            WHERE to_tsvector('simple', a.search_text) @@ to_tsquery('simple', ?)
            ORDER BY (a.search_text LIKE ? || '%') DESC, length(a.search_text), a.street_name,
                     NULLIF(regexp_replace(a.number_first, '\D', '', 'g'), '')::int NULLS LAST, a.number_first, a.flat_number NULLS FIRST
            LIMIT ?
        """.trimIndent()
    }
}
