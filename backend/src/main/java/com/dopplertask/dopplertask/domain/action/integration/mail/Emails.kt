package com.dopplertask.dopplertask.domain.action.integration.mail

import com.dopplertask.dopplertask.domain.action.integration.JsonApi
import jakarta.mail.Address
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeUtility
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities
import tools.jackson.databind.JsonNode
import java.io.ByteArrayInputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Properties

/**
 * Reads a raw email into the fields that mailparser gives, the reader the originals use, so that connectors give what
 * they gave: the sender and recipients with their text and HTML, the subject, date and ids, the text and HTML bodies,
 * the text as HTML, and the attachments with their bytes as base64.
 */
object Emails {

    private val LINK = Regex("""\b(?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]]""")
    private val HEADER_NAMES = Regex("^([^:\\s]+):")

    // As JavaScript writes a date in JSON.
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun read(raw: String): JsonNode {
        val message = MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(raw.toByteArray(Charsets.UTF_8)))
        val parts = Parts().also { it.collect(message) }
        val fields = linkedMapOf<String, Any?>(
            "attachments" to parts.attachments,
            "headers" to emptyMap<String, Any>(),
            "headerLines" to headerLinesOf(raw),
            "html" to (parts.html ?: false),
            "text" to parts.text,
            "textAsHtml" to parts.text?.let(::textAsHtml),
            "subject" to message.subject,
            "references" to message.getHeader("References")?.firstOrNull()?.trim()?.split(Regex("\\s+"))?.let { if (it.size == 1) it.first() else it },
            "date" to message.sentDate?.toInstant()?.let(DATE::format),
            "to" to addressesOf(message.getRecipients(Message.RecipientType.TO)),
            "from" to addressesOf(message.from),
            "cc" to addressesOf(message.getRecipients(Message.RecipientType.CC)),
            "bcc" to addressesOf(message.getRecipients(Message.RecipientType.BCC)),
            "replyTo" to message.getHeader("Reply-To")?.let { addressesOf(message.replyTo) },
            "messageId" to message.messageID,
            "inReplyTo" to message.getHeader("In-Reply-To")?.firstOrNull()?.trim(),
        )
        return JsonApi.mapper.valueToTree(fields.filterValues { it != null })
    }

    /** An email given as text, or as base64url as Gmail gives it. */
    fun readEncodedOrPlain(raw: String): JsonNode =
        read(if (raw.contains(':')) raw else String(Base64.getUrlDecoder().decode(raw.trimEnd('=')), Charsets.UTF_8))

    /** The bodies and attachments of an email, found in its parts. */
    private class Parts {
        var text: String? = null
        var html: String? = null
        val attachments = mutableListOf<Map<String, Any?>>()

        fun collect(part: Part) {
            when {
                part.isMimeType("multipart/*") -> (part.content as Multipart).let { multipart -> (0 until multipart.count).forEach { collect(multipart.getBodyPart(it)) } }
                Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) || part.fileName != null -> {
                    val bytes = part.inputStream.readAllBytes()
                    attachments += mapOf("filename" to part.fileName?.let(MimeUtility::decodeText), "contentType" to part.contentType.substringBefore(';').trim().lowercase(),
                        "size" to bytes.size, "base64" to Base64.getEncoder().encodeToString(bytes))
                }
                part.isMimeType("text/plain") && text == null -> text = (part.content as String).replace("\r\n", "\n")
                part.isMimeType("text/html") && html == null -> html = (part.content as String).replace("\r\n", "\n")
            }
        }
    }

    /** Each header as it was written, by its lowercase name. A header that goes on over several lines keeps them. */
    private fun headerLinesOf(raw: String): List<Map<String, String>> {
        val head = raw.replace("\r\n", "\n").substringBefore("\n\n")
        return head.split("\n").fold(mutableListOf<String>()) { lines, line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] += "\r\n" + line else lines += line
            lines
        }.mapNotNull { line -> HEADER_NAMES.find(line)?.let { mapOf("key" to it.groupValues[1].lowercase(), "line" to line) } }
    }

    /** Addresses as mailparser gives them: each with its name, and all of them as text and as HTML. */
    private fun addressesOf(addresses: Array<Address>?): Map<String, Any>? {
        val people = addresses?.filterIsInstance<InternetAddress>()?.takeIf { it.isNotEmpty() } ?: return null
        return mapOf(
            "value" to people.map { mapOf("address" to it.address, "name" to it.personal.orEmpty()) },
            "html" to people.joinToString(", ") { person ->
                val email = "<a href=\"mailto:${encoded(person.address)}\" class=\"mp_address_email\">${encoded(person.address)}</a>"
                "<span class=\"mp_address_group\">" + (person.personal?.let { "<span class=\"mp_address_name\">${encoded(it)}</span> &lt;$email&gt;" } ?: email) + "</span>"
            },
            "text" to people.joinToString(", ") { person -> person.personal?.let { "\"$it\" <${person.address}>" } ?: person.address },
        )
    }

    /** Plain text as HTML: escaped, with links, its lines broken and its paragraphs kept, as mailparser writes it. */
    private fun textAsHtml(text: String): String {
        var html = ""
        var last = 0
        LINK.findAll(text).forEach { link ->
            val href = if (link.value.startsWith("www.")) "http://${link.value}" else link.value
            html += encoded(text.substring(last, link.range.first), named = true) + "<a href=\"${encoded(href, named = true)}\">${encoded(link.value, named = true)}</a>"
            last = link.range.last + 1
        }
        html = (html + encoded(text.substring(last), named = true)).trim()
        return "<p>" + html.replace(Regex("\n\\s*\n"), "</p><p>").replace("\n", "<br/>") + "</p>"
    }

    /**
     * Text with the characters that HTML reserves, and every one outside ASCII, written as references, as the he encoder
     * mailparser uses does: by name where HTML has one and [named] is asked for, else by number.
     */
    private fun encoded(text: String, named: Boolean = false): String = buildString {
        text.codePoints().forEach { point ->
            when {
                point !in RESERVED && (point in 0x20..0x7E || point == '\n'.code || point == '\r'.code || point == '\t'.code) -> appendCodePoint(point)
                named -> append(RESERVED[point] ?: nameOf(point) ?: numbered(point))
                else -> append(numbered(point))
            }
        }
    }

    private fun numbered(point: Int) = "&#x${Integer.toHexString(point).uppercase()};"

    // The characters HTML reserves, which he writes as references even in ASCII, with their names.
    private val RESERVED = mapOf('&'.code to "&amp;", '<'.code to "&lt;", '>'.code to "&gt;", '"'.code to "&quot;", '\''.code to "&apos;", '`'.code to "&grave;")

    private val ESCAPE = Document.OutputSettings().escapeMode(Entities.EscapeMode.extended).charset("ascii")

    /** The HTML name of a character outside ASCII, such as &eacute; for é, when it has one. */
    private fun nameOf(point: Int): String? = Entities.escape(String(Character.toChars(point)), ESCAPE).takeIf { it.startsWith("&") && !it.startsWith("&#") }
}
