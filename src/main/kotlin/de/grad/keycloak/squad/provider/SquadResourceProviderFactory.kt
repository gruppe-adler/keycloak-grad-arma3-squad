package de.grad.keycloak.squad.provider

import de.grad.keycloak.squad.config.SquadRealmConfig
import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.provider.ProviderConfigProperty.BOOLEAN_TYPE
import org.keycloak.provider.ProviderConfigProperty.STRING_TYPE
import org.keycloak.services.resource.RealmResourceProvider
import org.keycloak.services.resource.RealmResourceProviderFactory

/**
 * SPI factory for the Arma 3 Squad provider.
 *
 * Keycloak discovers this class via the ServiceLoader file at:
 *   META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory
 *
 * The provider ID `"arma3-squad"` becomes the URL path segment, so all
 * endpoints are reachable under `/realms/{realm}/arma3-squad/…`.
 *
 * Per-realm configuration is stored as realm attributes — see [SquadRealmConfig].
 */
class SquadResourceProviderFactory : RealmResourceProviderFactory {

    companion object {
        const val PROVIDER_ID = "arma3-squad"
    }

    override fun getId(): String = PROVIDER_ID

    override fun create(session: KeycloakSession): RealmResourceProvider =
        SquadResourceProvider(session)

    override fun init(config: Config.Scope) = Unit

    override fun postInit(factory: KeycloakSessionFactory) = Unit

    override fun close() = Unit

    override fun getConfigMetadata(): List<ProviderConfigProperty?> = listOf(
        ProviderConfigProperty().apply {
            name         = SquadRealmConfig.ATTR_ENABLED
            label        = "Enabled"
            helpText     = "Activate the squad.xml endpoint for this realm."
            type         = BOOLEAN_TYPE
            defaultValue = "false"
        },
        ProviderConfigProperty().apply {
            name         = SquadRealmConfig.ATTR_GROUP_NAME
            label        = "Squad Group Name"
            helpText     = "Name of the Keycloak group whose members appear in squad.xml."
            type         = STRING_TYPE
            defaultValue = SquadRealmConfig.DEFAULT_GROUP_NAME
        },
        ProviderConfigProperty().apply {
            name         = SquadRealmConfig.ATTR_STEAM_ID_ATTR
            label        = "Steam ID User Attribute"
            helpText     = "User attribute that stores the Steam 64-bit ID."
            type         = STRING_TYPE
            defaultValue = SquadRealmConfig.DEFAULT_STEAM_ID_ATTR
        },
    )
}
