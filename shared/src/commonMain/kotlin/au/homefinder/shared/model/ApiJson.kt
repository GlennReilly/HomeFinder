package au.homefinder.shared.model

import kotlinx.serialization.json.Json

/**
 * The JSON configuration for the API and every client. Nulls and defaults are always written, so an unknown value
 * is explicit on the wire ("value": null) and never an omitted field; readers ignore fields they do not know yet.
 */
val ApiJson: Json = Json {
    explicitNulls = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}
