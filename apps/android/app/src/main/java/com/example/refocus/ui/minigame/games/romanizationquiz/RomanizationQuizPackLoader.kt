package com.example.refocus.ui.minigame.games.romanizationquiz

import android.content.res.AssetManager
import java.io.BufferedReader

private const val PACKS_ROOT = "romanizationquiz/packs"

internal object RomanizationQuizPackLoader {
    fun loadAllPacks(assets: AssetManager): List<LanguagePack> {
        val ids = listPackIds(assets)
        if (ids.isEmpty()) return emptyList()

        // packId の並びが端末依存にならないように必ずソートする
        return ids.mapNotNull { id ->
            runCatching { loadPack(assets, id) }.getOrNull()
        }
    }

    fun listPackIds(assets: AssetManager): List<String> {
        val listed = assets.list(PACKS_ROOT)?.toList().orEmpty()
        return listed.filter { it.isNotBlank() }.sorted()
    }

    fun loadPack(assets: AssetManager, packId: String): LanguagePack {
        val meta = readKeyValueTsv(assets, "$PACKS_ROOT/$packId/meta.tsv")
        val displayName = meta["displayName"]?.takeIf { it.isNotBlank() } ?: packId
        val scheme = meta["scheme"]?.takeIf { it.isNotBlank() } ?: ""
        val direction = when (meta["direction"]?.trim()?.uppercase()) {
            "RTL" -> TextDirection.RTL
            else -> TextDirection.LTR
        }

        val allowedCountriesIso2 = parseIso2Set(meta["allowedCountriesIso2"] ?: meta["allowedCountries"]) // 任意
        val validateRomanAgainstTokens = meta["validateRomanAgainstTokens"]?.trim()?.lowercase() == "true" // デフォルト false

        val tokens =
            readTwoColumnTsv(assets, "$PACKS_ROOT/$packId/tokens.tsv").map {
                TokenPair(it.first, it.second)
            }
        require(tokens.isNotEmpty()) { "tokens.tsv is empty: $packId" }

        val base =
            LanguagePack(
                packId = packId,
                displayName = displayName,
                direction = direction,
                romanizationStandard = scheme,
                tokens = tokens,
                lexemes = emptyList(),
            )

        val rawLexemes = readLexemesTsv(assets, "$PACKS_ROOT/$packId/lexemes.tsv")

        val lexemes =
            rawLexemes
                .asSequence()
                .mapNotNull { row ->
                    val scriptWord = row.scriptWord
                    if (scriptWord.isBlank()) return@mapNotNull null

                    // 国フィルタが有効なら，countryIso2 が無い行は除外する
                    val iso2 = row.countryIso2?.uppercase()
                    if (allowedCountriesIso2.isNotEmpty()) {
                        if (iso2 == null) return@mapNotNull null
                        if (!allowedCountriesIso2.contains(iso2)) return@mapNotNull null
                    }

                    // tokenization はスペースやハイフンを除去した表記で行う（表示は元の文字列）
                    val normalizedScript = normalizeScriptForTokenization(scriptWord)
                    if (normalizedScript.isBlank()) return@mapNotNull null

                    val toks = base.tokenize(normalizedScript) ?: return@mapNotNull null
                    // 「一文字対応ヒント」を回避するため，単体トークン語は採用しない
                    if (toks.size < 2) return@mapNotNull null

                    val derived = base.deriveRoman(toks)

                    val romanWord =
                        row.romanWord?.takeIf { it.isNotBlank() }
                            ?: derived
                            ?: return@mapNotNull null

                    if (validateRomanAgainstTokens && row.romanWord != null) {
                        // TSV の roman を採用する場合でも，機械導出と一致するものだけに絞る（任意）
                        if (derived == null) return@mapNotNull null
                        if (!romanEquals(derived, romanWord)) return@mapNotNull null
                    }

                    val tag = row.tag.ifBlank { "place" }
                    val id = row.qid?.takeIf { it.isNotBlank() } ?: "$tag:$scriptWord"

                    Lexeme(
                        id = id,
                        scriptWord = scriptWord,
                        romanWord = romanWord,
                        tokens = toks,
                        tags = setOf(tag),
                    )
                }
                .distinctBy { it.id }
                // seed 決定性のため，ファイル順に依存しないようにソート
                .sortedBy { it.scriptWord }
                .toList()

        require(lexemes.isNotEmpty()) { "lexemes.tsv has no usable rows: $packId" }

        return base.copy(romanizationStandard = scheme.ifBlank { meta["source"].orEmpty() }, lexemes = lexemes)
    }
}

private data class LexemeRow(
    val qid: String?,
    val scriptWord: String,
    val romanWord: String?,
    val tag: String,
    val countryIso2: String?,
)

private fun readKeyValueTsv(assets: AssetManager, path: String): Map<String, String> {
    assets.open(path).bufferedReader(Charsets.UTF_8).use { br ->
        val out = mutableMapOf<String, String>()
        br.forEachTsvLine { cols ->
            if (cols.size < 2) return@forEachTsvLine
            val key = cols[0]
            val value = cols.drop(1).joinToString("\t")
            if (key.isNotBlank()) out[key] = value
        }
        return out
    }
}

private fun readTwoColumnTsv(assets: AssetManager, path: String): List<Pair<String, String>> {
    assets.open(path).bufferedReader(Charsets.UTF_8).use { br ->
        val out = mutableListOf<Pair<String, String>>()
        var lineNo = 0
        for (raw0 in br.lineSequence()) {
            lineNo += 1
            val raw = if (lineNo == 1) raw0.trimStart('\uFEFF') else raw0
            val line = raw.trimEnd('\r') // CRLF 対策，ただし \t は消さない
            if (line.isBlank()) continue
            if (line.trimStart().startsWith("#")) continue

            val tab = line.indexOf('\t')
            val a = if (tab >= 0) line.substring(0, tab).trim() else line.trim()
            val b = if (tab >= 0) line.substring(tab + 1).trim() else "" // 2列目は無くても空扱い
            if (a.isBlank()) continue

            out.add(a to b) // b は空でも許可
        }
        return out
    }
}

/**
 * lexemes.tsv のスキーマ（後方互換あり）
 *
 * 推奨:
 * - scriptWord<TAB>romanWord<TAB>tag<TAB>countryIso2
 * - qid<TAB>scriptWord<TAB>romanWord<TAB>tag<TAB>countryIso2
 *
 * 後方互換:
 * - scriptWord<TAB>tag
 */
private fun readLexemesTsv(assets: AssetManager, path: String): List<LexemeRow> {
    assets.open(path).bufferedReader(Charsets.UTF_8).use { br ->
        val out = mutableListOf<LexemeRow>()
        br.forEachTsvLine { cols0 ->
            if (cols0.isEmpty()) return@forEachTsvLine

            var cols = cols0
            val first = cols.firstOrNull().orEmpty()
            val hasQid = first.matches(Regex("Q\\d+"))
            val qid = if (hasQid) first else null
            if (hasQid) cols = cols.drop(1)
            if (cols.isEmpty()) return@forEachTsvLine

            val script = cols[0]
            if (script.isBlank()) return@forEachTsvLine

            var roman: String? = null
            var tag = "place"
            var country: String? = null

            if (cols.size == 2) {
                val second = cols[1]
                // 2 列の場合は legacy（script, tag）を優先
                if (second == "place" || second == "word") {
                    tag = second
                } else {
                    roman = second
                }
            } else if (cols.size >= 3) {
                roman = cols[1]

                // 3 列目が ISO2 っぽい場合は country とみなし tag は default
                val third = cols[2]
                val thirdIsIso2 = third.length == 2 && third.all { it.isLetter() }
                if (thirdIsIso2) {
                    country = third
                } else {
                    tag = third.takeIf { it.isNotBlank() } ?: "place"
                }

                if (cols.size >= 4) {
                    // 4 列目は countryIso2 として扱う（推奨形式）
                    country = cols[3].takeIf { it.isNotBlank() } ?: country
                }
            }

            out.add(LexemeRow(qid = qid, scriptWord = script, romanWord = roman, tag = tag, countryIso2 = country))
        }
        return out
    }
}

private inline fun BufferedReader.forEachTsvLine(block: (List<String>) -> Unit) {
    var lineNo = 0
    for (raw in this.lineSequence()) {
        lineNo += 1
        val line = raw.trimEnd()
        if (line.isBlank()) continue
        val trimmed = line.trimStart()
        if (trimmed.startsWith("#")) continue

        val cols =
            line
                .split('\t')
                .mapIndexed { idx, s ->
                    if (lineNo == 1 && idx == 0) s.trimStart('\uFEFF') else s
                }
                .map { it.trim() }
        if (cols.isEmpty()) continue
        block(cols)
    }
}

private fun parseIso2Set(value: String?): Set<String> {
    if (value.isNullOrBlank()) return emptySet()
    return value
        .split(',', ';', ' ', '\t', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.uppercase() }
        .filter { it.length == 2 && it.all { ch -> ch.isLetter() } }
        .toSet()
}

private fun normalizeScriptForTokenization(scriptWord: String): String {
    // 表示は元の文字列を使い，tokenize は区切り文字を落とした形で行う
    // （例: "САНКТ-ПЕТЕРБУРГ" → "САНКТПЕТЕРБУРГ"）
    val dropChars = setOf('-', '‐', '‑', '–', '—')
    return scriptWord.filterNot { it.isWhitespace() || dropChars.contains(it) }
}

private fun romanEquals(a: String, b: String): Boolean {
    fun norm(s: String): String =
        s.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    return norm(a) == norm(b)
}
