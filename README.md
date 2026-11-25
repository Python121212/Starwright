# Starwright

Minecraft **1.20.1** 用の **Forge** mod です。
まずは「読み込みだけ」の最小構成から始めています。機能（アイテム・ブロック・イベント登録など）はまだ何も追加していません。

## この mod がすること

FML が `META-INF/mods.toml` と `@Mod("starwright")` を検出して `Starwright` クラスを生成し、
ログに 1 行だけ出力します。

```
[Starwright] Loaded successfully (Minecraft 1.20.1 / Forge 47.x).
```

`latest.log` にこの行が出れば、mod が正しく読み込まれた合図です。

## ファイル構成

mod 本体のファイルは **`src/main`**（Gradle の main ソースセット）に置いています。

```
src/main/java/io/github/python121212/starwright/Starwright.java  ... @Mod エントリポイント
src/main/resources/META-INF/mods.toml                          ... mod 定義・依存関係
src/main/resources/pack.mcmeta                                 ... リソースパック情報
src/mockmc/java/                                               ... 擬似マイクラ（フェイク実装一式）
src/test/java/.../EmulatedForgeLoader.java                     ... 実 Forge ＋ 擬似マイクラで FML を再現
src/test/java/.../StarwrightJarTest.java                       ... jar 検証 JUnit テスト（13 件）
build.gradle / settings.gradle / gradle.properties             ... ビルド定義（ルート）
gradlew / gradle/wrapper/                                     ... Gradle 8.1.1 wrapper
.github/workflows/build-release.yml                          ... GitHub Actions（CI）
```

生成された **jar はリポジトリにコミットせず、GitHub Releases に添付**します（`.gitignore` で `build/` と `*.jar` を除外）。

## ビルド

必要なもの: **JDK 17**（1.20.1 / Forge 47.x は Java 17 必須）

```bash
./gradlew build
```

`build` 一発で **jar 生成とテストが一緒に**走ります。
成果物は `build/libs/starwright-1.0.0.jar`（`reobfJar` 済みの本番用 jar）。
テストだけ実行したい場合は `./gradlew test`、開発環境で起動したい場合は `./gradlew runClient` / `./gradlew runServer` を使えます。

## Forge を使用したエミュレーション ＋ 擬似マイクラ

Minecraft は**一切起動せず**に、**Forge は本物**、**Minecraft 側は擬似**という役割分担でビルド済み jar を検証します。

| 層 | 中身 |
| --- | --- |
| **Forge（本物）** | テスト用 classpath の本物の `net.minecraftforge.fml.common.Mod`（Forge 47.2.0 の jar から class バイト列を読み、隔離ローダーに定義）。`src/mockmc` に Forge のスタブは**一切無い** |
| **擬似マイクラ** | `src/mockmc/java` の `net.minecraft.*` / `com.mojang.logging.*` / `org.slf4j.*` のフェイク実装と、`MockMinecraft`（起動・ログ・mod 登録を記録する疑似ランタイム） |
| **mod（本物）** | ビルド済み `build/libs/starwright-1.0.0.jar` を実際にロードしてコンストラクタを実行 |

`src/mockmc` は **JDK のみを classpath にして独立コンパイル**されるため、本物の Minecraft / Forge クラスが紛れ込むことはありません。

`EmulatedForgeLoader` の流れ:

1. テスト classpath から**本物の `@Mod` のバイト列**を読み、CodeSource（Forge jar の場所）を記録
2. 親を `null`（bootstrap のみ）にした隔離 `ClassLoader` を用意 → 本物の Minecraft クラスは混入しない
3. `@Mod` は本物のバイト列、`net.minecraft.*` 等は擬似マイクラ、mod クラスは jar から、それぞれ `defineClass`
4. `@Mod("starwright")` を読んで modId を取得し、**引数なしコンストラクタを実行**（FML と同じ手順）
5. 擬似マイクラの `MockMinecraft` に mod を登録し、起動／ログ／登録の記録を回収
6. ログをキャプチャして `[MockMC] Booting pseudo Minecraft 1.20.1` と `[Starwright] Loaded successfully` を確認

### 検証項目（JUnit 13 件）

| 種別 | 内容 |
| --- | --- |
| jar 静的検証 | エントリ、`${...}` 展開漏れ、`mods.toml`、`pack.mcmeta`、MANIFEST、class が Java 17 (major 61) |
| 実 Forge | 本物の `@Mod` が classpath に存在し、そのバイト列が使われている／擬似マイクラ側に Forge スタブが無い |
| 擬似マイクラ | `bootstrap:1.20.1` で起動、`[MockMC/INFO]` ログ、`mod:starwright` の登録が記録されている |
| エミュレート読み込み | `@Mod` 検出 → コンストラクタ実行 → `[Starwright] Loaded successfully` |

テストは本番 jar（reobf 後）と擬似マイクラのコンパイル済みクラスを読むため、`test` は `reobfJar` と `mockmcClasses` に依存させています。Gradle のテストログには `[emulated FML] ...` と実際のログ行が出力されます。

## 本題を始める前に（準備）

ここまでで揃っているもの:

| 項目 | 状態 |
| --- | --- |
| Forge 1.20.1 (47.2.0) / Gradle 8.1.1 / JDK 17 | ✅ ビルド定義済み |
| 読み込みだけの jar → GitHub Releases | ✅ `v1.0.0` |
| 実 Forge ＋ 擬似マイクラのエミュレーション | ✅ JUnit 13 件 |
| CI の発火条件 | ✅ jar 入力／本題の変更のみ。ドキュメントのみは無発火 |
| **mod イベントバスの注入** | ✅ `Starwright(IEventBus)` — FML が本物のバスを渡す |
| **`net.minecraftforge.*` はすべて本物** | ✅ エミュレータが test classpath の Forge jar から供給（`@Mod` だけでなく `IEventBus` 等も） |
| 翻訳ファイルの受け皿 | ✅ `assets/starwright/lang/{en_us,ja_jp}.json` |

エミュレータは **引数なし／`IEventBus` 1 引数、どちらのコンストラクタでも動く**ので、本題で登録処理を足しても壊れません。
また `net.minecraft.*` / `com.mojang.*` / `org.slf4j.*` は擬似マイクラ**だけ**が供给され、足りないクラスは
`pseudo Minecraft does not implement ...` と明示的に失敗します（本物の Minecraft が紛れ込むことはありません）。

### 本題でアイテム／ブロックを追加するときの手順

1. `src/main/java/.../registry/` に `DeferredRegister` を持つクラスを作り、`Starwright` コンストラクタの `modEventBus` に `register(...)` する
2. `assets/starwright/lang/*.json` に表示名、`models/` と `textures/` にモデル・テクスチャを置く
3. `data/starwright/recipes/` にレシピを置く（必要なら）
4. その登録コードが参照する **`net.minecraft.*` クラスだけ**を `src/mockmc/java` に擬似実装として追加する
5. `./gradlew build` → jar 生成とテストが一緒に走る。`src/main` を触っているので **CI も必ず発火**します

> Forge 側のクラス（`DeferredRegister`, `RegistryObject`, `IEventBus` …）はすべて本物が供給されるため、
> `src/mockmc` に Forge のスタブを置く必要はありません。

## 導入

Minecraft 1.20.1 + Forge 47.x をインストールし、Releases からダウンロードした jar を
`.minecraft/mods/` に置くだけです。

## CI / リリース（GitHub Actions）

### いつ走るか

**エミュレーション＋テストは、jar が更新される変更（アイテム追加などを含む）のときに必ず走り、それ以外では一切走りません。**

jar はリポジトリにコミットしないため、「jar が更新された」は `.github/workflows/build-release.yml` の
`on.push.paths` / `on.pull_request.paths` で判定します（**いずれか**のファイルが触られれば発火）。

| 変更内容 | CI |
| --- | --- |
| `src/main/**` — mod 本体、**アイテム・ブロック追加など** | ✅ **必ず実行** |
| `build.gradle` / `settings.gradle` / `gradle.properties` / `gradlew*` / `gradle/wrapper/**` — jar の作り方 | ✅ **必ず実行** |
| `src/test/**` / `src/mockmc/**` — エミュレータ・擬似マイクラ・テスト自体の変更 | ✅ **必ず実行** |
| `v*` タグの push | ✅ **必ず実行**。GitHub はタグ push に `paths` を適用しないため、Release 公開前に必ず検証される |
| `README.md` / `.github/**` / `.gitignore` / `.gitattributes` / ドキュメント**のみ** | ❌ **一切実行しない**（workflow run すら作られない） |
| `workflow_dispatch` | 自動ではなく、手動で明示的に起こした場合のみ |

### 実行内容

| 契機 | 動作 |
| --- | --- |
| 上記いずれかの変更を含む `main` / `arena/**` への push、PR | JDK 17 で `./gradlew build`（**jar 生成 ＋ エミュレーションテスト**）。jar と JUnit レポートを artifact に保存し、テスト結果をジョブサマリーに表示 |
| `v*` タグの push | 上記に加えて **GitHub Release を作成し、jar を添付** |

リリースを出す手順:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Actions の完了後、Releases に `starwright-1.0.0.jar` が添付されます。

## バージョン

| 項目 | 値 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.2.0（`[47,)`） |
| ForgeGradle | 6.0.x |
| Gradle | 8.1.1 |
| Java | 17 |
| JUnit | 5.10.2 |
| mod version | 1.0.0 |

---

> メモ: このセッションの作業ブランチは `arena/3d34603a-starwright` です。
> ブランチ運用の制約上、`main` ブランチへは直接 push せず、このブランチ上の変更として積み上げます。
> 「main に設置」は `src/main` ソースセットを指すものとして解釈しています。
