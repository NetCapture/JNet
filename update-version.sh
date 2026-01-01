#!/bin/bash

# JNet 版本号更新与发布脚本
# 用法: ./update-version.sh <版本号>
# 示例: ./update-version.sh 3.4.2

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
    echo "示例: $0 3.4.2"
    exit 1
fi

NEW_VERSION="$1"

echo "========================================"
echo "  JNet 版本发布工具"
echo "========================================"
echo "新版本: v$NEW_VERSION"
echo ""

# ========== 更新文件 ==========

info "1. 更新 pom.xml..."
sed -i.bak "s/<revision>.*<\/revision>/<revision>$NEW_VERSION<\/revision>/" pom.xml
rm -f pom.xml.bak
info "   ✅ pom.xml"

info "2. 更新 README.md..."
sed -i.bak "s/<version>.*<\/version>/<version>$NEW_VERSION<\/version>/" README.md
sed -i.bak "s/jnt:[0-9.]*\"/jnt:$NEW_VERSION\"/" README.md
rm -f README.md.bak
info "   ✅ README.md"

info "3. 更新 docs/data.json..."
sed -i.bak "s/\"version\": *\"[^\"]*\"/\"version\": \"$NEW_VERSION\"/" docs/data.json
sed -i.bak "s/\"releaseName\": *\"[^\"]*\"/\"releaseName\": \"Release v$NEW_VERSION\"/" docs/data.json
rm -f docs/data.json.bak
info "   ✅ docs/data.json"

info "4. 更新 docs/index.html..."
sed -i.bak "s/data-version>[0-9.]*</data-version>$NEW_VERSION</" docs/index.html
sed -i.bak "s/id=\"footerVersion\">v[0-9.]*</id=\"footerVersion\">v$NEW_VERSION</" docs/index.html
sed -i.bak "s/<span class=\"version-tag\">v[0-9.]*<\/span>/<span class=\"version-tag\">v$NEW_VERSION<\/span>/" docs/index.html
rm -f docs/index.html.bak
info "   ✅ docs/index.html"

info "5. 更新 docs/search.js..."
sed -i.bak "s/title: 'v[0-9.]* 版本'/title: 'v$NEW_VERSION 版本'/" docs/search.js
sed -i.bak "s/titleEn: 'v[0-9.]* Version'/titleEn: 'v$NEW_VERSION Version'/" docs/search.js
sed -i.bak "s/keywords: \['[0-9.]*',/keywords: ['$NEW_VERSION',/" docs/search.js
rm -f docs/search.js.bak
info "   ✅ docs/search.js"

info "6. 更新 docs/src/managers/SearchManager.ts..."
sed -i.bak "s/title: 'v[0-9.]* 版本'/title: 'v$NEW_VERSION 版本'/" docs/src/managers/SearchManager.ts
sed -i.bak "s/titleEn: 'v[0-9.]* Version'/titleEn: 'v$NEW_VERSION Version'/" docs/src/managers/SearchManager.ts
sed -i.bak "s/keywords: \['[0-9.]*',/keywords: ['$NEW_VERSION',/" docs/src/managers/SearchManager.ts
rm -f docs/src/managers/SearchManager.ts.bak
info "   ✅ SearchManager.ts"

info "7. 更新 CI/CD fallback..."
sed -i.bak "s/|| echo \"[0-9.]*\"/|| echo \"$NEW_VERSION\"/" build.sh
sed -i.bak "s/|| echo \"[0-9.]*\"/|| echo \"$NEW_VERSION\"/" .github/workflows/pages.yml
rm -f build.sh.bak .github/workflows/pages.yml.bak
info "   ✅ CI/CD workflows"

info "8. 更新 src/main/java/com/jnet/core/Version.java..."
# 更新 @Version 注释
sed -i.bak "s/\* @Version: [0-9.]*/* @Version: $NEW_VERSION/" src/main/java/com/jnet/core/Version.java
# 更新 return 语句
sed -i.bak "s/return \"[0-9.]*\";/return \"$NEW_VERSION\";/" src/main/java/com/jnet/core/Version.java
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
git add pom.xml README.md docs/ build.sh .github/workflows/pages.yml src/main/java/com/jnet/core/Version.java
if git diff --cached --quiet; then
    warn "没有检测到文件变更，跳过提交"
else
    git commit -m "chore: bump version to v$NEW_VERSION"
    info "✅ 已提交到 git"
fi

info "创建 tag v$NEW_VERSION..."
git tag -a "v$NEW_VERSION" -m "Release v$NEW_VERSION"
info "✅ Tag 已创建"

info "推送代码到远程仓库..."
git push origin main
info "✅ 代码已推送"

info "推送 tag 到远程仓库..."
git push origin "v$NEW_VERSION"
info "✅ Tag 已推送"

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
