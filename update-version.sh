#!/bin/bash

# JNet 版本号更新与发布脚本
# 用法: ./update-version.sh <版本号>
# 示例: ./update-version.sh 3.4.2 或 ./update-version.sh v3.4.2

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

if [ -z "$1" ]; then
    echo "用法: $0 <版本号>"
    echo "示例: $0 3.4.2 或 $0 v3.4.2"
    exit 1
fi

# 处理版本号格式：兼容 3.4.5 和 v3.4.5 两种输入
INPUT_VERSION="$1"
# 移除可能的 'v' 前缀，确保内部使用纯数字版本号
if [[ "$INPUT_VERSION" == v* ]]; then
    NEW_VERSION="${INPUT_VERSION#v}"  # 去掉开头的 v
else
    NEW_VERSION="$INPUT_VERSION"     # 保持原样
fi

# 用于显示的带v前缀版本号
DISPLAY_VERSION="v$NEW_VERSION"

echo "========================================"
echo "  JNet 版本发布工具"
echo "========================================"
echo "输入: $INPUT_VERSION"
echo "实际版本: $NEW_VERSION"
echo "显示版本: $DISPLAY_VERSION"
echo ""

# ========== 更新文件 ==========

info "1. 更新 pom.xml..."
sed -i.bak "s/<revision>.*<\/revision>/<revision>$NEW_VERSION<\/revision>/" pom.xml
rm -f pom.xml.bak
info "   ✅ pom.xml"

info "2. 更新 README.md..."
sed -E -i.bak "s/<version>[0-9A-Za-z._-]+<\/version>/<version>$NEW_VERSION<\/version>/" README.md
sed -E -i.bak "s/(jnt:)[0-9A-Za-z._-]+/\\1$NEW_VERSION/g" README.md
rm -f README.md.bak
info "   ✅ README.md"

info "3. 更新 docs/data.json..."
sed -i.bak "s/\"version\": *\"[^\"]*\"/\"version\": \"$NEW_VERSION\"/" docs/data.json
sed -i.bak "s/\"releaseName\": *\"[^\"]*\"/\"releaseName\": \"Release v$NEW_VERSION\"/" docs/data.json
rm -f docs/data.json.bak
info "   ✅ docs/data.json"

info "4. 更新 docs/index.html..."
sed -E -i.bak "s/data-version>v?[0-9A-Za-z._-]+</data-version>$DISPLAY_VERSION</" docs/index.html
sed -E -i.bak "s/id=\"footerVersion\">v?[0-9A-Za-z._-]+</id=\"footerVersion\">$DISPLAY_VERSION</" docs/index.html
sed -E -i.bak "s/<span class=\"version-tag\">v?[0-9A-Za-z._-]+<\/span>/<span class=\"version-tag\">$DISPLAY_VERSION<\/span>/" docs/index.html
rm -f docs/index.html.bak
info "   ✅ docs/index.html"

info "5. 更新 docs/search.js..."
sed -E -i.bak "s/title: 'v[0-9A-Za-z._-]+ 版本'/title: '$DISPLAY_VERSION 版本'/" docs/search.js
sed -E -i.bak "s/titleEn: 'v[0-9A-Za-z._-]+ Version'/titleEn: '$DISPLAY_VERSION Version'/" docs/search.js
sed -E -i.bak "s/keywords: \\['v?[0-9A-Za-z._-]+',/keywords: ['$DISPLAY_VERSION',/" docs/search.js
rm -f docs/search.js.bak
info "   ✅ docs/search.js"

info "6. 更新 docs/src/managers/SearchManager.ts..."
sed -E -i.bak "s/title: 'v[0-9A-Za-z._-]+ 版本'/title: '$DISPLAY_VERSION 版本'/" docs/src/managers/SearchManager.ts
sed -E -i.bak "s/titleEn: 'v[0-9A-Za-z._-]+ Version'/titleEn: '$DISPLAY_VERSION Version'/" docs/src/managers/SearchManager.ts
sed -E -i.bak "s/keywords: \\['v?[0-9A-Za-z._-]+',/keywords: ['$DISPLAY_VERSION',/" docs/src/managers/SearchManager.ts
rm -f docs/src/managers/SearchManager.ts.bak
info "   ✅ SearchManager.ts"

info "7. 更新 CI/CD fallback..."
sed -E -i.bak "s/\\|\\| echo \"v?[0-9A-Za-z._-]+\"/|| echo \"$NEW_VERSION\"/" build.sh
sed -E -i.bak "s/\\|\\| echo \"v?[0-9A-Za-z._-]+\"/|| echo \"$NEW_VERSION\"/" .github/workflows/pages.yml
rm -f build.sh.bak .github/workflows/pages.yml.bak
info "   ✅ CI/CD workflows"

info "8. 更新 src/main/java/com/jnet/core/Version.java..."
# 更新 @Version 注释
sed -E -i.bak "s/\\* @Version: [0-9A-Za-z._-]+/* @Version: $NEW_VERSION/" src/main/java/com/jnet/core/Version.java
# 更新 return 语句
sed -E -i.bak "s/return \"[0-9A-Za-z._-]+\";/return \"$NEW_VERSION\";/" src/main/java/com/jnet/core/Version.java
rm -f src/main/java/com/jnet/core/Version.java.bak
info "   ✅ Version.java"

echo ""
echo "========================================"
echo "  ✅ 所有文件已更新完成！"
echo "========================================"
echo ""

# ========== Git 操作 ==========

info "检查 git 仓库..."
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    error "当前目录不是 git 仓库"
    exit 1
fi

info "提交版本号修改..."
FILES=(
    "pom.xml"
    "README.md"
    "docs/data.json"
    "docs/index.html"
    "docs/search.js"
    "docs/src/managers/SearchManager.ts"
    "build.sh"
    ".github/workflows/pages.yml"
    "src/main/java/com/jnet/core/Version.java"
)

for file in "${FILES[@]}"; do
    if [ -f "$file" ] && ! git diff --quiet -- "$file"; then
        git add "$file"
    fi
done
if git diff --cached --quiet; then
    warn "没有检测到文件变更，跳过提交"
else
    git commit -m "chore: bump version to v$NEW_VERSION"
    info "✅ 已提交到 git"
fi

info "创建 tag v$NEW_VERSION..."
TAG_NAME="v$NEW_VERSION"
if git rev-parse -q --verify "refs/tags/$TAG_NAME" > /dev/null; then
    warn "Tag $TAG_NAME 已存在，跳过创建"
    TAG_EXISTS=1
else
    git tag -a "$TAG_NAME" -m "Release $TAG_NAME"
    info "✅ Tag 已创建"
    TAG_EXISTS=0
fi

info "推送代码到远程仓库..."
git push origin main
info "✅ 代码已推送"

if [ "${TAG_EXISTS:-0}" -eq 0 ]; then
    info "推送 tag 到远程仓库..."
    git push origin "$TAG_NAME"
    info "✅ Tag 已推送"
else
    warn "跳过推送已存在的 Tag: $TAG_NAME"
fi

echo ""
echo "========================================"
echo "  🎉 发布完成！"
echo "========================================"
echo ""
echo "GitHub Actions 将自动执行："
echo "  1. 发布到 GitHub Packages"
echo "  2. 创建 GitHub Release"
echo "  3. 上传 JAR 文件"
echo "  4. 更新 GitHub Pages"
echo ""
REPO=$(git remote get-url origin | sed 's/.*github.com[/:]//' | sed 's/.git$//')
echo "查看进度: https://github.com/$REPO/actions"
echo "查看 Release: https://github.com/$REPO/releases/tag/v$NEW_VERSION"
