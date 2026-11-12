package de.grad.keycloak.squad.provider

import de.grad.keycloak.squad.config.SquadRealmConfig.Companion.ATTR_ENABLED
import de.grad.keycloak.squad.config.SquadRealmConfig.Companion.ATTR_GROUP_NAME
import io.mockk.every
import io.mockk.mockk
import jakarta.ws.rs.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.w3c.dom.Document
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import javax.xml.parsers.DocumentBuilderFactory

class SquadResourceProviderTest {

    private val group: GroupModel = mockk { every { name } returns "arma3-squad" }

    private fun user(username: String, steamId: String?): UserModel = mockk {
        every { this@mockk.username } returns username
        every { getFirstAttribute("steamId") } returns steamId
    }

    private fun provider(
        attributes: Map<String, String> = mapOf(ATTR_ENABLED to "true"),
        groups: List<GroupModel> = listOf(group),
        members: List<UserModel> = emptyList(),
    ): SquadResourceProvider {
        val realm: RealmModel = mockk {
            every { name } returns "grad"
            every { getAttribute(any<String>()) } answers { attributes[firstArg()] }
        }
        val session: KeycloakSession = mockk {
            every { context.realm } returns realm
            every { groups().getGroupsStream(realm) } answers { groups.stream() }
            every { users().getGroupMembersStream(realm, group) } answers { members.stream() }
        }
        return SquadResourceProvider(session)
    }

    /** Parses with DTD validation, resolving `squad.dtd` from the classpath. */
    private fun parseValidated(xml: String): Document {
        val builder = DocumentBuilderFactory.newInstance()
            .apply { isValidating = true }
            .newDocumentBuilder()
        builder.setEntityResolver { _, systemId ->
            check(systemId.endsWith("squad.dtd")) { "unexpected entity $systemId" }
            InputSource(javaClass.getResourceAsStream("/static/squad.dtd"))
        }
        builder.setErrorHandler(object : ErrorHandler {
            override fun warning(e: SAXParseException) = Unit
            override fun error(e: SAXParseException) = throw e
            override fun fatalError(e: SAXParseException) = throw e
        })
        return builder.parse(InputSource(xml.reader()))
    }

    // --- squad.xml ---------------------------------------------------------

    @Test
    fun `squad xml is 404 when provider is disabled`() {
        assertThrows<NotFoundException> { provider(attributes = emptyMap()).getSquadXml() }
    }

    @Test
    fun `squad xml is 404 when configured group does not exist`() {
        val p = provider(attributes = mapOf(ATTR_ENABLED to "true", ATTR_GROUP_NAME to "nope"))
        assertThrows<NotFoundException> { p.getSquadXml() }
    }

    @Test
    fun `lists only members with a steam id`() {
        val response = provider(
            members = listOf(
                user("alice", "76561198000000001"),
                user("bob", null),
                user("carol", "   "),
                user("dave", "76561198000000002"),
            ),
        ).getSquadXml()

        assertEquals(200, response.status)
        val doc = parseValidated(response.entity as String)
        val members = doc.getElementsByTagName("member")
        val nicks = (0 until members.length).map { members.item(it).attributes.getNamedItem("nick").nodeValue }
        val ids = (0 until members.length).map { members.item(it).attributes.getNamedItem("id").nodeValue }

        assertEquals(listOf("alice", "dave"), nicks)
        assertEquals(listOf("76561198000000001", "76561198000000002"), ids)
    }

    @Test
    fun `escapes special characters in username and steam id`() {
        val nasty = """a<b>&"c'"""
        val xml = provider(members = listOf(user(nasty, "1\"2"))).getSquadXml().entity as String

        val member = parseValidated(xml).getElementsByTagName("member").item(0)
        assertEquals(nasty, member.attributes.getNamedItem("nick").nodeValue)
        assertEquals("1\"2", member.attributes.getNamedItem("id").nodeValue)
        assertEquals(nasty, member.firstChildElement("name").textContent)
    }

    private fun org.w3c.dom.Node.firstChildElement(tag: String) =
        (0 until childNodes.length).map { childNodes.item(it) }.first { it.nodeName == tag }

    // --- static files ------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ["..", "..squad.dtd", "a/b", "..\\pom.xml", "web\\squad.css"])
    fun `rejects path traversal`(filename: String) {
        assertEquals(400, provider().serveFile(filename).status)
        assertEquals(400, provider().serveWebFile(filename).status)
    }

    @Test
    fun `unknown file is 404`() {
        assertEquals(404, provider().serveFile("missing.txt").status)
        assertEquals(404, provider().serveWebFile("missing.txt").status)
    }

    @Test
    fun `serves static files with matching content type`() {
        val cases = mapOf(
            provider().serveFile("squad.dtd") to "application/xml-dtd",
            provider().serveFile("template.xml") to "text/xml",
            provider().serveWebFile("squad.css") to "text/css",
            provider().serveWebFile("logo.svg") to "image/svg+xml",
        )
        for ((response, contentType) in cases) {
            assertEquals(200, response.status)
            assertEquals(contentType, response.getHeaderString("Content-Type"))
        }
    }

    @Test
    fun `squad xsl gets realm-specific base href`() {
        val response = provider().serveWebFile("squad.xsl")
        val xsl = String(response.entity as ByteArray)

        assertEquals("text/xsl", response.getHeaderString("Content-Type"))
        assertTrue(xsl.contains("""<base href="/realms/grad/arma3-squad/web/" />"""))
        assertFalse(xsl.contains("{{BASE_HREF}}"))
    }
}
