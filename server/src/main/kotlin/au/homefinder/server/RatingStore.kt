package au.homefinder.server

import au.homefinder.shared.model.HomelyRating
import au.homefinder.shared.model.SuburbRatingPoint
import javax.sql.DataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.sql.SQLException

/** A rating as stored: the score and the day you entered it. */
data class StoredRating(val rating: HomelyRating, val ratedOn: LocalDate)

/** Keeps your Homely scores. A fake stands in for it in route tests. */
interface RatingStore {
    suspend fun get(suburbId: String): StoredRating?

    suspend fun put(suburbId: String, rating: HomelyRating, ratedOn: LocalDate)

    /** Removes a rating; removing one that does not exist is not an error. */
    suspend fun delete(suburbId: String)

    /** Every rated suburb that still exists, with the point its label sits at. */
    suspend fun all(): List<SuburbRatingPoint>
}

/**
 * Reads and writes `suburb_rating` (ADR 0028). It needs a database connection that can write, unlike the tile and lookup
 * pools. Before the table exists reads find nothing, so the app works until migration V13 is applied; writes then fail.
 */
class PostgisRatingStore(private val dataSource: DataSource) : RatingStore {
    override suspend fun get(suburbId: String): StoredRating? = tolerateMissingTable(null) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(GET_SQL).use { statement ->
                statement.setString(1, suburbId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return@use null
                    val reviews = rows.getInt("review_count").takeIf { !rows.wasNull() }
                    val day = rows.getDate("rated_on").toLocalDate()
                    StoredRating(HomelyRating(rows.getBigDecimal("stars").toDouble(), reviews), LocalDate(day.year, day.monthValue, day.dayOfMonth))
                }
            }
        }
    }

    override suspend fun put(suburbId: String, rating: HomelyRating, ratedOn: LocalDate) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(UPSERT_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.setBigDecimal(2, java.math.BigDecimal.valueOf(rating.stars).setScale(1, java.math.RoundingMode.HALF_UP))
                    if (rating.reviewCount == null) statement.setNull(3, java.sql.Types.INTEGER) else statement.setInt(3, rating.reviewCount!!)
                    statement.setDate(4, java.sql.Date.valueOf(ratedOn.toString()))
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun delete(suburbId: String) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { connection ->
                connection.prepareStatement(DELETE_SQL).use { statement ->
                    statement.setString(1, suburbId)
                    statement.executeUpdate()
                }
            }
        }
    }

    override suspend fun all(): List<SuburbRatingPoint> = tolerateMissingTable(emptyList()) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(ALL_SQL).use { statement ->
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val reviews = rows.getInt("review_count").takeIf { !rows.wasNull() }
                            add(
                                SuburbRatingPoint(
                                    suburbId = rows.getString("suburb_id"), name = rows.getString("name"),
                                    rating = HomelyRating(rows.getBigDecimal("stars").toDouble(), reviews),
                                    latitude = rows.getDouble("lat"), longitude = rows.getDouble("lon"),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun <T> tolerateMissingTable(whenMissing: T, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (failure: SQLException) {
            if (failure.sqlState == UNDEFINED_TABLE) whenMissing else throw failure
        }
    }

    private companion object {
        const val UNDEFINED_TABLE = "42P01"
        const val GDA2020_SRID = 7844

        const val GET_SQL = "SELECT stars, review_count, rated_on FROM suburb_rating WHERE suburb_id = ?"

        const val UPSERT_SQL = """
            INSERT INTO suburb_rating (suburb_id, stars, review_count, rated_on) VALUES (?, ?, ?, ?)
            ON CONFLICT (suburb_id) DO UPDATE
            SET stars = EXCLUDED.stars, review_count = EXCLUDED.review_count, rated_on = EXCLUDED.rated_on, updated_at = now()
        """

        const val DELETE_SQL = "DELETE FROM suburb_rating WHERE suburb_id = ?"

        // The label sits where the suburb's name is drawn (the label table stores Web Mercator), else inside the suburb.
        const val ALL_SQL = """
            SELECT r.suburb_id, s.name, r.stars, r.review_count,
                   ST_Y(COALESCE(ST_Transform(l.geom, $GDA2020_SRID), ST_PointOnSurface(s.geom))) AS lat,
                   ST_X(COALESCE(ST_Transform(l.geom, $GDA2020_SRID), ST_PointOnSurface(s.geom))) AS lon
            FROM suburb_rating r JOIN suburb s ON s.suburb_id = r.suburb_id LEFT JOIN suburb_label l ON l.suburb_id = r.suburb_id
            ORDER BY s.name
        """
    }
}
