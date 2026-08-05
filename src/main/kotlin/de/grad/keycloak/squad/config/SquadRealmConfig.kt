package de.grad.keycloak.squad.config

import org.keycloak.models.RealmModel

/**
 * Per-realm configuration for the Arma 3 Squad provider.
 *
 * Settings are persisted as realm attributes so they survive restarts and are
 * editable via the Keycloak Admin REST API or Admin UI (Realm settings → Attributes).
 *
 * Example — enable via Admin REST API:
 * ```
 * PATCH /admin/realms/{realm}
 * {
 *   "attributes": {
 *     "arma3squad.enabled":          "true",
 *     "arma3squad.groupName":        "arma3-squad",
 *     "arma3squad.steamIdAttribute": "steamId"
 *   }
 * }
 * ```
 */
data class SquadRealmConfig(
    /** Whether the squad.xml endpoint is active for this realm. */
    val enabled: Boolean,
    /** Name of the Keycloak group whose members are listed in squad.xml. */
    val groupName: String,
    /** Name of the user attribute that holds the Steam 64-bit ID. */
    val steamIdAttribute: String,
) {
    companion object {
        const val ATTR_ENABLED           = "arma3squad.enabled"
        const val ATTR_GROUP_NAME        = "arma3squad.groupName"
        const val ATTR_STEAM_ID_ATTR     = "arma3squad.steamIdAttribute"

        const val DEFAULT_GROUP_NAME     = "arma3-squad"
        const val DEFAULT_STEAM_ID_ATTR  = "steamId"

        fun fromRealm(realm: RealmModel): SquadRealmConfig {
            fun attr(key: String, default: String): String =
                realm.getAttribute(key)?.takeIf { it.isNotBlank() } ?: default

            return SquadRealmConfig(
                enabled          = attr(ATTR_ENABLED, "false").equals("true", ignoreCase = true),
                groupName        = attr(ATTR_GROUP_NAME, DEFAULT_GROUP_NAME),
                steamIdAttribute = attr(ATTR_STEAM_ID_ATTR, DEFAULT_STEAM_ID_ATTR),
            )
        }
    }
}
