package au.homefinder.app.map

import au.homefinder.shared.model.NaplanRules
import au.homefinder.shared.model.SuburbFilterValue

/** One suburb's NAPLAN label on the map (ADR 0076): its text ("+27", "-41", "0") and the point the suburb's name is drawn at. */
data class NaplanLabel(val suburbId: String, val name: String, val text: String, val at: LonLat)

/**
 * The map's NAPLAN labels (ADR 0076), made from the filter values the page already loads (ADR 0074): a suburb gets one only when it has an average of
 * your typed figures for its zoned schools, and only when the server sent the point its name is drawn at. The average is the server's, already rounded
 * to a whole point (halves upwards), so no second average is worked out here.
 */
object NaplanLabels {
    /** The label's text: the whole-point average with its sign always shown, "0" for none. */
    fun text(average: Int): String = NaplanRules.signed(average)

    fun from(values: List<SuburbFilterValue>): List<NaplanLabel> = values.mapNotNull { value ->
        val average = value.zonedNaplan
        val latitude = value.latitude
        val longitude = value.longitude
        if (average == null || latitude == null || longitude == null) null
        else NaplanLabel(value.suburbId, value.name, text(average), LonLat(longitude, latitude))
    }
}
