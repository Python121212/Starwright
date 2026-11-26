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
| 地上 | **描画を完全停止**（Mixin が `renderChunkLayer` を cancel） | **止めない**。チャンク tick・当たり判定・他プレイヤーを維持 |

`SpaceForgeHooks` と `SpaceClientState` は `@Mod.EventBusSubscriber` で FML が自動登録するため、
`Starwright` 本体はこれらを一切参照しません。これにより jar エミュレータは従来どおり
`Starwright` だけを隔離ロードできます。

## 実装状況とロードマップ

| Step | 内容 | 状態 |
| --- | --- | --- |
| 1 | ステージ判定・設定・エリトラ禁止・無重力・クライアント状態・テスト | ✅ **今回** |
| 2 | 空を段階的に暗くする（`ComputeFogColor` ＋ `AFTER_SKY` の空キューブ） | ✅ **今回** |
| 3 | 見える星を増やす（固定シード 240 クアッドを段階的に描画） | ✅ **今回** |
| 4 | 地上描画の完全カット（`renderChunkLayer` を Mixin 1 つで cancel。refmap 不要） | ✅ **今回** |
| 5 | Y=5,000 への**連続移動**手段（建築限界バニラ維持） | ✅ 実装済み |
| 6 | 酸素・気圧・モブスポーン抑制・音の減衰など | ⏳ |

**Step 2〜4 は今回まとめて実装**し、あわせて描画まわりの軽量化も行いました（下記）。
Step 5 も実装済みです。残るは Step 6 のみです。

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

浮遊判定は今回の Step 5 で対処済み（サバイバル／アドベンチャーは一時的に飛行許可を付与）。
`Entity#checkOutOfWorld()` は下方向のみなので高高度では発火しません。
酸素・気圧・モブスポーン抑制などは Step 6 のままです。

## ✅ Step 2〜4: 空・星・地上描画カットと軽量化

AstroDock の `SkyLayer` / `AtmosphereClient` を参考にしつつ、**1 クラス＋Mixin 1 つ**に集約しました。

| Step | 実装 | 根拠 |
| --- | --- | --- |
| 2 空の暗化 | `ViewportEvent.ComputeFogColor` でフォグを暗転＋`RenderLevelStageEvent(AFTER_SKY)` で空キューブを 1 ドローコール描画。色は `SpaceRenderRules.skyColor(darkness)` で青 `0x659be2` → 黒 | `darkness = sqrt(t)` をそのまま色補間に使う |
| 3 星の増加 | 固定シードの **240 クアッドをクラスロード時に 1 回だけ生成**。毎フレームは `visibleStars(starVisibility)` 個だけ描く | `starVisibility = t^1.8`。地上 0 個 → 宇宙 240 個 |
| 4 地上描画カット | `SpaceLevelRendererMixin` が `LevelRenderer#renderChunkLayer`（SRG `m_172993_`）を **HEAD で cancel**。空は生きるので Step 2/3 と両立 | `SpaceClientState.skipGroundRendering()` が Y≥4,200 または宇宙で true |

### 軽量化の中身

| 項目 | 効果 |
| --- | --- |
| **地形描画** | `renderChunkLayer` の呼び出し自体を止める（チャンクが無い高高度での無駄なレイヤー処理もゼロ） |
| **星ジオメトリ** | 240 クアッドを 1 回生成するだけ。毎フレームの再生成・星エンティティ・テクスチャは **0** |
| **ドローコール** | 空キューブ＋星を `BufferBuilder` 1 本 / `BufferUploader` 1 回に統合 |
| **`SpaceState`** | 高度が **1 ブロック以上**変わったときだけ再生成。静止中の 20 回/秒のアロケーションを削減 |
| **地上付近** | `darkness == 0` ならフォグも空も即 return。バニラ描画に一切触らない |
| **追加物** | 描画クラス 1、`client` Mixin 1、`mixins.json` 1。`refmap` / MixinGradle / 追加依存 / テクスチャ / モデル / 画面は **0** |
| **サーバー** | 描画カットはクライアントのみ。サーバーは地上チャンクを従来どおり tick し続ける |

Mixin は SRG 名をリテラルで書き `remap = false, require = 0` にしたため **refmap が不要**です。
開発環境（official 名）では `require = 0` で無視され、本番の reobf 済み jar で確実に適用されます。
**登録は Forge 1.20.1 の標準どおり jar manifest の `MixinConfigs: starwright.mixins.json`** です。
（`mods.toml` の `[[mixins]]` は NeoForge 側のキーで、Forge 1.20.1 の `ModFileInfo` は読みません。）
このため MixinGradle プラグインも追加依存も必要ありません。

## ✅ Step 5: Y=5,000 への連続昇降手段

**建築限界はバニラのまま。** 宇宙へは「座標の書き換え」ではなく、毎 tick の**速度**で到達します。
アイテム・ブロック・キー登録・独自ネットワークパケット・画面は**一切追加していません**。
上昇入力はバニラの搭乗入力パケットが既に運ぶ `jumping` を、1 行の Access Transformer で読むだけです。

| 項目 | 内容 |
| --- | --- |
| 開始高度 | `ASCENT_ARM_Y = 320`（= バニラ最大建築 Y 319 の 1 つ上）。**これ未満では何も触らない** |
| 上昇 | **ジャンプ長押し**（`LivingEntity.jumping`。AT で公開、バニラの搭乗入力パケットが既にサーバーへ届けているので追加通信なし）。AstroDock の FlightCapsule アイテムの代わり |
| 下降 | スニーク長押し（`player.isShiftKeyDown()`。サーバーへ同期済みのバニラ入力） |
| 停止 | ジャンプを離すと減速→停止。**非クリエは Y≥5,000 到達で自動停止**、`setNoGravity(true)` で宇宙に浮く |
| **クリエイティブ飛行** | `getAbilities().flying` を検出。**宇宙到達後も飛行を継続**し、スペース／シフトで上昇・下降し続けられる。Y<320 の通常クリエ飛行は完全にバニラのまま |
| **サバイバル／アドベンチャー** | `mayfly = false` のため vanilla の浮遊判定で kick される。Y≥320 の間だけ `Abilities.mayfly` / `flying` を**一時付与**し、元の値を `UUID` で退避。**Y<320 に戻った瞬間に復元**、ゲームモードは不変 |
| 中断 | エリトラ飛行中はブーストしない（Y≥1,000 で `stopFallFlying()`） |
| 最高速度 | `ASCENT_MAX_SPEED = 1.0 m/tick`（20 m/s） |
| 加速度 | `ASCENT_ACCELERATION = 0.04 m/tick²`。開始直後はゆっくり、徐々に加速 |
| 所要 tick | Y=320 → 5,000 に **4,700 tick 前後（約 4 分）**。全大気層を通り抜ける |
| 適用 | `PlayerTickEvent(Phase.START)`・サーバー側のみ、`Entity#setDeltaMovement(x, vy, z)` |

### 「連続」であることの担保

- `SpaceAscentRules` は **import ゼロの純粋 Java**で、**速度しか返さない**（座標・ディメンションを一切返さない）
- `teleportTo` / `setPos` / `absMoveTo` / `changeDimension` / `ServerLevel` 生成を**一切使わない**
- 1 tick の変位は常に `≤ 1.0 m`。テストが軌跡の全 tick を検査し、飛び・逆走を検出
- 水平速度 (`motion.x/z`) は保持、`fallDistance` はリセット（無重力→地上帰還時の落下ダメージ防止）
- **`Phase.START` で適用**する。`Player#travel()` はクリエ飛行中に垂直速度をジャンプ／シフト入力から作り直すため、
  その**前**に速度を入れないと上書きされてしまう。START ならバニラの飛行入力がそのまま上乗せされ、水平移動も自由なまま宇宙まで飛べる
- **サバイバル／アドベンチャーは `mayfly = false`** なので、Y≥320 の間だけ `Abilities.mayfly` / `flying` を一時付与し、
  `UUID` をキーに元の値を退避。`onUpdateAbilities()` は付与・復元の **1 回だけ**呼ぶ（毎 tick パケットを送らない）。
  **Y<320 に戻った瞬間に元通り復元**し、ゲームモードは一切変更しない
- クリエイティブ／スペクテイターは元から飛行許可があるため**一切触らない**。浮遊判定対象外なので高高度でも kick されない
- **重力は `ForgeMod.ENTITY_GRAVITY` の `AttributeModifier(MULTIPLY_TOTAL)` で段階的に弱める**。
  `SpaceRules.gravityScale(y)` を実際の物理へ適用し、0.01 刻みに丸めて属性パケットの連送を防ぐ（AstroDock の `gravityLoss` 手法）。
  Y≥5,000 は `setNoGravity(true)` で完全無重力。「宇宙は見た目ではない」を数値だけにしない
- **Access Transformer は `LivingEntity.f_20899_ # jumping` の 1 行だけ**。アイテム・キー登録・独自パケット・画面は追加しない
- AstroDock の `teleportTo` による ORBIT ディメンション境界移動は**採用しない**（Starwright はディメンションを一切作らない）
- `Entity#checkOutOfWorld()` は下方向のみ。Y=5,000 では発火しない
- エリトラ飛行中はブーストせず、Y≥1,000 で既存フックが `stopFallFlying()`（**エリトラでは宇宙へ行けない**）
- Y<320 に戻ると機構が切れ、以降は**完全にバニラの重力・落下**に引き継ぐ

### なぜエミュレータで実行できるか

本番 jar は `reobfJar` で Minecraft 側のメソッド名が SRG 名に置き換わります。
そのため `SpaceForgeHooks`（Minecraft 依存）は静的検証に留め、
**MC 非依存の `SpaceAscentRules` / `SpaceTuning` を本番 jar から直接ロード**して、
擬似マイクラ (`MockMinecraft`) 上で Y=5,000 到達 → 下降まで **全 tick を実際にエミュレート**します。
`EmulatedForgeLoader` が各 tick の高度・速度を `ascent:y=...,vy=...` として記録します。

## 🛰️ AstroDock から学んだ「宇宙の行き方」だけ

成功例 [AstroDock](https://github.com/Python121212/AstroDock/tree/arena%2Ff49e2986-astrodock) のうち、
**到達方法だけ**を参考にしました。アイテム・UI・船・モデル・コマンドは一切持っていません。

| AstroDock | Starwright | 採用 |
| --- | --- | --- |
| `Atmosphere.SPACE = 5000`、段階的な `ramp` / band | `SPACE_START_Y = 5000` ＋ 6 ステージ | ✅ 既に同等 |
| `accesstransformer.cfg` で `LivingEntity.f_20899_ # jumping` を public 化 | **同じ AT を 1 行だけ追加**（`build.gradle` で明示指定） | ✅ **今回** |
| `FlightCapsule` を使い続けると視線方向へ加速 | **ジャンプ長押しで上昇**（アイテム不要・追加通信不要） | ✅ 入力だけ採用 |
| `ForgeMod.ENTITY_GRAVITY` に `MULTIPLY_TOTAL` で `gravityLoss` | `gravityScale(y)` を**実際の属性へ適用** | ✅ **今回** |
| 高高度の空気抵抗を `rarefaction` で相殺 | 連続上昇が速度を上書きするため不要 | ⏸ 対象外 |
| エリトラを Y≥1,500 で停止 | `ELYTRA_CUTOFF_Y = 1000` を維持（ユーザー指定） | ❌ 変更せず |
| `teleportTo` で ORBIT ディメンションへ境界移動・チャンク先読み | **ディメンションを一切作らない** | ❌ 不採用 |
| `FlightCapsule` / `Ship` / `CoreScreen` / `ShipGuideScreen` / 惑星 / 太陽 / 低酸素 | **一切追加しない** | ❌ 不採用 |

## テスト

- `SpaceRulesTest.java`（10 件）: 境界 4,999 / 5,000、ステージ順序、暗さ・星の単調増加、
  重力の単調減少、エリトラ禁止、地上描画カット判定、ディメンション移動がないこと、
  サーバー／クライアント分離、**建築限界バニラ維持**
- `SpaceAscentRulesTest.java`（13 件）: 開始高度 320、**ジャンプ長押しが上昇入力**、ジャンプを離すと停止、
  **クリエ飛行は宇宙でも上昇継続**、**サバイバル／アドベンチャーの一時飛行許可と復元**、
  エリトラ無ブースト、速度クランプ、加速度上限、**Y=5,000 までの連続軌跡と全ステージ通過**、下降
- `SpaceRenderRulesTest.java`（6 件）: 星の単調増加、空の青→黒、フォグ減衰、地上カット境界、
  星が暗さより遅れて現れること、範囲外/NaN のクランプ
- `ShipStructureRulesTest.java`（10 件）: 船化の氾濫埋め・浮遊判定・コア保護・1 ブロック移動・バニラ委譲
- `StarwrightJarTest.java`（35 件）: 従来の jar／実 Forge／擬似 MC 検証に加え、
  **本番 jar の連続昇降エミュレーション**、**ジャンプ入力が上昇を駆動すること**、
  **Access Transformer の内容**、**本番 jar のクリエ飛行が宇宙でも上昇を返すこと**、
  **本番 jar がサバイバル／アドベンチャーに飛行許可を与えること**、
  **AstroDock 由来のアイテム／画面／船が混入していないこと**、`Phase.START`・重力属性・AT フィールド参照、
  一時飛行許可ルールへの委譲、擬似ゲームの全 tick 記録、
  **Mixin の SRG 名と refmap 不在**、**本番 jar の空・星・フォグ曲線**、**1 renderer / 1 mixin / PNG 0 の軽量構成**、
  **本番 jar の船化エンジン**、**Create / Create Aeronautics 依存不在**、
  `SpaceAscentRules` と `SpaceRenderRules` が Minecraft 非依存であること、`ServerLevel` 参照が jar 全体に無いこと

合計 **74 件**。`src/main` / `src/test` を触っているため、
前回決めた CI の発火条件どおり **ビルド＋エミュレーションテストが必ず走ります**。
