# Docker 消息通知

Homelab 通过 [ntfy](https://docs.ntfy.sh/) 接收消息。准备一个手机可访问的 HTTPS ntfy 主题，例如 `https://notify.example.com/docker-events`。自建实例可以限制发布和订阅权限。手机端使用有读取权限的令牌，Docker 主机使用有发布权限的令牌。

在 Homelab 的「设置 → Docker 消息推送」填写主题地址和访问令牌，点「保存并连接」，并允许 Android 通知。看到「已连接」后，可以在 Docker 主机上先发一条测试消息：

```sh
curl --fail --silent --show-error \
  --header "Authorization: Bearer ${NTFY_WRITE_TOKEN}" \
  --header "Title: Docker" \
  --data-binary 'Docker notifications are connected.' \
  "${NTFY_TOPIC_URL}"
```

将 `NTFY_TOPIC_URL` 和 `NTFY_WRITE_TOKEN` 替换为实际值。公开主题可省略 Authorization 请求头。

## 转发 Docker 事件

在 Docker 主机上运行以下命令，将容器退出、内存不足和重启事件转发至同一个主题。需要在运行环境设置 `NTFY_TOPIC_URL`，启用鉴权时也需要设置 `NTFY_WRITE_TOKEN`。

```bash
headers=(-H 'Title: Docker')
if [[ -n ${NTFY_WRITE_TOKEN:-} ]]; then
  headers+=(-H "Authorization: Bearer ${NTFY_WRITE_TOKEN}")
fi

docker events --filter type=container --filter event=die \
  --filter event=oom --filter event=restart \
  --format '{{.Action}} {{.Actor.Attributes.name}}' |
while IFS= read -r event; do
  printf '%s' "$event" | curl --fail --silent --show-error --max-time 15 \
    "${headers[@]}" --data-binary @- "$NTFY_TOPIC_URL" >/dev/null
done
```

本例在前台运行。需要持续转发时，在 Docker 主机上用已有的服务管理器运行这段命令。只需发送你想收到的事件，容器名会包含在通知正文中。

Homelab 订阅期间显示一条常驻连接通知。网络恢复后，它会尝试从 ntfy 服务器保存的最后一条消息继续。手机重启或系统强行停止应用后，重新打开 Homelab 可恢复此前启用的订阅。
