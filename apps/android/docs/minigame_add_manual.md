# Refocus，ミニゲーム追加マニュアル

このドキュメントは，Refocus（Android）に新しいミニゲームを追加するための手順書です．
現状の実装は，ミニゲームの「起動タイミング」は domain が決め，ミニゲームの「中身（Composable）」は ui
レイヤに閉じ，system は overlay 表示など Android 固有の責務だけを担う構成になっています．

対象リポジトリ（本 zip 展開内容）

- `apps/android/app/src/main/java/com/example/refocus/core/model/MiniGameKind.kt`
- `apps/android/app/src/main/java/com/example/refocus/ui/minigame/` 以下
- `apps/android/app/src/main/java/com/example/refocus/system/overlay/MiniGameOverlayController.kt`
  （overlay へ載せる側）

---

## 1．ミニゲーム追加で守るべき設計ルール

### 1-1．レイヤ境界

ミニゲーム UI は `ui` レイヤにあります．そのため，ミニゲーム実装は次を守ってください．

- 参照してよいのは，`ui`，`core`，Kotlin，Compose です．
- `app`，`feature`，`system`，`data` への import は禁止です（`checkUiBoundaries` で検出されます）．
- domain の型やユースケースへ直接依存すると，後から設計が崩れやすいので避けます．必要なメタデータは
  `core` 側へ置きます．

関連

- `apps/android/docs/layers.md`
- `apps/android/app/build.gradle.kts` の `checkUiBoundaries`

### 1-2．「結果は返さない」原則

現状の設計では，ミニゲームの正誤やスコアは domain に返しません．
domain が受け取るのは「ユーザがミニゲームを閉じた」という事実だけです（
`MiniGameOverlayUiModel.onFinished`）．

- クリア条件，失敗条件，演出はゲーム内で完結させます．
- 永続化や統計への反映は，この段階では行いません（将来やるなら別設計）．

### 1-3．決定性（seed から同じ問題が出る）を強く推奨

domain はミニゲーム表示の失敗（WindowManager の一時的失敗など）に備えて，同じサイクル中は kind と seed
を固定します．
そのため，ゲームは「seed が同じなら同じ問題が生成される」ように実装するのが安全です．

実装上の指針

- 乱数は `Random(seed)` を `remember(seed)` で作る
- 問題生成は `remember(seed) { ... }` に閉じる
- `System.currentTimeMillis()` 等でゲーム内容を決めない（デバッグ用 seed の生成は別）

### 1-4．入力方法は「画面内 UI」で完結させる

ミニゲームは `TYPE_APPLICATION_OVERLAY` のフルスクリーン View で表示され，レイアウトフラグに
`FLAG_NOT_FOCUSABLE` が含まれます．
そのため，IME（ソフトキーボード）に頼ると端末や状況によって入力が不安定になります．

- 数字入力は `NumericKeypad` のような画面内キーパッドを推奨します．
- 文字入力が必要なゲームを作る場合も，可能なら画面内キーボードを用意してください．

### 1-5．必ず「閉じる導線」を用意する

- クリア，時間切れ，キャンセルのいずれでも，最終的に `onFinished()` に到達できる UI を用意してください．
- 背景タップは閉じません（`MiniGameHostOverlay` が吸収します）．「閉じる」ボタンなどを必須にしてください．

### 1-6．Activity 依存の Compose API に注意（overlay 表示では owner が存在しない）

ミニゲームは `WindowManager.addView(ComposeView)` による `TYPE_APPLICATION_OVERLAY` として表示されます．
この表示経路では Activity 配下の Compose と違い，Activity が提供する各種 owner（戻るボタンの
dispatcher など）が `CompositionLocal` として自動提供されません．
そのため，Activity 依存 API を無条件に呼ぶと，実行時に例外が投げられ，アプリがクラッシュすることがあります．

特に注意が必要な例

- `androidx.activity.compose.BackHandler`（`OnBackPressedDispatcherOwner` が必要）
- `rememberLauncherForActivityResult`（`ActivityResultRegistryOwner` が必要）
- `viewModel()` / `hiltViewModel()` など（`ViewModelStoreOwner` が必要）

対応方針

- 原則として，ミニゲームからは上記の Activity 依存 API を使わず，画面内 UI で完結させます．
- どうしても必要な場合は，owner が存在する場合だけ機能を有効にします（存在しない場合は登録しない）．

例（BackHandler を安全に使う）

```kotlin
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner

val owner = LocalOnBackPressedDispatcherOwner.current
if (owner != null) {
    BackHandler(enabled = true) {
        // Back を消費したい場合の処理
    }
}
```

補足

- overlay は `FLAG_NOT_FOCUSABLE` を含むため，端末や状況によっては Back キーイベント自体がミニゲームに届きません．
- Back で閉じる設計に依存せず，必ず画面内ボタンで完結する導線を用意してください（1-5 と同様）．

### 1-7．共通開始画面（Intro）との整合を取る

Refocus のミニゲームは，ホスト側（`MiniGameHostOverlay`）が共通の開始画面（Intro）を表示し，ユーザが「開始」を押した後にだけゲーム本体の
Composable を Compose する設計です．
この設計により，「説明を読んでいる間にタイマーが進む」「問題表示が先に始まる」といった事故を構造的に防ぎます．

追加するゲームは，次を守ってください．

- ゲーム本体に「開始ボタン」や `Ready` 画面を置かないことを推奨します（開始操作は Intro に一本化します）．
- 説明文，操作ルール，制限時間の表示は `MiniGameDescriptor` のメタ情報（`description`，`rules`，
  `timeLimitSeconds` など）へ集約してください．ゲーム本体の UI に重複して書くと，文言の不整合が起きやすくなります．
- タイマーや `LaunchedEffect` による進行は「ゲーム Composable が Compose
  されてから」始めれば十分です．ホストが未開始状態ではゲームを Compose しないため，説明中に進行しません．

補足

- どうしても開始直後に「準備」演出を入れたい場合は，ゲーム内で短い導入フェーズ（例:
  0.5〜1秒のカウントイン）を持つのは許容できますが，ユーザ操作としての開始ボタンは置かないことを推奨します．

---

## 2．現状の実装ポイント（どこを触るか）

### 2-1．追加する種類（Kind）

- ファイル: `core/model/MiniGameKind.kt`
- 役割: ミニゲームの種類の列挙子（domain が扱う識別子）

例（既存）

- `FlashAnzan`
- `MakeTen`

この enum に追加すると，domain 側のランダム選択（`MiniGameKind.entries`）に自動的に含まれます．

### 2-2．system 側のレジストリ（Kind -> 実装）

- ファイル: `ui/minigame/catalog/MiniGameRegistry.kt`
- 役割: 実装済みゲームを `MiniGameEntry` として列挙し，kind から解決する

新規ゲームを追加したら，ここへ `entry` を追加します．

### 2-3．ゲームの置き場所

既存の実装は以下にあります．

- `ui/minigame/games/flashanzan/`
    - `Entry.kt`
    - `Game.kt`
- `ui/minigame/games/maketen/`
    - `Entry.kt`
    - `Game.kt`
    - `Problems.kt`（問題データ読み込み）

新規ゲームも同様に，`games/<gameId>/` 配下にまとめるのを推奨します．

---

## 3．追加手順（最短ルート）

### 手順0．ブランチ作成とビルド確認

作業前に，現状がビルドできることを確認します．

```bash
cd apps/android
./gradlew :app:assembleDebug
```

### 手順1．`MiniGameKind` に列挙子を追加する

`apps/android/app/src/main/java/com/example/refocus/core/model/MiniGameKind.kt` に新しい種類を追加します．

例

```kotlin
enum class MiniGameKind {
    FlashAnzan,
    MakeTen,
    NewPuzzle, // 追加
}
```

注意

- enum 名は Kotlin の慣例に合わせて `UpperCamelCase` を推奨します．
- 将来的に kind を永続化するようになった場合，enum の rename は互換性に影響します．その段階では移行戦略を用意してください．

### 手順2．ゲーム用パッケージを作る

`apps/android/app/src/main/java/com/example/refocus/ui/minigame/games/<gameId>/` を作ります．
例: `games/numbermemory/`，`games/quicktap/` など

最低限，次の2ファイルを用意します．

- `Entry.kt`（レジストリ登録用）
- `Game.kt`（Composable 本体）

### 手順3．`Entry.kt` を実装する（カタログ登録の最小単位）

テンプレート

```kotlin
package com.example.refocus.ui.minigame.games.<gameId >

    import com . example . refocus . core . model . MiniGameKind
    import com . example . refocus . ui . minigame . catalog . MiniGameDescriptor
    import com . example . refocus . ui . minigame . catalog . MiniGameEntry

    internal
val <gameId> Entry: MiniGameEntry =
    MiniGameEntry(
        descriptor =
            MiniGameDescriptor(
                kind = MiniGameKind.< NewKind >,
                title = "<表示名>",
                description = "<一行説明>",
                rules = listOf(
                    "<操作ルール1>",
                    "<操作ルール2>",
                ),
                timeLimitSeconds = null, // 制限時間がある場合は秒で指定（例: 60）
                primaryActionLabel = "開始",
                canSkipBeforeStart = true,
            ),
        content = { seed, onFinished, modifier ->
            Game(
                seed = seed,
                onFinished = onFinished,
                modifier = modifier,
            )
        },
    )
```

ポイント

- `internal val ...Entry` にして，レジストリ以外からの参照を最小化します（既存と同様）．
- `title` と `description` はデバッグ UI の一覧に出ます（`feature/customize/BasicCustomizeContent.kt`
  が `MiniGameRegistry.descriptors` を表示）．
- `rules` や `timeLimitSeconds` は共通開始画面（Intro）で表示されます．ゲーム内に同じ説明を重複させるより，カタログ側へ寄せることを推奨します．

### 手順4．`Game.kt` を実装する（Composable 本体）

#### 4-1．最小構成テンプレート

```kotlin
package com.example.refocus.ui.minigame.games.<gameId >

    import androidx . compose . foundation . layout . Column
    import androidx . compose . foundation . layout . Spacer
    import androidx . compose . foundation . layout . fillMaxSize
    import androidx . compose . foundation . layout . fillMaxWidth
    import androidx . compose . foundation . layout . height
    import androidx . compose . runtime . Composable
    import androidx . compose . runtime . getValue
    import androidx . compose . runtime . mutableStateOf
    import androidx . compose . runtime . remember
    import androidx . compose . runtime . setValue
    import androidx . compose . ui . Modifier
    import androidx . compose . ui . unit . dp
    import androidx . compose . material3 . Button
    import androidx . compose . material3 . Text
    import kotlin . random . Random

    @Composable
    fun Game(
        seed: Long,
        onFinished: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val rng = remember(seed) { Random(seed) }
        val problem = remember(seed) { rng.nextInt(1, 100) }

        var phase by remember(seed) { mutableStateOf(Phase.Playing) }

        Column(modifier = modifier.fillMaxSize()) {
            Text(text = "問題: $problem")

            Spacer(Modifier.weight(1f))

            when (phase) {
                Phase.Playing -> {
                    Button(
                        onClick = { phase = Phase.Result },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("決定") }
                }
                Phase.Result -> {
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("閉じる") }
                }
            }
        }
    }

private enum class Phase {
    Playing,
    Result,
}
```

#### 4-2．推奨実装パターン

- 状態は `remember(seed)` を基本にします（ゲーム開始時に seed が変わると初期化される）．
- 時間制限がある場合は `LaunchedEffect(seed)` か `LaunchedEffect(phase)` を使い，フェーズが変わったら自然に止まるようにします．
    - `while(true)` を回す場合は，必ずフェーズや残り秒で break します（`maketen/Game.kt` の実装が参考になります）．
- 画面は `MiniGameFrame` の中で表示されるため，`modifier.fillMaxSize()` の範囲に収めれば OK です．

#### 4-3．共通 UI の利用

- 数字入力が必要なら `ui/minigame/components/NumericKeypad.kt` を使えます．
- 似た UI を複数ゲームで使う見込みがあるなら，ゲーム固有パッケージに閉じず `components/` に切り出してください．

### 手順5．`MiniGameRegistry` にエントリを追加する

`apps/android/app/src/main/java/com/example/refocus/ui/minigame/catalog/MiniGameRegistry.kt` の
`entries` に追加します．

```kotlin
val entries: List<MiniGameEntry> =
    listOf(
        flashAnzanEntry,
        makeTenEntry,
        < gameId > Entry, // 追加
)
```

ポイント

- `init` で kind 重複チェックが走るため，追加直後に duplicate があるとすぐ分かります．

### 手順6．必要ならリソースを追加する（問題データ，画像など）

例（問題リストを raw で持つ）

- 置き場所: `apps/android/app/src/main/res/raw/`
- 参照: `context.resources.openRawResource(R.raw.<fileName>)`

既存例

- `res/raw/make_ten_problems.txt`
- `games/maketen/Problems.kt`

注意

- overlay 表示は頻繁に起動され得るため，大きなファイルを毎回読み込むのは避けます．
    - `remember` とキャッシュを使う
    - 必要なら `lazy` などでメモ化する
    - ただし，極端なメモリ常駐は避ける

### 手順7．動作確認（アプリ内デバッグ起動）

Debug ビルドでは，カスタマイズ画面に「ミニゲームのテスト」が出ます．

注意

- ここは feature（設定画面）側の実装なので，ミニゲーム UI は
  `com.example.refocus.ui.minigame.MiniGameHostOverlay` を import します．
  `com.example.refocus.system.*` を参照すると境界違反になります．

- 画面: 設定（カスタマイズ） -> ミニゲーム -> ミニゲームのテスト
- 一覧は `MiniGameRegistry.descriptors` から生成されるため，レジストリに追加できていれば表示されます．

表示できない場合

- `MiniGameRegistry.entries` に追加し忘れ
- `Entry.kt` の import／パッケージ名ミス
- kind の重複で `MiniGameRegistry` の init チェックに失敗

### 手順8．ビルド，静的チェック

最低限，次を通します．

```bash
cd apps/android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew checkUiBoundaries
./gradlew checkFeatureBoundaries
```

---

## 4．品質ガイド（ミニゲーム実装のチェックリスト）

### 4-1．UX

- 3分以上の長いゲームにしない（ナッジとしての「切り替え」目的に合わない）．
- 途中で迷子にならないように，フェーズを明確にする（例: Playing，Result）．
- 開始前の説明と開始操作は共通開始画面（Intro）側で提供されるため，ゲーム内に開始ボタンを置かないことを推奨します．
- 結果表示から必ず「閉じる」に進める．
- 小さい端末でも押せるボタンサイズを保つ（既存は `height(52.dp)` を基準にしています）．

### 4-2．堅牢性

- 例外が起きてもアプリ全体が落ちないようにする（特にリソース読み込み）．
- `LaunchedEffect` のループはフェーズ変更で自然に止める．
- 重い処理（大量の計算，画像の巨大デコードなど）を UI スレッドで行わない．必要なら問題を事前生成して
  `remember` に閉じる．

### 4-3．依存と配置

- `ui` レイヤから `feature`，`data`，`app`，`system` への import がないことを確認する．
- ゲーム固有のファイルは `games/<gameId>/` に寄せる．
- 複数ゲームで使う UI は `components/` に切り出す．

### 4-4．決定性

- `seed` が同じなら問題が同じになることを確認する．
- seed 以外の外部状態（時刻，乱数のグローバル状態）で問題を決めない．

---

## 5．よくある落とし穴と対処

### 5-1．ミニゲームが出ない，すぐ消える

- overlay 権限が無いと，WindowManager への addView が失敗し，domain からは「表示失敗」として扱われます．
- ただしデバッグの「ミニゲームのテスト」は通常の画面（Dialog）内表示なので，権限がなくても確認できます．
    - まずデバッグ起動で UI 自体を確認し，次に overlay 権限を付与して実運用経路で確認します．

### 5-2．タップが効かない

- 背景はタップ吸収しますが，カード内は操作できるはずです．
- もし全体が反応しない場合は，Composable の上にクリック吸収レイヤを置いていないかを確認します．

### 5-3．ビルドで `checkUiBoundaries` が落ちる

- `ui` から `com.example.refocus.feature.` や `com.example.refocus.system.`，
  `com.example.refocus.data.` を import していないか確認します．
- 特に，feature からミニゲームを呼び出す場合は，`com.example.refocus.ui.minigame.*` を参照し，
  `com.example.refocus.system.*` を import しないでください（`checkFeatureBoundaries` で検出されます）．
- 設定値が欲しい場合は，domain から注入される Port を増やすのが原則です（短絡的に DataStore
  を直接読むのは避ける）．

---

## 6．拡張（任意，必要になったら）

### 6-1．ミニゲーム選択ロジックを変更したい（重み付けなど）

現状は `domain/overlay/orchestration/SuggestionOrchestrator.kt` の `pickRandomMiniGameKind(seed)` が
`MiniGameKind.entries` を等確率で選びます．
重み付けや「直近で出たものを避ける」などを入れたい場合は，ここを拡張します．

注意

- domain は ui のレジストリを参照できません．そのため，重み情報を domain が知る必要がある場合は，`core`
  側に「メタデータ」を置くなど，設計を一段階見直してください．

### 6-2．ゲームごとの設定を追加したい

難易度や制限時間をユーザ設定にしたい場合，変更範囲が広くなります．

- `core/model/Customize.kt` と `CustomizeDefaults.kt` の拡張
- `data/datastore/SettingsDataStore.kt` の永続化
- `feature/customize` の UI 追加
- domain から system へ設定値を渡す経路（Port／DI）整理

この段階のコストが高い場合は，まずゲーム内で固定値にし，後から設定を外出しするのが安全です．
