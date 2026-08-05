package de.example.keycloak.arma3squad.provider

import de.example.keycloak.arma3squad.config.SquadRealmConfig
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

/**
 * JAX-RS resource mounted at `/realms/{realm}/arma3-squad/`.
 *
 * Endpoints:
 *   GET /realms/{realm}/arma3-squad/squad.xml  — Arma 3 squad.xml
 */
class SquadResourceProvider(
    private val session: KeycloakSession,
) : RealmResourceProvider {

    override fun getResource(): Any = this

    override fun close() = Unit

    @GET
    @Path("squad.xml")
    @Produces(MediaType.APPLICATION_XML)
    fun getSquadXml(): Response {
        val realm  = session.context.realm
        val config = SquadRealmConfig.fromRealm(realm)

        if (!config.enabled) {
            throw NotFoundException("Arma 3 Squad provider is disabled for realm '${realm.name}'.")
        }

        val group = session.groups()
            .getGroupsStream(realm)
            .filter { it.name == config.groupName }
            .findFirst()
            .orElseThrow {
                NotFoundException("Group '${config.groupName}' not found in realm '${realm.name}'.")
            }

        val members = session.users()
            .getGroupMembersStream(realm, group)
            .filter { user -> user.getFirstAttribute(config.steamIdAttribute)?.isNotBlank() == true }
            .toList()

        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<!DOCTYPE squad SYSTEM "squad.dtd">""")
            appendLine("""<?xml-stylesheet href="squad.xsl" type="text/xsl"?>""")
            appendLine("""<squad nick="${realm.name.escapeXml()}">""")
            appendLine("""  <name>${realm.displayName?.escapeXml() ?: realm.name.escapeXml()}</name>""")
            appendLine("""  <email/>""")
            appendLine("""  <web/>""")
            appendLine("""  <picture/>""")
            appendLine("""  <title/>""")

            for (user in members) {
                val steamId     = user.getFirstAttribute(config.steamIdAttribute)!!
                val displayName = listOfNotNull(user.firstName, user.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { user.username }

                appendLine("""  <member id="${steamId.escapeXml()}" nick="${user.username.escapeXml()}">""")
                appendLine("""    <name>${displayName.escapeXml()}</name>""")
                appendLine("""    <email>${(user.email ?: "").escapeXml()}</email>""")
                appendLine("""    <icq>0</icq>""")
                appendLine("""    <remark/>""")
                appendLine("""  </member>""")
            }

            append("</squad>")
        }

        return Response.ok(xml, MediaType.APPLICATION_XML_TYPE).build()
    }

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
