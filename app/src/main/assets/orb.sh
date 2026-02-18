#!/bin/sh
# Orb Eye CLI — 供 BotDrop/OpenClaw 通过 exec 调用
# 用法: orb.sh <子命令> [参数...]
# 依赖: curl，Orb Eye 服务需运行在 http://127.0.0.1:7333

BASE="http://127.0.0.1:7333"
cmd="${1:-}"

case "$cmd" in
ping)
  curl -s "$BASE/ping"
  ;;
info)
  curl -s "$BASE/info"
  ;;
screen)
  curl -s "$BASE/screen"
  ;;
tree)
  curl -s "$BASE/tree"
  ;;
notify)
  curl -s "$BASE/notify"
  ;;
wait)
  curl -s "$BASE/wait?timeout=${2:-5000}"
  ;;
click)
  if [ -z "$2" ]; then
    echo '{"ok":false,"error":"usage: orb.sh click \"text\""}'
    exit 1
  fi
  curl -s -X POST "$BASE/click" -H "Content-Type: application/json" -d "{\"text\":\"$2\"}"
  ;;
tap)
  if [ -z "$2" ] || [ -z "$3" ]; then
    echo '{"ok":false,"error":"usage: orb.sh tap X Y"}'
    exit 1
  fi
  curl -s -X POST "$BASE/tap" -H "Content-Type: application/json" -d "{\"x\":$2,\"y\":$3}"
  ;;
setText)
  if [ -z "$2" ]; then
    echo '{"ok":false,"error":"usage: orb.sh setText \"text\""}'
    exit 1
  fi
  curl -s -X POST "$BASE/setText" -H "Content-Type: application/json" -d "{\"text\":\"$2\"}"
  ;;
swipe)
  if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ] || [ -z "$5" ]; then
    echo '{"ok":false,"error":"usage: orb.sh swipe x1 y1 x2 y2 [duration_ms]"}'
    exit 1
  fi
  curl -s -X POST "$BASE/swipe" -H "Content-Type: application/json" \
    -d "{\"x1\":$2,\"y1\":$3,\"x2\":$4,\"y2\":$5,\"duration\":${6:-300}}"
  ;;
back)
  curl -s -X POST "$BASE/back"
  ;;
home)
  curl -s -X POST "$BASE/home"
  ;;
*)
  echo "Orb Eye CLI — usage: orb.sh <command> [args]"
  echo "  ping | info | screen | tree | notify | wait [timeout_ms]"
  echo "  click \"<text>\" | tap <x> <y> | setText \"<text>\""
  echo "  swipe <x1> <y1> <x2> <y2> [duration] | back | home"
  exit 1
  ;;
esac
