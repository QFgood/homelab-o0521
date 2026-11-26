# GitHub 应用更新

带 `-PenableUpdateCheck=true` 构建的独立 APK 会在「设置 → GitHub 更新」提供检查、下载和安装入口。更新源默认是 `QFgood/homelab-o0521`，可以在构建时用 `-PgithubUpdateRepository=OWNER/REPO` 指定。安卓应用分发渠道使用默认构建配置时不会主动查询 GitHub。

更新检查只读取 GitHub 最新稳定 Release。要提供可安装更新，需要发布形如 `v1.6.8` 的 Release，并将应用 APK 上传为 `Homelab.apk`。该 APK 必须使用相同的 Android 包名及签名证书，且 `versionCode` 更高。独立构建和后续发布应使用同一签名密钥。首次安装更新时，根据 Android 提示授权 Homelab 安装应用。

使用 GitHub Actions 发布时，在仓库 Actions secrets 中配置这四个值，工作流会用该密钥签名并开启应用内更新。

- `HOMELAB_SIGNING_KEYSTORE_BASE64`（签名库文件的 Base64 内容）
- `HOMELAB_SIGNING_STORE_PASSWORD`（签名库密码）
- `HOMELAB_SIGNING_KEY_ALIAS`（签名密钥的别名）
- `HOMELAB_SIGNING_KEY_PASSWORD`（签名密钥密码）
