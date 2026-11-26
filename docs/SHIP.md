# 船（Ship）仕様 — Create: Aeronautics 参考の自前エンジン

MC **1.20.1 / Forge 47.x**、**単独 mod**。
[Create: Aeronautics / Simulated](https://github.com/Creators-of-Aeronautics/Simulated-Project) の
**役割と名前だけを参考**にし、コード・アセット・依存は一切持ってきていません。
**Create / Create Aeronautics / flywheel / AstroDock の mod は不要**です（`mods.toml` に追加依存なし）。

**レンチとコアブロックは削除しました。**

## 追加したもの（6 オブジェクト）

| ID | 種別 | 参考にした CA の要素 | 役割 |
| --- | --- | --- | --- |
| `starwright:physics_assembler` | ブロック | `Physics Assembler` | **船化の起点**。空中に浮く自作構造に置くと構造全体が 1 つの `ShipEntity` に。シフト右クリックで不要ブロック除去、通常右クリックで前進 |
| `starwright:propeller_bearing` | ブロック | `Propeller Bearing` | プレイヤーの向いている方向へ 1 ブロック前進 |
| `starwright:hot_air_burner` | ブロック | `Hot Air Burner` | 上昇（+Y に 1 ブロック） |
| `starwright:steam_vent` | ブロック | `Steam Vent` | 下降（−Y に 1 ブロック） |
| `starwright:levitite` | アイテム | `Levitite` | 浮遊結晶（素材／装飾） |
| `starwright:aviators_goggles` | アイテム | `Aviator's Goggles` | 飛行士ゴーグル（装飾、1 スタック 1 個） |

3 つの操縦ブロックは **`ShipControlBlock` 1 クラス**に `Direction` を渡すだけの実装です
（`null` = プレイヤー向き、`UP` / `DOWN` = 固定軸）。クラスを増やさないのが軽量化です。

6 つとも **Starwright 専用クリエイティブタブ** `starwright:starwright`
（`itemGroup.starwright`、アイコンは物理アセンブラ）に並びます。
レシピ／`data/**` は未追加（クリエイティブ／`/give` 用）。

## 見た目（鉄の流用を廃止）

以前は `minecraft:item/iron_ingot` と `minecraft:block/iron_block` を親・テクスチャに流用していましたが、
**6 枚すべてオリジナルの 16×16 ピクセルアート**に差し替えました（`assets/starwright/textures/**`）。

| テクスチャ | デザイン |
| --- | --- |
| `physics_assembler` | 真鍮の枠＋四隅のボルト＋光るシアンのコア |
| `propeller_bearing` | 銅のハブ＋4 枚の木製プロペラ羽根 |
| `hot_air_burner` | 鉄の台座＋オレンジ／黄色の炎 |
| `steam_vent` | 鉄の格子＋白い蒸気 |
| `levitite` | 青紫の浮遊結晶＋ハイライト |
| `aviators_goggles` | 革バンド＋水色の二眼レンズ |

Pillow 等の画像ライブラリは使わず、PNG を直接生成しているため 1 枚 121〜152 B です。
モデル 10 個・blockstate 4 個・PNG 6 個、すべて `starwright:` の自前テクスチャを参照します
（`minecraft:block/iron_block` / `minecraft:item/iron_ingot` への参照は 0）。

## 使い方

1. **空中に浮かせた船を自分で建てる**（地面・壁・別の建造物に接していないこと）。
2. 構造のどこでもいいので **物理アセンブラ** を置く → 6 近傍 BFS で連結ブロックを収集し、
   **底面の下が空気**なら構造全体が 1 つの `ShipEntity` に所有されて船になります。
   ロード画面・テレポート・ディメンション移動はありません。
3. **物理アセンブラ + シフト右クリック** … くっついてきた不要なブロックを船から外してドロップ。
   アセンブラ自体は外せません。
4. **右クリックで移動**（1 回 = 1 軸 1 ブロック）:

   | クリック先 | 動き |
   | --- | --- |
   | 物理アセンブラ | 向いている方向へ前進 |
   | プロペラ軸受 | 向いている方向へ前進 |
   | 熱気バーナー | 上昇 |
   | スチームベント | 下降 |

   チェスト等の中身は `CompoundTag` で一緒に運びます。移動先が埋まっていたら動きません。

## 「空中に浮いている船に限る」の判定

`ShipStructureRules`（`ship/`、`net.minecraft.*` 非依存）が担います。座標はアセンブラからの相対値を
1 つの `long` にパックし、`BlockPos` を毎ブロック生成しません。

| ルール | 値 |
| --- | --- |
| 連結判定 | 6 近傍の洪水埋め（BFS）。非空気ブロックをたどる |
| 上限 | `MAX_BLOCKS = 4096`、`MAX_RADIUS = 32` |
| 最小 | `MIN_BLOCKS = 2` |
| 浮遊判定 | **収集した構造の最下端の直下が全て空気**であること |

> BFS は地形も拾うため「構造の外に非空気が無い」という判定は誤りです。
> 地面に置いた船は地面ごと吸収されて浮いているように見えてしまいます。
> そこで **底面の 1 つ下** を調べ、下方向探索が `MAX_RADIUS` に達した場合も接地として拒否します。

接地している場合は船化せず、物理アセンブラは普通のブロックとして残ります。

## 独自エンジンと軽量化

Create Aeronautics はコントラプションを仮想レベル／物理世界で毎 tick 動かします。
Starwright は**ブロックを `Level` に残したまま 1 エンティティが所有する**方式なので、その重い層が不要です。

| 項目 | Create Aeronautics | **Starwright** |
| --- | --- | --- |
| ブロックの置き場所 | コントラプション / 仮想レベル | **実 `Level` のまま** |
| 物理演算 | 毎 tick の剛体・揚力・空気圧・角速度 | **なし**。クリック 1 回 = 1 軸 1 ブロック |
| 回転・衝突ソルバ | あり | **なし**（移動先が埋まっていたら止まるだけ） |
| 船メッシュ | 毎フレーム更新 | **0**。`NoopShipRenderer` は描画しない |
| ドローコール | 船メッシュ分 | **0**（バニラの地形レンダラが描く） |
| 毎 tick の追加処理 | あり | **0**（`tick()` を override せず `noPhysics = true`） |
| チェスト / 設置 / 歩行 | 仮想レベルで再実装 | **バニラにそのまま委譲**（再実装 0 行） |
| コンテナ移送 | コントラプションの NBT | `saveWithFullMetadata()` → `loadStatic()` を入力時 1 回だけ |
| 追加依存 | Create / flywheel / Valkyrien 系 | **Forge のみ** |
| 船化の起点 | Physics Assembler | **自前 `PhysicsAssemblerBlock`**（同名の役割だけ参考） |
| 操作方法 | レンチ／ベアリング／バーナー | **レンチ廃止**、4 つのブロックの右クリックだけ |

エンジン本体（`ShipTuning` / `ShipBlockSource` / `ShipStructureRules` / `ShipStructure` /
`ShipPhysics` / `ShipInteractionRules`）は **`net.minecraft.*` も `net.minecraftforge.*` も import しません**。
MC 依存は `ship/forge/**` の薄いブリッジに集約されています。

## バニラ透過性（「船化してもなんでもできる」）

`PhysicsAssemblerBlock#use` / `ShipControlBlock#use` は**上記 4 操作だけ**を処理し、
それ以外は `InteractionResult.PASS` で Minecraft に返します。ブロックが実レベルに残るため、
内部への設置・破壊・歩行・**チェスト／かまど／扉を開く**・レッドストーン・光照度は
追加コード無しで動きます。つまり「船化」は**所有権が 1 エンティティに移る**ことであって、
ブロックを消すことではありません。

サーバー／クライアント分離も維持（`tryAssemble` / `detach` / `advance` は `level.isClientSide` で早期 return）。

## 登録とエミュレータ

`ShipRegistry` は `@Mod.EventBusSubscriber(bus = MOD)` + `RegisterEvent` で
BLOCKS(4) / ITEMS(6) / ENTITY_TYPES(1) / `Registries.CREATIVE_MODE_TAB`(1) を登録します。
`Starwright`（`@Mod`）はこのクラスを参照しないため、実 Forge ＋ 擬似マイクラのエミュレータは
`Starwright.class` だけを隔離ロードでき、`src/mockmc` に MC クラスを足す必要がありません。

エミュレータは本番 jar から船エンジンを反射ロードし、`ShipBlockSource` の `Proxy` で
「空中の船」と「地面に置いた船」を組み立てて、洪水埋め・浮遊判定・アセンブラ保護・
各クリック判定を実際に実行します。結果は `MockMinecraft.recordShip` に `ship:...` として残ります。

## テスト

- `ShipStructureRulesTest.java`（10 件）: 座標パック、BFS、上限、接地拒否、底面接触拒否、
  アセンブラ保護と不要ブロック除去、可視面、1 軸 1 ブロック、バニラ委譲
- `StarwrightJarTest.java`（37 件）: 従来の jar／実 Forge／擬似 MC 検証に加え、
  **15 個の船クラス**、**エンジンが pure Java**、**6 つの自前テクスチャ**（鉄流用 0）、
  **モデル 10 / blockstate 4 / PNG 6**、**レンチとコアブロックが jar に無いこと**、
  **6 オブジェクトのモデルと翻訳**、**Create / Create Aeronautics / flywheel 依存不在**、
  専用タブ、本番 jar の船化エミュレーション

`src/main` / `src/test` を触っているため、選択的 CI の条件どおり
**jar 生成 ＋ エミュレーション ＋ JUnit が必ず走ります**。

## 非採用（明示）

| 持ってこなかったもの | 理由 |
| --- | --- |
| Create / Create Aeronautics / flywheel / Valkyrien の jar・コード・PNG | 「Create などなしで」。役割と名前だけ参考 |
| レンチ / 旧コアブロック | ユーザー指定で削除。操作は 4 ブロックの右クリックに統合 |
| 気球エンベロープ 16 色・ボイラー・高度計・物理演算 | まず 6 オブジェクトの「とりあえず」構成。軽量化を優先 |
| 新しいディメンション / `teleportTo` / ロード画面 | 宇宙と同じくシームレス |
| レシピ / `data/**` | リソースを増やさない（クリエイティブ／`/give` 用） |
