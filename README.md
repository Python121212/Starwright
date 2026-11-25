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
src/test/java/.../EmulatedForgeLoader.java                     ... FML もどき（jar エミュレータ）
src/test/java/.../StarwrightJarTest.java                       ... jar 検証 JUnit テスト
build.gradle / settings.gradle / gradle.properties             ... ビルド定義（ルート）
gradlew / gradle/wrapper/                                     ... Gradle 8.1.1 wrapper
.github/workflows/build-release.yml                          ... GitHub Actions（CI）
```

生成された **jar はリポジトリにコミットせず、GitHub Releases に添付**します（`.gitignore` で `build/` と `*.jar` を除外）。

## ビルド

必要なもの: **JDK 17**（1.20.1 / Forge 47.x は Java 17 必須。エミュレーションテストも JDK のコンパイラを使います）

```bash
./gradlew build
```

`build` 一発で **jar 生成とテストが一緒に**走ります。
成果物は `build/libs/starwright-1.0.0.jar`（`reobfJar` 済みの本番用 jar）。
テストだけ実行したい場合は `./gradlew test`、開発環境で起動したい場合は `./gradlew runClient` / `./gradlew runServer` を使えます。

## jar のエミュレーションテスト

Minecraft や Forge を**一切起動せずに**、ビルド済み jar が「読み込めるか」を検証します。
`EmulatedForgeLoader` が FML の mod 検出を模します:

1. テスト時に `@Mod` / `Logger` / `LogUtils` の**最小スタブをその場でコンパイル**
2. 親を `null`（bootstrap のみ）にした**隔離 ClassLoader** で jar をロード
   → テスト用 classpath にある本物の Forge/Minecraft は絶対に混入しない
3. `@Mod` アノテーションを読んで modId を取得し、**引数なしコンストラクタを実行**（FML と同じ手順）
4. 出力されたログをキャプチャして、`[Starwright] Loaded successfully` が出たことを確認

あわせて jar の中身も静的に検証します:

| 検証項目 | 内容 |
| --- | --- |
| エントリ | mod クラス・`mods.toml`・`pack.mcmeta`・`MANIFEST.MF` が入っている |
| プレースホルダ | `${...}` が展開し忘れなく残っていないか |
| `mods.toml` | `javafml` / `loaderVersion=[47,)` / mod が 1 個だけ / Forge・Minecraft 依存 |
| `pack.mcmeta` | `pack_format=15`（1.20.1） |
| MANIFEST | `Implementation-Title` / `Version` / `Timestamp` |
| バイトコード | class が **Java 17 (major 61)** であること |

テストは `build/libs` の**本番 jar**（reobf 後）を読むため、`test` は `reobfJar` に依存させています。
Gradle のテストログには `[emulated FML] ...` と実際のログ行が出力されます。

## 導入

Minecraft 1.20.1 + Forge 47.x をインストールし、Releases からダウンロードした jar を
`.minecraft/mods/` に置くだけです。

## CI / リリース（GitHub Actions）

`.github/workflows/build-release.yml` が以下を自動で行います。

| 契機 | 動作 |
| --- | --- |
| `main` / `arena/**` への push、PR | JDK 17 で `./gradlew build`（**jar 生成 ＋ エミュレーションテスト**）を実行。jar と JUnit レポートを artifact に保存し、テスト結果をジョブサマリーに表示 |
| `v*` タグの push | 上記に加えて **GitHub Release を作成し、jar を添付** |
| `workflow_dispatch` | 手動実行 |

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
