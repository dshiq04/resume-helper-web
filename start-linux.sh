#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

echo
echo " 履途 ResumeTrail"
echo " ----------------------------------------"
echo

command -v java >/dev/null 2>&1 || { echo "[错误] 未找到 Java，请安装 JDK 21 或更高版本。"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "[错误] 未找到 Node.js，请安装 Node.js 20 或更高版本。"; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "[错误] 未找到 npm。"; exit 1; }

if command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
elif [ -x "./mvnw" ]; then
  MVN="./mvnw"
else
  echo "[错误] 未找到 Maven，请安装 Maven 3.9 或更高版本。"
  exit 1
fi

PORT=$(python3 -c "import json;print(json.load(open('config.json'))['server']['port'])" 2>/dev/null || true)
if [ -z "$PORT" ]; then
  PORT=$(grep -o '"port"[[:space:]]*:[[:space:]]*[0-9]*' config.json | head -1 | grep -o '[0-9]*' || true)
fi
PORT=${PORT:-18080}
URL="http://127.0.0.1:${PORT}"

if curl -fsS "${URL}/api/health" >/dev/null 2>&1; then
  echo "[信息] 服务已在运行：${URL}"
  (xdg-open "${URL}" >/dev/null 2>&1 || true)
  exit 0
fi

if [ ! -d "frontend/node_modules" ]; then
  echo "[1/3] 安装前端依赖..."
  (cd frontend && npm install --no-audit --no-fund)
fi

echo "[2/3] 构建前端..."
(cd frontend && npm run build)

echo "[3/3] 打包服务端..."
${MVN} -q -DskipTests package
# IDE 的 Java 扩展有时会同时编译，导致模块 jar 为空；检测后重试一次。
if ! jar tf jobtrack-common/target/jobtrack-common-1.0.0.jar 2>/dev/null | grep -q '\.class'; then
  echo "[提示] 检测到模块编译竞争，正在重新打包..."
  sleep 3
  ${MVN} -q -DskipTests package
fi

mkdir -p log
echo "[启动] 正在启动服务端..."
nohup java -jar jobtrack-web/target/jobtrack.jar >> log/console.log 2>&1 &
echo $! > .jobtrack.pid

READY=0
for _ in $(seq 1 90); do
  if curl -fsS "${URL}/api/health" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 1
done

if [ "$READY" = "1" ]; then
  echo "[完成] 服务已启动：${URL}"
  (xdg-open "${URL}" >/dev/null 2>&1 || true)
else
  echo "[警告] 服务启动超时，请查看 log/console.log。"
  echo "        稍后可以手动打开：${URL}"
fi

echo
echo "默认账号：admin / 123456"
echo "配置文件：config.json"
echo "日志目录：log/"
echo "停止服务：kill \$(cat .jobtrack.pid)"
echo
