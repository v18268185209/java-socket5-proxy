#!/bin/bash
# Docker 构建脚本
set -e

echo "🚀 开始构建 Docker 镜像..."

cd "$(dirname "$0")/.."

docker compose -f docker/docker-compose.yml build

echo "✅ Docker 镜像构建完成！"
echo "📊 镜像列表:"
docker images | grep proxy-hub
