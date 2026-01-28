package com.example.refocus.ui.minigame.games.romanizationquiz

import kotlin.math.abs
import kotlin.random.Random

private const val DEFAULT_HINT_COUNT = 4
private const val DEFAULT_MAX_QUESTIONS = 1

// 4 ヒントで論理的に推理できるよう，問題語のユニークトークン数に上限を設ける
private const val MAX_UNIQUE_TOKENS_IN_QUESTION = 9

internal data class RomanizationQuizSessionPlan(
    val pack: LanguagePack,
    val rounds: List<RomanizationQuizRound>,
    val timeLimitSeconds: Int,
)

internal data class RomanizationQuizRound(
    val hintPairs: List<RomanizationQuizHintPair>,
    val questionScript: String,
    val correctRoman: String,
    val options: List<String>,
    val correctIndex: Int,
)

internal data class RomanizationQuizHintPair(
    val script: String,
    val roman: String,
)

internal data class LanguagePack(
    val packId: String,
    val displayName: String,
    val direction: TextDirection,
    val romanizationStandard: String,
    val tokens: List<TokenPair>,
    val lexemes: List<Lexeme>,
) {
    private val tokenCandidates: List<TokenPair> = tokens.sortedByDescending { it.script.length }
    private val tokenIndex: Map<String, TokenPair> = tokens.associateBy { it.script }

    /**
     * scriptWord を tokens に分割する（greedy longest-match）
     *
     * - パック内で 1 対 1 のトークン対応を前提にするため，失敗時は null を返す
     */
    fun tokenize(scriptWord: String): List<String>? {
        if (scriptWord.isBlank()) return null
        var i = 0
        val out = mutableListOf<String>()
        while (i < scriptWord.length) {
            val match =
                tokenCandidates.firstOrNull { t ->
                    val end = i + t.script.length
                    end <= scriptWord.length && scriptWord.regionMatches(i, t.script, 0, t.script.length)
                } ?: return null
            out.add(match.script)
            i += match.script.length
        }
        return out
    }

    fun deriveRoman(tokens: List<String>): String? {
        if (tokens.isEmpty()) return null
        val romanTokens = tokens.map { tokenIndex[it]?.roman ?: return null }
        val joined = romanTokens.joinToString("")
        if (joined.isBlank()) return null
        return joined.replaceFirstChar { it.uppercaseChar() }
    }
}

internal enum class TextDirection {
    LTR,
    RTL,
}

internal data class TokenPair(
    val script: String,
    val roman: String,
)

internal data class Lexeme(
    val id: String,
    val scriptWord: String,
    val romanWord: String,
    val tokens: List<String>,
    val tags: Set<String> = emptySet(),
)

private const val TAG_PLACE = "place"
private const val TAG_WORD = "word"

internal fun generateRomanizationQuizSessionPlan(
    seed: Long,
    packs: List<LanguagePack>,
    timeLimitSeconds: Int,
    maxQuestions: Int = DEFAULT_MAX_QUESTIONS,
    hintCount: Int = DEFAULT_HINT_COUNT,
): RomanizationQuizSessionPlan {
    require(packs.isNotEmpty()) { "no language packs loaded" }

    val rng = Random(seed)
    val stablePacks = packs.sortedBy { it.packId }
    val pack = stablePacks[rng.nextInt(stablePacks.size)]

    val placeLexemes = pack.lexemes.filter { it.tags.contains(TAG_PLACE) }
    val wordLexemes = pack.lexemes.filter { it.tags.contains(TAG_WORD) }

    // 地名で十分なら地名のみを問題にする．地名が無い場合は全 lexeme を許可する
    val questionPoolBase = if (placeLexemes.isNotEmpty()) placeLexemes else pack.lexemes

    // 4 ヒントで推理可能なサイズに寄せる
    val questionLexemes =
        questionPoolBase.filter { it.tokens.distinct().size <= MAX_UNIQUE_TOKENS_IN_QUESTION }

    require(questionLexemes.isNotEmpty()) { "pack has no usable lexemes: ${pack.packId}" }

    // ヒントは地名を優先し，不足時に一般語を許可
    val hintPoolPreferred = if (placeLexemes.isNotEmpty()) placeLexemes else pack.lexemes
    val hintPoolFallback = if (wordLexemes.isNotEmpty()) wordLexemes else emptyList()

    val rounds = mutableListOf<RomanizationQuizRound>()
    val shuffledQuestions = questionLexemes.shuffled(rng).toMutableList()

    var guard = 0
    while (rounds.size < maxQuestions && guard < 800) {
        guard += 1

        val q = if (shuffledQuestions.isNotEmpty()) shuffledQuestions.removeAt(0) else questionLexemes.random(rng)

        val hints =
            buildHintsForQuestion(
                preferredPool = hintPoolPreferred,
                fallbackPool = hintPoolFallback,
                question = q,
                hintCount = hintCount,
                rng = rng,
            ) ?: continue

        val options = buildOptions(preferredPool = hintPoolPreferred, fallbackPool = hintPoolFallback, question = q, rng = rng)
            ?: continue

        val correctIndex = options.indexOf(q.romanWord)
        if (correctIndex < 0) continue

        rounds.add(
            RomanizationQuizRound(
                hintPairs = hints.map { RomanizationQuizHintPair(it.scriptWord, it.romanWord) },
                questionScript = q.scriptWord,
                correctRoman = q.romanWord,
                options = options,
                correctIndex = correctIndex,
            ),
        )
    }

    // フェイルセーフ：データが不足している場合でも UI が成立するようにする
    if (rounds.isEmpty()) {
        val q = questionLexemes.first()
        val options = listOf(q.romanWord, q.romanWord + "X", q.romanWord + "Y", q.romanWord + "Z").shuffled(rng)
        rounds.add(
            RomanizationQuizRound(
                hintPairs = emptyList(),
                questionScript = q.scriptWord,
                correctRoman = q.romanWord,
                options = options,
                correctIndex = options.indexOf(q.romanWord),
            ),
        )
    }

    return RomanizationQuizSessionPlan(
        pack = pack,
        rounds = rounds,
        timeLimitSeconds = timeLimitSeconds,
    )
}

private fun buildHintsForQuestion(
    preferredPool: List<Lexeme>,
    fallbackPool: List<Lexeme>,
    question: Lexeme,
    hintCount: Int,
    rng: Random,
): List<Lexeme>? {
    val qTokens = question.tokens.toSet()
    if (qTokens.isEmpty()) return null

    // 候補：質問に含まれるトークンを 1 つ以上含むものだけ（ヒントが必ず何かしら寄与する）
    fun candidateFilter(source: List<Lexeme>): List<Lexeme> {
        return source
            .asSequence()
            .filter { it.id != question.id }
            .filter { lex -> lex.tokens.any { qTokens.contains(it) } }
            .distinctBy { it.id }
            .toList()
    }

    val preferred = candidateFilter(preferredPool)
    val fallback = candidateFilter(fallbackPool)

    if (preferred.isEmpty() && fallback.isEmpty()) return null

    val remaining = qTokens.toMutableSet()
    val hints = mutableListOf<Lexeme>()
    val usedIds = mutableSetOf<String>()

    fun addHint(l: Lexeme) {
        if (usedIds.add(l.id)) hints.add(l)
    }

    // 1) set cover（最大 hintCount）
    var coverGuard = 0
    while (remaining.isNotEmpty() && coverGuard < 160 && hints.size < hintCount) {
        coverGuard += 1

        val candidates = (preferred + fallback).filterNot { usedIds.contains(it.id) }
        if (candidates.isEmpty()) break

        val scored =
            candidates
                .map { c ->
                    val cover = c.tokens.count { remaining.contains(it) }
                    c to cover
                }
                .filter { it.second > 0 }

        if (scored.isEmpty()) break

        val bestCover = scored.maxOf { it.second }
        val bestCandidates = scored.filter { it.second == bestCover }.map { it.first }

        // 同点なら地名（preferred）を優先し，それが無ければ全体から選ぶ
        val bestPreferred = bestCandidates.filter { it.tags.contains(TAG_PLACE) }
        val pool = if (bestPreferred.isNotEmpty()) bestPreferred else bestCandidates

        val chosen = pool.random(rng)
        addHint(chosen)
        chosen.tokens.forEach { remaining.remove(it) }
    }

    // 4 つで全トークンをカバーできなければ不採用
    if (remaining.isNotEmpty()) return null

    // 2) 4 つに寄せる（不足分を補う）
    //    - 追加も必ず質問トークンを含むものだけ
    //    - できるだけ「質問に無いトークン」を増やさないものを優先
    if (hints.size < hintCount) {
        val extras = (preferred + fallback).filterNot { usedIds.contains(it.id) }
        val extrasMutable = extras.toMutableList()

        while (hints.size < hintCount && extrasMutable.isNotEmpty()) {
            val scored =
                extrasMutable
                    .map { l ->
                        val shared = l.tokens.distinct().count { qTokens.contains(it) }
                        val noise = l.tokens.distinct().count { !qTokens.contains(it) }
                        Triple(l, shared, noise)
                    }
                    .filter { it.second > 0 }

            if (scored.isEmpty()) break

            // shared が多く，noise が少ないものを優先
            val bestShared = scored.maxOf { it.second }
            val topShared = scored.filter { it.second == bestShared }
            val bestNoise = topShared.minOf { it.third }
            val best = topShared.filter { it.third == bestNoise }.map { it.first }

            val bestPreferred = best.filter { it.tags.contains(TAG_PLACE) }
            val pool = if (bestPreferred.isNotEmpty()) bestPreferred else best

            val chosen = pool.random(rng)
            addHint(chosen)
            extrasMutable.removeAll { it.id == chosen.id }
        }
    }

    // 問題語がヒントに混ざると丸暗記ゲーになるので排除
    if (hints.any { it.scriptWord == question.scriptWord }) return null

    // 最終安全チェック（全トークンがヒント内に出現していること）
    val covered = hints.flatMap { it.tokens }.toSet()
    if (!covered.containsAll(qTokens)) return null

    // 4 個に満たない場合でも，すべてが有用なヒントであることを優先して許容する
    return hints.take(hintCount)
}

private fun buildOptions(
    preferredPool: List<Lexeme>,
    fallbackPool: List<Lexeme>,
    question: Lexeme,
    rng: Random,
): List<String>? {
    val correct = question.romanWord

    // デコイは地名を優先し，不足時に一般語を混ぜる
    val preferred = preferredPool.filter { it.id != question.id }
    val fallback = fallbackPool.filter { it.id != question.id }
    val pool = if (preferred.size >= 4) preferred else (preferred + fallback)

    val candidateRomans = pool.map { it.romanWord }.distinct().filter { it != correct }
    if (candidateRomans.size < 3) return null

    // 文字数が近いものを優先してデコイにする（見た目に紛らわしい四択を作る）
    val near =
        candidateRomans
            .sortedBy { abs(it.length - correct.length) }
            .take(48)
            .shuffled(rng)

    val decoys = near.take(3).toMutableList()
    if (decoys.size < 3) {
        val more =
            candidateRomans
                .shuffled(rng)
                .filterNot { decoys.contains(it) }
                .take(3 - decoys.size)
        decoys.addAll(more)
    }

    if (decoys.size != 3) return null

    val options = (decoys + correct).shuffled(rng)
    if (options.distinct().size != 4) return null
    return options
}
