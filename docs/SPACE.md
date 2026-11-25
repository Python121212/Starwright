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
| 5 | Y=5,000 への到達手段（下記の制約を参照） | ⏳ |
| 6 | 酸素・気圧・モブスポーン抑制・音の減衰など | ⏳ |

Step 2〜4 はクライアント描画に深入りするため、Mixin あるいは Forge の
クライアント拡張ポイントの追加が必要です。判定側は完成しているので、
描画側を足すだけの状態です。

## ⚠️ 技術的な制約（重要）

Minecraft 1.20.1 のオーバーワールドは既定で **Y = -64 〜 319** です。
したがって **Y = 5,000 には通常到達できません**。

判定ロジックは指定どおり 5,000 基準で先に実装していますが、実際に到達するには
次のいずれかが必要です:

1. `data/minecraft/dimension_type/overworld.json` の `height` / `logical_height` を拡張する
   （**新規ワールドのみ有効**。チャンク 1 個あたりのセクション数が増え、メモリ・生成コストが大きい）
2. 専用の昇降手段（ロケット／エレベーター等。テレポートではなく**連続移動**）で高高度へ運ぶ
3. 論理高度と実座標をスケール対応させ、「Y=5,000」を論理値として扱う

**Step 5 で `SpaceTuning.SPACE_START_Y` を変えずに**この到達手段を決めます。
現時点では、サーバー／クライアントの状態遷移・物理変化・テストが先に動く状態です。

## テスト

`src/test/java/.../SpaceRulesTest.java`（9 件）が以下を検証します:

- 境界 4,999 / 5,000
- ステージの順序
- 暗さ・星の単調増加、重力の単調減少
- エリトラ禁止
- 地上描画カット判定
- ディメンション移動がないこと
- サーバー／クライアントの分離

`src/main` を触っているため、前回決めた CI の発火条件どおり **ビルド＋エミュレーションテストが必ず走ります**。
