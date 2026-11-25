# 船（Ship）仕様 — Starwright 独自エンジン

MC **1.20.1 / Forge 47.x**、**単独 mod**。**Create / Create Aeronautics / flywheel / AstroDock の船は一切使用しません**。
依存は Forge だけ、`mods.toml` に追加の `[[dependencies.*]]` はありません。

## 追加したもの

| 種別 | ID | 実装 |
| --- | --- | --- |
| ブロック | `starwright:core_block` | `ship/forge/CoreBlock.java` |
| アイテム | `starwright:wrench` | `ship/forge/WrenchItem.java` |
| エンティティ | `starwright:ship` | `ship/forge/ShipEntity.java` |
| 登録 | `RegisterEvent`（MOD bus） | `ship/forge/ShipRegistry.java` |
| クライアント | 描画しないレンダラ | `ship/forge/client/NoopShipRenderer.java` |

モデルは `assets/starwright/models/**` の **3 個**、`blockstates` **1 個**。
すべて `minecraft:block/iron_block` / `minecraft:item/iron_ingot` を親・テクスチャに再利用し、**PNG は 0 個**です。
レシピ・`data/**`・画面・独自パケット・キー登録は追加していません。

## 使い方

1. **空中に浮かせた船を自分で建てる**（地面・壁・別の建造物に接していないこと）。
2. 構造の**どこでもいいので**コアブロックを置く。コアは BFS の起点になります。
3. 6 近傍の連結した非空気ブロックが収集され、**底面の下が空気**なら構造全体が
   **1 つの `ShipEntity` に所有**されて船になります。ロード画面・テレポート・ディメンション移動はありません。
4. **レンチ + シフト右クリック** … 船化したときにくっついてきた不要なブロックを船から外してドロップ。
   **コアは外せません**。
5. **レンチ + 通常右クリック** … プレイヤーの向いている方向へ **1 ブロック**進みます。
   チェスト等の中身は `CompoundTag` で一緒に運びます。

## 「空中に浮いている船に限る」の判定

`ShipStructureRules`（`ship/`、`net.minecraft.*` 非依存）が担います。座標はコアからの相対値を
1 つの `long` にパックし、`BlockPos` を毎ブロック生成しません。

| ルール | 値 |
| --- | --- |
| 連結判定 | 6 近傍の洪水埋め（BFS）。非空気ブロックをたどる |
| 上限 | `MAX_BLOCKS = 4096`、`MAX_RADIUS = 32`。山や地下を丸ごと船にしない |
| 最小 | `MIN_BLOCKS = 2`（コア + 1 ブロック） |
| 浮遊判定 | **収集した構造の最下端の直下が全て空気**であること |

> **重要な設計判断**: 「構造の外に非空気ブロックが無い」という素直な判定は**誤り**です。
> BFS は地形も非空気として拾ってしまうため、地面に置いた船は地面ごと「構造内」に吸収され、
> 外側が全部空気に見えて浮いていると誤判定されます。
> そこで Starwright は **底面（最小 Y のブロック列）の 1 つ下** だけを調べ、
> さらに下方向の探索が `MAX_RADIUS` に達した場合も「地形に潜った」として拒否します。
> 空中の船だけが通ります。

接地している場合は船化せず、コアブロックは普通のブロックとして残ります（何も壊しません）。

## 独自エンジンと軽量化

Create Aeronautics は「コントラプション」としてブロックをレベルから抜き、仮想レベル・物理世界・
回転・衝突・船メッシュを毎 tick 動かします。Starwright は**ブロックを `Level` に残したまま
1 エンティティが所有する**方式にしたため、その重い層が丸ごと要らなくなりました。

| 項目 | Create Aeronautics | **Starwright** |
| --- | --- | --- |
| ブロックの置き場所 | エンティティ内 / 仮想レベル | **実 `Level` のまま** |
| 物理演算 | 毎 tick の剛体・角速度 | **なし**。クリック 1 回 = 1 軸 1 ブロック |
| 回転・衝突ソルバ | あり | **なし**（移動先が埋まっていたら動かないだけ） |
| 船メッシュ | 毎フレーム更新・バッチ再構築 | **0**。`NoopShipRenderer` は描画しない |
| ドローコール | 船メッシュ分 | **0**（バニラの地形レンダラが描く） |
| 毎 tick の追加処理 | あり | **0**（`tick()` を override せず、`noPhysics = true` で重力も停止） |
| チェスト / 設置 / 歩行 | 仮想レベルで再実装 | **バニラにそのまま委譲**（再実装 0 行） |
| コンテナ移送 | コントラプションの NBT | `saveWithFullMetadata()` → `loadStatic()` を入力時 1 回だけ |
| 追加依存 | Create / flywheel | **Forge のみ** |
| 新規テクスチャ | 多数 | **PNG 0**（バニラを参照） |
| Mixin | 多数 | **既存の 1 つだけ**（宇宙の地上描画カット用） |

`ShipEntity` は構造の相対座標配列・原点・NBT だけを持ち、`defineSynchedData()` も空です。
MC 依存は `ship/forge/**` の薄いブリッジ 1 箇所に集約され、エンジン本体
（`ShipTuning` / `ShipBlockSource` / `ShipStructureRules` / `ShipStructure` / `ShipPhysics` /
`ShipInteractionRules`）は **import に `net.minecraft.*` も `net.minecraftforge.*` も持ちません**。

## バニラ透過性（「船化してもなんでもできる」）

`WrenchItem#useOn` は**レンチの 2 つの操作だけ**を処理し、それ以外は `InteractionResult.PASS` で
Minecraft に返します。ブロックが実レベルに残っているため、以下はすべて追加コード無しで動きます。

- 船の内部にブロックを置く／壊す
- 船の上・内部を歩く（当たり判定はバニラの `BlockState`）
- **チェスト・かまど・扉を開く**（`MenuProvider` / `BlockEntity` は実物）
- レッドストーン、ホッパー、光照度、コマンド、`/setblock` など

つまり「船化」は**構造の所有権が 1 エンティティに移る**ことであって、ブロックを消すことではありません。
サーバー／クライアント分離も維持しています（`tryAssemble` / `detach` / `advance` はすべて
`level.isClientSide` で早期 return）。

## 登録とエミュレータ

`ShipRegistry` は `@Mod.EventBusSubscriber(bus = MOD)` + `RegisterEvent` で登録します。
`Starwright`（`@Mod` エントリポイント）はこのクラスを**一切参照しない**ため、実 Forge ＋ 擬似マイクラの
エミュレータは `Starwright.class` だけを隔離ロードでき、`src/mockmc` に MC クラスを足す必要がありません。
`DeferredRegister` は使いません（Mixin も jar manifest の `MixinConfigs` 登録のまま）。

エミュレータは本番 jar から `ShipStructureRules` / `ShipPhysics` / `ShipInteractionRules` を反射ロードし、
`ShipBlockSource` の `Proxy` で「空中の船」と「地面に置いた船」を組み立てて、洪水埋め・浮遊判定・
コア保護・レンチ判定を実際に実行します。結果は `MockMinecraft.recordShip` に `ship:...` として残ります。

## テスト

- `ShipStructureRulesTest.java`（10 件）: 座標パック、BFS、上限、**接地拒否**、底面接触拒否、
  コア保護と不要ブロック除去、可視面、1 軸 1 ブロック、バニラ委譲
- `StarwrightJarTest.java`（32 件）: 従来の jar／実 Forge／擬似 MC 検証に加え、
  **13 個の船クラスが jar に入っていること**、**エンジンが pure Java であること**、
  **`simibubi` / `createaeronautics` / `flywheel` が class・manifest・エントリ名のどこにも無いこと**、
  **モデル 3 個 / PNG 0 個**、**日本語・英語の表示名**、**本番 jar の船化エミュレーション**、
  擬似マイクラの `ship:` 記録

`src/main` / `src/test` を触っているため、選択的 CI の条件どおり
**jar 生成 ＋ エミュレーション ＋ JUnit が必ず走ります**。

## 非採用（明示）

| 持ってこなかったもの | 理由 |
| --- | --- |
| Create / Create Aeronautics / flywheel | ユーザー指定「使用しない・単独の mod で」 |
| AstroDock の `Ship` / `FlightCapsule` / `CoreScreen` / `ShipGuideScreen` | 到達方法だけを参考にする方針を維持。船は自前実装 |
| 新しいディメンション / `teleportTo` / ロード画面 | 宇宙と同じくシームレスであるべき |
| 船の回転・物理・自動航行・酸素 | 軽量化のため今回の対象外（宇宙の Step 6 も別課題） |
| 新規 PNG / レシピ / `data/**` | リソースを増やさない |
