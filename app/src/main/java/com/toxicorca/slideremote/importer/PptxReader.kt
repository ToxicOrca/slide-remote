package com.toxicorca.slideremote.importer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ImportException(message: String) : Exception(message)

data class PptxSlideInfo(val title: String, val hidden: Boolean)

data class PptxInfo(val title: String?, val slides: List<PptxSlideInfo>)

/**
 * Reads slide order, titles and hidden flags out of a .pptx (a zip of XML files).
 *
 * - ppt/presentation.xml          -> <p:sldIdLst> gives slide order as relationship ids
 * - ppt/_rels/presentation.xml.rels -> maps relationship ids to slides/slideN.xml
 * - ppt/slides/slideN.xml          -> title placeholder text, <p:sld show="0"> = hidden
 * - docProps/core.xml              -> document title
 */
object PptxReader {

    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024

    fun read(input: InputStream): PptxInfo {
        val entries = HashMap<String, ByteArray>()
        var sawAnyEntry = false
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                sawAnyEntry = true
                val name = entry.name.trimStart('/')
                if (!entry.isDirectory && isWanted(name)) {
                    val bytes = zip.readBytes()
                    if (bytes.size <= MAX_ENTRY_BYTES) entries[name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        if (!sawAnyEntry) {
            throw ImportException("That isn't a readable .pptx. Password-protected files can't be imported.")
        }

        val slidePaths = slideOrder(entries)
        if (slidePaths.isEmpty()) throw ImportException("No slides found in that file.")

        val slides = slidePaths.map { path ->
            entries[path]?.let { runCatching { parseSlide(it) }.getOrNull() }
                ?: PptxSlideInfo("", false)
        }
        val title = entries["docProps/core.xml"]?.let { runCatching { parseCoreTitle(it) }.getOrNull() }
        return PptxInfo(title?.takeIf { it.isNotBlank() }, slides)
    }

    private fun isWanted(name: String): Boolean =
        name == "ppt/presentation.xml" ||
            name == "ppt/_rels/presentation.xml.rels" ||
            name == "docProps/core.xml" ||
            (name.startsWith("ppt/slides/") && name.endsWith(".xml") && !name.contains("/_rels/"))

    /** Slide XML paths in presentation order. */
    private fun slideOrder(entries: Map<String, ByteArray>): List<String> {
        val pres = entries["ppt/presentation.xml"]
        val rels = entries["ppt/_rels/presentation.xml.rels"]
        if (pres != null && rels != null) {
            val ids = runCatching { parseSlideIds(pres) }.getOrDefault(emptyList())
            val targets = runCatching { parseRels(rels) }.getOrDefault(emptyMap())
            val ordered = ids.mapNotNull { id -> targets[id]?.let { resolveTarget(it) } }
                .filter { entries.containsKey(it) }
            if (ordered.isNotEmpty()) return ordered
        }
        // Fallback: slide1.xml, slide2.xml, ... by number.
        val re = Regex("""^ppt/slides/slide(\d+)\.xml$""")
        return entries.keys.mapNotNull { k -> re.find(k)?.let { it.groupValues[1].toInt() to k } }
            .sortedBy { it.first }
            .map { it.second }
    }

    /** Targets in presentation.xml.rels are relative to ppt/ ("slides/slide1.xml") or absolute. */
    private fun resolveTarget(target: String): String {
        val raw = if (target.startsWith("/")) target.trimStart('/') else "ppt/$target"
        val parts = mutableListOf<String>()
        for (p in raw.split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(ByteArrayInputStream(bytes), null)
        }

    private fun parseSlideIds(bytes: ByteArray): List<String> {
        val p = parser(bytes)
        val ids = mutableListOf<String>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "sldId") {
                relationshipId(p)?.let(ids::add)
            }
            ev = p.next()
        }
        return ids
    }

    /** The r:id attribute (transitional or strict OOXML relationships namespace). */
    private fun relationshipId(p: XmlPullParser): String? {
        for (i in 0 until p.attributeCount) {
            val ns = p.getAttributeNamespace(i) ?: ""
            if (p.getAttributeName(i) == "id" && ns.endsWith("relationships")) {
                return p.getAttributeValue(i)
            }
        }
        return null
    }

    private fun parseRels(bytes: ByteArray): Map<String, String> {
        val p = parser(bytes)
        val map = HashMap<String, String>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "Relationship") {
                val id = p.getAttributeValue(null, "Id")
                val target = p.getAttributeValue(null, "Target")
                val mode = p.getAttributeValue(null, "TargetMode")
                if (id != null && target != null && mode != "External") map[id] = target
            }
            ev = p.next()
        }
        return map
    }

    private fun isDrawingNs(ns: String?): Boolean = ns != null && ns.contains("drawingml")

    private fun parseSlide(bytes: ByteArray): PptxSlideInfo {
        val p = parser(bytes)
        var hidden = false
        var spDepth = 0
        var shapeIsTitle = false
        val text = StringBuilder()
        var title: String? = null
        var firstText: String? = null

        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "sld" -> {
                        val show = p.getAttributeValue(null, "show")
                        hidden = show == "0" || show == "false"
                    }
                    "sp" -> {
                        spDepth++
                        if (spDepth == 1) {
                            shapeIsTitle = false
                            text.setLength(0)
                        }
                    }
                    "ph" -> if (spDepth >= 1) {
                        val type = p.getAttributeValue(null, "type")
                        if (type == "title" || type == "ctrTitle") shapeIsTitle = true
                    }
                    "t" -> if (spDepth >= 1 && isDrawingNs(p.namespace)) {
                        // Leaves the parser on </a:t>; the loop's next() moves past it.
                        text.append(p.nextText())
                    }
                    "br" -> if (spDepth >= 1 && isDrawingNs(p.namespace)) text.append(' ')
                }

                XmlPullParser.END_TAG -> when (p.name) {
                    "p" -> if (spDepth >= 1 && isDrawingNs(p.namespace)) text.append(' ')
                    "sp" -> {
                        if (spDepth == 1) {
                            val cleaned = clean(text)
                            if (cleaned.isNotEmpty()) {
                                if (shapeIsTitle && title == null) title = cleaned
                                if (firstText == null) firstText = cleaned
                            }
                        }
                        if (spDepth > 0) spDepth--
                    }
                }
            }
            ev = p.next()
        }
        return PptxSlideInfo(title ?: firstText ?: "", hidden)
    }

    private fun parseCoreTitle(bytes: ByteArray): String? {
        val p = parser(bytes)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "title") {
                return clean(StringBuilder(p.nextText()))
            }
            ev = p.next()
        }
        return null
    }

    private fun clean(sb: CharSequence): String =
        sb.toString().replace(Regex("\\s+"), " ").trim().take(80)
}
