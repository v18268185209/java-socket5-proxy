#!/bin/bash
# 一键部署脚本
set -e

echo "🚀 开始部署 ProxyHub..."

cd "$(dirname "$0")/.."

# 检查 Docker
if ! command -v docker &> /dev/null; then
  echo "❌ Docker 未安装，请先安装 Docker"
  exit 1
fi

# 构建
echo "🔨 构建 Docker 镜像..."
./scripts/docker-build.sh

# 部署
echo "📦 启动容器..."
cd docker
docker compose up -d

echo "✅ 部署完成！"
echo "🌐 管理界面: http://localhost:9090"
echo "🔌 SOCKS5: localhost:1080"
echo "🔌 HTTP: localhost:8080"
echo ""
echo "📋 查看日志: docker compose -f docker/docker-compose.yml logs -f"
echo "🛑 停止: docker compose -f docker/docker-compose.yml down"
