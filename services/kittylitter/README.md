# baozicli（包子 Mac 端）

Distribution wrapper for the [alleycat](https://github.com/huangguang1999/alleycat) daemon.
Ships the daemon to npm as **`baozicli`**; the installed command is **`baozi`**.

把手机端「包子」App 连到这台 Mac 的守护进程。安装后用户跑：

```sh
npm install -g baozicli   # 或 npx baozicli
baozi serve               # 启动守护进程
baozi pair                # 打印配对二维码，用「包子」App 扫
```

The wrapper itself is a tiny `main()` that re-exports the alleycat daemon with
baozi branding (`binary_name = "baozi"`, identity `com.kris99.baozicli`). All
daemon behavior lives in the alleycat crate; this crate exists so cargo-dist
sees a `baozicli` package name and produces correctly-named artifacts.

## Cutting a release

1. Push the alleycat changes to `huangguang1999/alleycat`.
2. Keep the `alleycat` dependency pinned to a commit and refresh it with
   `./tools/scripts/update-alleycat-main.sh --kittylitter` (or bump the rev by hand).
3. Bump `version` in this crate's `Cargo.toml`.
4. Tag `vX.Y.Z` on the `huangguang1999/baozi` repo. The `release.yml` workflow at
   the repo root builds and publishes `baozicli` to npm via trusted publishing.
