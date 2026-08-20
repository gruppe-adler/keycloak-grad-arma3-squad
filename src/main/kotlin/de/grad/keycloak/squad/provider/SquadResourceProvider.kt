package de.grad.keycloak.squad.provider

import de.grad.keycloak.squad.config.SquadRealmConfig
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider
import java.io.InputStream

/**
 * JAX-RS resource mounted at `/realms/{realm}/arma3-squad/`.
 *
 * Endpoints:
// *   GET /realms/{realm}/arma3-squad/squad.xml  — Arma 3 squad.xml
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
            appendLine("""<?xml-stylesheet href="web/squad.xsl" type="text/xsl"?>""")
            appendLine("""<squad nick="A">""")
            appendLine("""  <name>Gruppe Adler</name>""")
            appendLine("""  <email>kontakt@gruppe-adler.de</email>""")
            appendLine("""  <web>https://gruppe-adler.de</web>""")
            appendLine("""  <picture>logo.paa</picture>""")
            appendLine("""  <title>Gruppe Adler</title>""")

            for (user in members) {
                val steamId     = user.getFirstAttribute(config.steamIdAttribute)!!
                val displayName = listOfNotNull(user.firstName, user.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { user.username }

                appendLine("""  <member id="${steamId.escapeXml()}" nick="${user.username.escapeXml()}">""")
                appendLine("""    <name>${displayName.escapeXml()}</name>""")
                appendLine("""    <email>admin@gruppe-adler.de</email>""")
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

    @GET
    @Path("/web/{filename}")
    fun serveWebFile(@PathParam("filename") filename: String): Response {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        return buildFileResponse( "/static/web/$filename");
    }

    @GET
    @Path("{filename}")
    fun serveFile(@PathParam("filename") filename: String): Response {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        return buildFileResponse("/static/$filename");
    }

    fun buildFileResponse(resourcePath: String): Response {
        var bytes = javaClass.getResourceAsStream(resourcePath)
            ?.readBytes()
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        if (resourcePath.endsWith("squad.xsl")) {
            val realm = session.context.realm.name
            val basePath = "/realms/$realm/${SquadResourceProviderFactory.PROVIDER_ID}/web/"
            bytes = String(bytes)
                .replace("{{BASE_HREF}}", basePath)
                .toByteArray()
        }

        return Response.ok(bytes)
            .header("Content-Type", detectMediaType(resourcePath))
            .build()
    }

    private fun detectMediaType(filename: String) = when {
        filename.endsWith(".xsl")  -> "text/xsl"
        filename.endsWith(".dtd")  -> "application/xml-dtd"
        filename.endsWith(".xml")  -> "text/xml"
        filename.endsWith(".html") -> "text/html"
        filename.endsWith(".js")   -> "application/javascript"
        filename.endsWith(".css")  -> "text/css"
        filename.endsWith(".png")  -> "image/png"
        filename.endsWith(".svg")  -> "image/svg+xml"
        filename.endsWith(".ico") or filename.endsWith(".paa")  -> "image/x-icon"
        else                       -> "application/octet-stream"
    }
}
