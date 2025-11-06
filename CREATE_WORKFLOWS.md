# 📝 GitHub Actions 工作流配置说明

## ✅ 已完成的配置

1. ✅ pom.xml - 配置GitHub Packages发布
2. ✅ settings.xml - 配置Maven认证
3. ✅ 两个工作流文档已创建

## ⚠️ 需要手动创建的文件

需要在 `.github/workflows/` 目录下创建以下两个YAML文件：

### 方法1: 通过GitHub Web界面创建

1. 访问 https://github.com/NetCapture/JNet
2. 点击 "Actions" 标签
3. 点击 "New workflow"
4. 选择 "set up a workflow yourself"
5. 复制下方代码并粘贴

### 方法2: 本地创建后推送

在项目根目录执行：

```bash
# 创建目录
mkdir -p .github/workflows

# 创建CI工作流
cat > .github/workflows/ci.yml << 'EOF'
name: CI Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

env:
  JAVA_VERSION: '11'

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'

      - name: Cache Maven dependencies
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: |
            ${{ runner.os }}-m2

      - name: Build with Maven
        run: |
          mvn clean test
EOF

# 创建Release工作流
cat > .github/workflows/release.yml << 'EOF'
name: Release Build and Publish

on:
  push:
    tags:
      - 'v*.*.*'

env:
  JAVA_VERSION: '11'

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK ${{ env.JAVA_VERSION }}
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          server-id: github
          server-username: GITHUB_USER
          server-password: GITHUB_TOKEN

      - name: Configure Maven settings
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << 'SETTINGS'
          <?xml version="1.0" encoding="UTF-8"?>
          <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
            <servers>
              <server>
                <id>github</id>
                <username>github-actions[bot]</username>
                <password>\${{ secrets.GITHUB_TOKEN }}</password>
              </server>
            </servers>
          </settings>
          SETTINGS

      - name: Extract version from tag
        id: version
        run: |
          TAG=${GITHUB_REF#refs/tags/v}
          echo "version=$TAG" >> $GITHUB_OUTPUT

      - name: Build with Maven
        run: |
          mvn clean package -DskipTests -Drevision=${{ steps.version.outputs.version }}

      - name: Publish to GitHub Packages
        run: |
          mvn clean deploy -DskipTests -Dgpg.skip=true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: v${{ steps.version.outputs.version }}
          name: Release v${{ steps.version.outputs.version }}
          draft: false
          prerelease: false
          files: |
            target/jnt-${{ steps.version.outputs.version }}.jar
            target/jnt-${{ steps.version.outputs.version }}-sources.jar
            target/jnt-${{ steps.version.outputs.version }}-javadoc.jar
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
EOF

# 提交更改
git add .github/
git commit -m "✨ Add GitHub Actions workflows"
git push origin main
```

## 🔑 必需配置

### 1. 启用GitHub Packages
- 仓库 → Settings → Packages → 勾选 "GitHub Packages"

### 2. Actions权限
- 仓库 → Settings → Actions → General
- Workflow permissions: "Read and write permissions"

## 🚀 测试工作流

创建工作流文件后，测试自动发布：

```bash
# 创建并推送标签
git tag v3.0.0
git push origin v3.0.0

# 查看Actions: https://github.com/NetCapture/JNet/actions
```

## 📚 查看文档

- `QUICK_START_GITHUB_ACTIONS.md` - 快速入门
- `WORKFLOW_SUMMARY.md` - 详细说明
- `GitHub_Packages_使用指南.md` - 发布配置

---

⚠️ **注意**: 这是必要的配置步骤，因为某些系统不允许通过API自动创建 .github 目录。
