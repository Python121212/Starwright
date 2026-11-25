# Starwright — 宇宙レイヤー設計

## 受けた仕様（構造化）

| # | 要件 | 対応箇所 |
| --- | --- | --- |
| 1 | まず宇宙を追加する | `src/main/java/.../space/` |
| 2 | **Y = 5,000 から宇宙にする** | `SpaceTuning.SPACE_START_Y = 5000` |
| 3 | それまでに成層圏など、少しずつ暗くなり、見える星が増えていく | `SpaceStage` 6 段階 ＋ `darkness()` / `starVisibility()` の単調増加 |
| 4 | **エリトラでは行けない** | `ELYTRA_CUTOFF_Y = 1000` 以上で `stopFallFlying()` |
| 5 | **宇宙は見た目ではない** | サーバー側で `setNoGravity` を適用するなど、実際の物理状態を変更 |
| 6 | **ネザーのようなディメンション読み込みなしでシームレス** | `ServerLevel` 生成／`changeDimension`／ローディング画面を一切使わない |
| 7 | **地上の描画は宇宙にいる、または地上が見えなくなったら全く行わない** | `shouldSkipGroundRendering()` をクライアント側の判定として用意 |
| 8 | **ただしサーバーはまた別** | `!level.isClientSide` と `Dist.CLIENT` で明示分離。サーバーは地上を止めない |

## ステージ境界

| Y | ステージ | 意味 | 暗さ | 星 | 重力 |
| --- | --- | --- | --- | --- | --- |
| 0 | `GROUND` | 地上 | 0.00 | 0.00 | 1.00 |
| 1–799 | `TROPOSPHERE` | 対流圏 | 徐々に | ほぼ 0 | ほぼ 1 |
| 800–1,999 | `STRATOSPHERE` | 成層圏 | ↑ | ↑ | ↓ |
| 2,000–3,199 | `MESOSPHERE` | 中間圏 | ↑ | ↑ | ↓ |
| 3,200–4,199 | `THERMOSPHERE` | 熱圏 | ↑ | ↑ | ↓ |
| 4,200–4,999 | `EXOSPHERE` | 外気圏 | ↑ | ↑ | ↓ |
| **≥ 5,000** | `SPACE` | **宇宙** | 1.00 | 1.00 | 0.05 |

数式（`t = clamp(y / 5000, 0, 1)`）:

```
darkness        = sqrt(t)      // 早めに暗くなる
starVisibility  = t ^ 1.8      // 星は後から増える
gravityScale    = 1 - 0.95 * t // 宇宙でも 0 にはしない
```

3 つとも単調で、`SpaceRulesTest` が 250 刻みで単調性を検証しています。

## シームレスであることの担保

- ディメンションを **1 つも追加しない**（`data/*/dimension/**` を作らない）
- テレポート、`ServerLevel` の生成、`changeDimension`、ロード画面を一切使わない
- `SpaceRules` は `Level` を読まず、**Y 座標だけ**を見る（Minecraft 非依存の純粋 Java）
- `SpaceState.dimensionId()` は常に `minecraft:overworld`
- テスト `stateAlwaysStaysInTheOverworld` で固定

つまり「ネザーに行く」のではなく、**同じオーバーワールドの中で上に上がっていく**だけです。

## クライアント / サーバーの分離

| | クライアント | サーバー |
| --- | --- | --- |
| クラス | `SpaceClientState`（`Dist.CLIENT`） | `SpaceForgeHooks`（`!isClientSide`） |
| 役割 | `darkness` / `starVisibility` / `skipGroundRendering` を保持 | ステージ判定・エリトラ停止・無重力・ステージ変化ログ |
| 地上 | **描画を止める**（適用は Step 4） | **止めない**。チャンク tick・当たり判定・他プレイヤーを維持 |

`SpaceForgeHooks` と `SpaceClientState` は `@Mod.EventBusSubscriber` で FML が自動登録するため、
`Starwright` 本体はこれらを一切参照しません。これにより jar エミュレータは従来どおり
`Starwright` だけを隔離ロードできます。

## 実装状況とロードマップ

| Step | 内容 | 状態 |
| --- | --- | --- |
| 1 | ステージ判定・設定・エリトラ禁止・無重力・クライアント状態・テスト | ✅ **今回** |
| 2 | 空を段階的に暗くする（`FogEvents` / `RegisterDimensionSpecialEffectsEvent`） | ⏳ |
| 3 | 見える星を増やす（`DimensionSpecialEffects#renderSky` の差し替え） | ⏳ |
| 4 | 地上描画の完全カット（`LevelRenderer` Mixin ＋ refmap） | ⏳ |
| 5 | Y=5,000 への**連続移動**手段（建築限界バニラ維持） | ✅ **今回** |
| 6 | 酸素・気圧・モブスポーン抑制・音の減衰など | ⏳ |

Step 2〜4 はクライアント描画に深入りするため、Mixin あるいは Forge の
クライアント拡張ポイントの追加が必要です。判定側は完成しているので、
描画側を足すだけの状態です。Step 5 は今回実装しました（下記）。

## ✅ 決定事項（ユーザー承認）

**建築限界高度はバニラのまま変更しません。**

- `data/minecraft/dimension_type/overworld.json` を**作らない**
- `data/*/dimension/**` も作らない（ディメンション追加なし）
- `min_y = -64` / `height = 384` / `logical_height = 384` / 最大 Y = 319 を維持
- `SpaceTuning.SPACE_START_Y = 5,000` は不変
- Y=5,000 は**建築限界の外側にあるエンティティ座標**として扱う（チャンクは存在しない）
- 到達は `changeDimension` / `/tp` ではなく**連続移動**（Step 5 = 実装済み）

この決定は `SpaceRulesTest#buildLimitStaysVanilla` と
`StarwrightJarTest#noDimensionTypeOverride` で固定しています。

## ⚠️ 建築限界と「プレイヤーが居られる高さ」は別（重要）

### これは建築限界高度の話か？

**はい。** ただし正確には `DimensionType` の次の値です:

| 値 | 1.20.1 既定 | 意味 |
| --- | --- | --- |
| `min_y` | `-64` | 下端 |
| `height` | `384` | チャンクの実データ高さ（24 セクション） |
| `logical_height` | `384` | 論理的上限（`height` 以下でないとエラー） |
| `getMaxBuildHeight()` | `320` (= `-64 + 384`) | ブロックを置ける上限 → **最大 Y = 319** |

`logical_height` だけを上げても `logical_height <= height` の検証で弾かれるため、
`height` も上げる必要があります。しかし **`DimensionType` には `min_y + height <= 2032`
の制限があるため、データパックを拡張しても Y=5,000 のチャンクは作れません**。
（初版に書いた「dimension_type 拡張で到達」は誤りでした。）

### では Y=5,000 はどう実現するか

**「ブロックを置ける高さ」と「エンティティが座標を持てる高さ」は別**です。
`Entity` は `getMaxBuildHeight()` を超える Y にも座標を持てます（`/tp` や mod の連続移動）。
その領域にはチャンクが無いだけで、重力・tick・当たり判定・ステージ判定は普通に動きます。

むしろ今回の要件に都合がいい:

- チャンクが無い → **地上の描画が最初から存在しない**（「全く行わない」に自然に合う）
- サーバーは地上プレイヤーのチャンクを普通に tick し続ける（「サーバーは別」）
- ディメンションを一切作らないので**シームレス**のまま

### 結論

**Y=5,000 は「建築限界の外側」として扱うのが正解**で、`height` の拡張は不要かつ不可能です。
必要なのは **テレポートではなく連続移動でそこまで運ぶ手段（Step 5）だけ**です。
`SpaceTuning.SPACE_START_Y` は 5,000 のまま変えません。

残りの留意点: `ServerGamePacketListenerImpl` の浮遊判定や `Entity#checkOutOfWorld()`
（下方向のみ）は Step 6 で確認し、無重力状態でも kick されないようにします。

## ✅ Step 5: Y=5,000 への連続昇降手段

**建築限界はバニラのまま。** 宇宙へは「座標の書き換え」ではなく、毎 tick の**速度**で到達します。
アイテム・ブロック・キー登録・ネットワークパケット・アクセス変換は**一切追加していません**。

| 項目 | 内容 |
| --- | --- |
| 開始高度 | `ASCENT_ARM_Y = 320`（= バニラ最大建築 Y 319 の 1 つ上）。**これ未満では何も触らない** |
| 上昇 | **Y=320 を超えた時点で自動的に連続上昇**（追加入力なし。建築限界の外に出たら同じオーバーワールドがそのまま運ぶ） |
| 下降 | スニーク長押し（`player.isShiftKeyDown()`。サーバーへ同期済みのバニラ入力） |
| 停止 | **Y≥5,000 到達で自動停止**。`setNoGravity(true)` と相まって宇宙で浮く |
| 中断 | エリトラ飛行中はブーストしない（Y≥1,000 で `stopFallFlying()`） |
| 最高速度 | `ASCENT_MAX_SPEED = 1.0 m/tick`（20 m/s） |
| 加速度 | `ASCENT_ACCELERATION = 0.04 m/tick²`。開始直後はゆっくり、徐々に加速 |
| 所要 tick | Y=320 → 5,000 に **4,700 tick 前後（約 4 分）**。全大気層を通り抜ける |
| 適用 | `PlayerTickEvent(Phase.END)`・サーバー側のみ、`Entity#setDeltaMovement(x, vy, z)` |

### 「連続」であることの担保

- `SpaceAscentRules` は **import ゼロの純粋 Java**で、**速度しか返さない**（座標・ディメンションを一切返さない）
- `teleportTo` / `setPos` / `absMoveTo` / `changeDimension` / `ServerLevel` 生成を**一切使わない**
- 1 tick の変位は常に `≤ 1.0 m`。テストが軌跡の全 tick を検査し、飛び・逆走を検出
- 水平速度 (`motion.x/z`) は保持、`fallDistance` はリセット（無重力→地上帰還時の落下ダメージ防止）
- エリトラ飛行中はブーストせず、Y≥1,000 で既存フックが `stopFallFlying()`（**エリトラでは宇宙へ行けない**）
- Y<320 に戻ると機構が切れ、以降は**完全にバニラの重力・落下**に引き継ぐ

### なぜエミュレータで実行できるか

本番 jar は `reobfJar` で Minecraft 側のメソッド名が SRG 名に置き換わります。
そのため `SpaceForgeHooks`（Minecraft 依存）は静的検証に留め、
**MC 非依存の `SpaceAscentRules` / `SpaceTuning` を本番 jar から直接ロード**して、
擬似マイクラ (`MockMinecraft`) 上で Y=5,000 到達 → 下降まで **全 tick を実際にエミュレート**します。
`EmulatedForgeLoader` が各 tick の高度・速度を `ascent:y=...,vy=...` として記録します。

## テスト

- `SpaceRulesTest.java`（10 件）: 境界 4,999 / 5,000、ステージ順序、暗さ・星の単調増加、
  重力の単調減少、エリトラ禁止、地上描画カット判定、ディメンション移動がないこと、
  サーバー／クライアント分離、**建築限界バニラ維持**
- `SpaceAscentRulesTest.java`（8 件）: 開始高度 320、バニラ入力の方向、エリトラ無ブースト、
  速度クランプ、加速度上限、**Y=5,000 までの連続軌跡と全ステージ通過**、下降
- `StarwrightJarTest.java`（17 件）: 従来の jar／実 Forge／擬似 MC 検証に加え、
  **本番 jar の連続昇降エミュレーション**、擬似ゲームの全 tick 記録、
  `SpaceAscentRules` が Minecraft 非依存であること、`ServerLevel` 参照が jar 全体に無いこと

合計 **35 件**。`src/main` / `src/mockmc` / `src/test` を触っているため、
前回決めた CI の発火条件どおり **ビルド＋エミュレーションテストが必ず走ります**。
