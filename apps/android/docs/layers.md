# レイヤ構成と依存ルール

## 目的

Refocus は，後から機能を増やしても設計が崩れないように，コードをレイヤごとに分離して扱う．
このドキュメントは，「どこに何を書くか」と「どこへ依存して良いか」を明確にし，将来のマルチモジュール化に備える．

## レイヤ一覧

| レイヤ | 役割 | 依存して良い範囲 |
| --- | --- | --- |
| domain | ビジネスルール，ユースケース，純粋な計算，イベント再解釈 | core，kotlin 標準，外部ライブラリ（Android 依存を除く），domain 内 |
| data | 永続化（Room，DataStore），データ変換 | core，domain，kotlin，Android（ただし UI や system の都合に依存しない） |
| system | Android 実装（Service，Receiver，通知，UsageStats，WindowManager） | domain，core，kotlin，Android |
| feature | 画面実装（Compose Screen，ViewModel），ユーザ操作 | domain，core，ui，gateway |
| ui | 共通 UI 部品（theme，コンポーネント） | core，kotlin，Compose |
| app | DI，navigation，起動，画面統合 | domain，feature，system，data，gateway，ui |
| gateway | feature が Android 型を扱うための interface 群 | core，kotlin，Android（型参照のみ） |
| core | 共有モデル，ユーティリティ，値オブジェクト | kotlin，外部ライブラリ（必要最小限） |

## package と役割の対応

- `com.example.refocus.domain.*`  
  ユースケース，オーケストレーション，純粋ロジック．Android API を直接参照しない．

- `com.example.refocus.domain.*.port`  
  domain が外の世界へ依頼するための interface（入出力の境界）．
  実装は system または data に置き，DI で注入する．

- `com.example.refocus.data.*`  
  DB，DataStore，Repository 実装，Mapper．

- `com.example.refocus.system.*`  
  Android 実装．Service，Receiver，通知，オーバーレイ，前面アプリ監視など．

- `com.example.refocus.feature.*`  
  画面と ViewModel．ユースケースを呼び出して状態を組み立てる．

- `com.example.refocus.ui.*`  
  画面横断の UI 部品と theme．

- `com.example.refocus.app.*`  
  依存配線（DI），ナビゲーション，アプリ全体の統合．

- `com.example.refocus.gateway.*`  
  feature が Android 型を含む操作を抽象化するための interface 群．

## 命名規約

- domain 側の境界は `*Port` とする．例: `OverlayUiPort`．
- system 側の実装は `*Impl` か，役割が明確なら `*Controller`，`*Adapter` を使う．
- `Gateway` という語は，feature が参照する Android 型込みの interface 群（`com.example.refocus.gateway`）に限定する．

## 現状の自動チェック

`apps/android/app/build.gradle.kts` に import ベースの軽量ガードを置いている．

- `checkDomainBoundaries`
- `checkFeatureBoundaries`
- `checkSystemBoundaries`
- `checkDataBoundaries`
- `checkGatewayBoundaries`
- `checkUiBoundaries`

この検査は import 文のみを対象とするため，FQCN 直書きなどは検知できない．
ただし，日常的な逸脱の早期検知としては十分に有効である．

## 将来のマルチモジュール化案

将来の分割候補は以下の通り．

- `:core`
- `:domain`
- `:data`
- `:system`
- `:ui`
- `:feature:*`（screen 単位や領域単位）
- `:app`

このとき，`domain` は Android 依存を持たない純 Kotlin モジュールにできる．
