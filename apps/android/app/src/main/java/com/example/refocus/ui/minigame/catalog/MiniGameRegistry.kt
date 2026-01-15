package com.example.refocus.ui.minigame.catalog

import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.ui.minigame.games.eightpuzzle.eightPuzzleEntry
import com.example.refocus.ui.minigame.games.flashanzan.flashAnzanEntry
import com.example.refocus.ui.minigame.games.maketen.makeTenEntry
import com.example.refocus.ui.minigame.games.mirrortext.mirrorTextEntry

/**
 * 実装済みのミニゲームを列挙するレジストリ。
 *
 * 注意
 * - ui 側の UI 実装（Composable）を保持するため，domain からは参照しないこと。
 * - 新しいミニゲームを追加したら，ここにエントリを追加する。
 */
object MiniGameRegistry {
    val entries: List<MiniGameEntry> =
        listOf(
            flashAnzanEntry,
            makeTenEntry,
            eightPuzzleEntry,
            mirrorTextEntry,
        )

    private val byKind: Map<MiniGameKind, MiniGameEntry> =
        entries.associateBy { it.descriptor.kind }

    init {
        check(byKind.size == entries.size) {
            val duplicates =
                entries
                    .groupBy { it.descriptor.kind }
                    .filterValues { it.size >= 2 }
                    .keys
            "MiniGameRegistry contains duplicate kinds: $duplicates"
        }
    }

    val descriptors: List<MiniGameDescriptor>
        get() = entries.map { it.descriptor }

    fun resolve(kind: MiniGameKind): MiniGameEntry? = byKind[kind]
}
