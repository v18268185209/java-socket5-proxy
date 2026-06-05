#!/bin/bash
# 构建脚本 - 本地构建 JAR
set -e

echo "🚀 开始构建 ProxyHub..."

cd "$(dirname "$0")/.."

# 清理并打包
echo "📦 清理旧构建..."
mvn clean

echo "🔨 打包项目..."
mvn package -DskipTests -B

echo "✅ 构建完成！"
echo "📁 JAR 位置: target/proxy-hub-*.jar"

# 生成校验和
md5sum target/*.jar > target/checksum.md5
echo "🔐 校验和: target/checksum.md5"
