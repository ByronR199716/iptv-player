package com.example.iptvplayer.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedChannel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val tvgId: String
)

data class M3uResult(
    val channels: List<ParsedChannel>,
    val epgUrl: String
)

/** Parser de listas M3U / M3U8 (formato #EXTINF) en streaming, línea por línea. */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    private class Pending(val name: String, val logo: String, val group: String, val tvgId: String)

    fun parse(reader: BufferedReader): M3uResult {
        val out = ArrayList<ParsedChannel>()
        var epgUrl = ""
        var pending: Pending? = null
        var extGroup = ""
        var first = true

        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            val l = line.trim()
            if (l.isEmpty()) continue

            when {
                l.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val a = parseAttrs(l)
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"] ?: "")
                        .substringBefore(',').trim()
                }

                l.startsWith("#EXTINF", ignoreCase = true) -> {
                    val (head, title) = splitExtinf(l)
                    val a = parseAttrs(head)
                    val name = title.ifBlank { a["tvg-name"].orEmpty() }
                        .ifBlank { "Canal ${out.size + 1}" }
                    pending = Pending(
                        name = name,
                        logo = a["tvg-logo"].orEmpty(),
                        group = a["group-title"].orEmpty().trim(),
                        tvgId = a["tvg-id"].orEmpty().trim()
                    )
                }

                l.startsWith("#EXTGRP:", ignoreCase = true) ->
                    extGroup = l.substringAfter(':').trim()

                l.startsWith("#") -> Unit

                else -> {
                    val p = pending
                    if (p != null) {
                        out.add(
                            ParsedChannel(
                                name = p.name,
                                url = l,
                                logo = p.logo,
                                group = p.group.ifBlank { extGroup }.ifBlank { DEFAULT_GROUP },
                                tvgId = p.tvgId
                            )
                        )
                        pending = null
                        extGroup = ""
                    } else if (l.contains("://")) {
                        // URL suelta sin #EXTINF
                        out.add(
                            ParsedChannel(
                                name = l.substringBefore('?').substringAfterLast('/').ifBlank { l },
                                url = l,
                                logo = "",
                                group = extGroup.ifBlank { DEFAULT_GROUP },
                                tvgId = ""
                            )
                        )
                    }
                }
            }
        }
        return M3uResult(out, epgUrl)
    }

    private fun parseAttrs(s: String): Map<String, String> =
        attrRegex.findAll(s).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /** Separa "#EXTINF:-1 a=\"b\",Nombre" en (cabecera, nombre) ignorando comas dentro de comillas. */
    private fun splitExtinf(l: String): Pair<String, String> {
        var inQuotes = false
        for (i in l.indices) {
            val c = l[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) return l.substring(0, i) to l.substring(i + 1).trim()
        }
        return l to ""
    }
}

/**
 * Parser XMLTV en streaming (XmlPullParser). Solo conserva programas dentro de la ventana
 * [minStop, maxStart] y entrega lotes al `sink` para no llenar la memoria.
 */
class XmltvParser {
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(
        input: InputStream,
        playlistId: Long,
        minStop: Long,
        maxStart: Long,
        sink: (List<EpgEntity>) -> Unit
    ): Int {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        val batch = ArrayList<EpgEntity>(BATCH)
        var total = 0

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && p.name == "programme") {
                val start = parseTime(p.getAttributeValue(null, "start"))
                val stop = parseTime(p.getAttributeValue(null, "stop"))
                val channel = p.getAttributeValue(null, "channel").orEmpty()
                var title = ""
                var desc = ""
                val depth = p.depth
                while (true) {
                    event = p.next()
                    if (event == XmlPullParser.END_DOCUMENT) break
                    if (event == XmlPullParser.END_TAG && p.depth == depth) break
                    if (event == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "title" -> {
                                val t = p.nextText()
                                if (title.isEmpty()) title = t.trim()
                            }

                            "desc" -> {
                                val d = p.nextText()
                                if (desc.isEmpty()) desc = d.trim().take(400)
                            }
                        }
                    }
                }
                if (channel.isNotEmpty() && start > 0 && stop > start &&
                    stop >= minStop && start <= maxStart
                ) {
                    batch.add(
                        EpgEntity(
                            playlistId = playlistId,
                            channelKey = channel.lowercase(),
                            start = start,
                            stop = stop,
                            title = title,
                            description = desc
                        )
                    )
                    total++
                    if (batch.size >= BATCH) {
                        sink(ArrayList(batch))
                        batch.clear()
                    }
                }
            }
            event = p.next()
        }
        if (batch.isNotEmpty()) sink(ArrayList(batch))
        return total
    }

    /** "20241113120000 +0000" -> epoch ms. Sin zona horaria se asume UTC. */
    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        var s = raw.trim()
        s = when {
            s.length == 14 -> "$s +0000"
            s.length > 14 && s[14] != ' ' -> s.substring(0, 14) + " " + s.substring(14)
            else -> s
        }
        return try {
            fmt.parse(s)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private companion object {
        const val BATCH = 2000
    }
}
