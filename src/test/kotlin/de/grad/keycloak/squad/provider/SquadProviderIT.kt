package de.grad.keycloak.squad.provider

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.keycloak.representations.userprofile.config.UPConfig.UnmanagedAttributePolicy
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Smoke test: deploys the shaded provider JAR into a real Keycloak (same version
 * as `keycloak.version` in the POM) and calls the endpoints over HTTP.
 *
 * Runs via maven-failsafe in the `verify` phase; requires Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SquadProviderIT {

    private val realmName = "grad"

    private val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:${System.getProperty("keycloak.version")}")
        .withProviderLibsFrom(listOf(File(System.getProperty("provider.jar"))))

    private val http = HttpClient.newHttpClient()

    @BeforeAll
    fun setUp() {
        keycloak.start()
        val admin = keycloak.keycloakAdminClient

        admin.realms().create(RealmRepresentation().apply {
            realm = realmName
            isEnabled = true
            attributes = mapOf("arma3squad.enabled" to "true")
        })
        val realm = admin.realm(realmName)

        // Keycloak's declarative user profile drops undeclared attributes like `steamId`.
        val userProfile = realm.users().userProfile()
        userProfile.update(userProfile.configuration.apply {
            unmanagedAttributePolicy = UnmanagedAttributePolicy.ENABLED
        })

        realm.groups().add(GroupRepresentation().apply { name = "arma3-squad" }).close()
        val groupId = realm.groups().groups().single { it.name == "arma3-squad" }.id

        fun addMember(username: String, steamId: String?) {
            realm.users().create(UserRepresentation().apply {
                this.username = username
                isEnabled = true
                if (steamId != null) attributes = mapOf("steamId" to listOf(steamId))
            }).close()
            val userId = realm.users().search(username, true).single().id
            realm.users().get(userId).joinGroup(groupId)
        }
        addMember("alice", "76561198000000001")
        addMember("bob", null)
    }

    @AfterAll
    fun tearDown() = keycloak.stop()

    private fun get(path: String): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI("${keycloak.authServerUrl}/realms/$realmName/arma3-squad/$path")).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @Test
    fun `squad xml lists group members with a steam id`() {
        val response = get("squad.xml")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("""<member id="76561198000000001" nick="alice">"""), response.body())
        assertFalse(response.body().contains("bob"), response.body())
    }

    @Test
    fun `serves static assets`() {
        assertEquals(200, get("squad.dtd").statusCode())
        val xsl = get("web/squad.xsl")
        assertEquals(200, xsl.statusCode())
        assertTrue(xsl.body().contains("/realms/$realmName/arma3-squad/web/"))
    }
}
