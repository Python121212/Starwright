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

成果物は `build/libs/starwright-1.0.0.jar` に作られます（`reobfJar` 済みの本番用 jar）。
開発環境で起動したい場合は `./gradlew runClient` / `./gradlew runServer` を使えます。

## 導入

Minecraft 1.20.1 + Forge 47.x をインストールし、Releases からダウンロードした jar を
`.minecraft/mods/` に置くだけです。

## CI / リリース（GitHub Actions）

`.github/workflows/build-release.yml` が以下を自動で行います。

| 契機 | 動作 |
| --- | --- |
| `main` / `arena/**` への push、PR | JDK 17 で `./gradlew build` を実行し、jar を workflow artifact として保存 |
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
| mod version | 1.0.0 |

---

> メモ: このセッションの作業ブランチは `arena/3d34603a-starwright` です。
> ブランチ運用の制約上、`main` ブランチへは直接 push せず、このブランチ上の変更として積み上げます。
> 「main に設置」は `src/main` ソースセットを指すものとして解釈しています。
