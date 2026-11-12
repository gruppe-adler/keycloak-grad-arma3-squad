package de.grad.keycloak.squad.config

import de.grad.keycloak.squad.config.SquadRealmConfig.Companion.ATTR_ENABLED
import de.grad.keycloak.squad.config.SquadRealmConfig.Companion.ATTR_GROUP_NAME
import de.grad.keycloak.squad.config.SquadRealmConfig.Companion.ATTR_STEAM_ID_ATTR
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.keycloak.models.RealmModel

class SquadRealmConfigTest {

    private fun realm(vararg attributes: Pair<String, String>): RealmModel {
        val map = attributes.toMap()
        return mockk { every { getAttribute(any<String>()) } answers { map[firstArg()] } }
    }

    @Test
    fun `uses defaults when no attributes are set`() {
        assertEquals(
            SquadRealmConfig(enabled = false, groupName = "arma3-squad", steamIdAttribute = "steamId"),
            SquadRealmConfig.fromRealm(realm()),
        )
    }

    @Test
    fun `falls back to defaults for blank attributes`() {
        val config = SquadRealmConfig.fromRealm(
            realm(ATTR_ENABLED to " ", ATTR_GROUP_NAME to "", ATTR_STEAM_ID_ATTR to "  "),
        )
        assertEquals(SquadRealmConfig(false, "arma3-squad", "steamId"), config)
    }

    @Test
    fun `uses configured group and steam id attribute`() {
        val config = SquadRealmConfig.fromRealm(
            realm(ATTR_ENABLED to "true", ATTR_GROUP_NAME to "members", ATTR_STEAM_ID_ATTR to "steam64"),
        )
        assertEquals(SquadRealmConfig(true, "members", "steam64"), config)
    }

    @ParameterizedTest
    @CsvSource("true,true", "TRUE,true", "True,true", "false,false", "yes,false", "1,false")
    fun `parses enabled flag case-insensitively`(value: String, expected: Boolean) {
        assertEquals(expected, SquadRealmConfig.fromRealm(realm(ATTR_ENABLED to value)).enabled)
    }
}
