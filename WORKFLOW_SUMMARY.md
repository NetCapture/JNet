# 🔄 GitHub Actions 工作流完整配置

## 📊 工作流概览

本项目已配置两套GitHub Actions工作流：

### 1. CI工作流 (ci.yml)
**触发条件**: 每次推送代码到 main/develop 分支 或 创建Pull Request

**功能**:
- ✅ 自动运行单元测试
- ✅ 构建JAR文件
- ✅ 缓存Maven依赖
- ✅ 安全扫描（OWASP Dependency Check）
- ✅ 上传测试报告和构建产物

**运行时间**: ~3-5分钟

### 2. Release工作流 (release.yml)
**触发条件**: 推送格式为 `v*.*.*` 的Git标签

**功能**:
- ✅ 自动构建项目
- ✅ 发布到GitHub Packages
- ✅ 创建GitHub Release
- ✅ 上传JAR文件到Release Assets
- ✅ 生成详细的发布说明

**运行时间**: ~5-8分钟

## 🚀 使用流程

### 开发阶段 (CI)

```bash
# 1. 开发代码
git checkout -b feature/new-feature
# ... 编写代码 ...

# 2. 推送代码（自动触发CI）
git add .
git commit -m "Add new feature"
git push origin feature/new-feature

# 3. 创建Pull Request到main分支
# CI会自动运行测试和构建
```

### 发布阶段 (Release)

```bash
# 1. 切换到main分支并拉取最新代码
git checkout main
git pull origin main

# 2. 创建发布标签
git tag v3.0.0

# 3. 推送标签（自动触发Release）
git push origin v3.0.0

# 4. GitHub Actions自动完成：
#    - 构建项目
#    - 发布到GitHub Packages
#    - 创建GitHub Release
```

## 📁 工作流文件结构

```
.github/
└── workflows/
    ├── ci.yml          # CI工作流：测试和构建
    └── release.yml     # Release工作流：发布和创建Release
```

## ⚙️ 配置要求

### 必须配置

1. **GitHub Packages启用**
   - 仓库 → Settings → Packages → 启用GitHub Packages

2. **Actions权限**
   - 仓库 → Settings → Actions → General → Workflow permissions
   - 选择 "Read and write permissions"

### 可选配置（Secrets）

如果想发布到Maven Central，添加以下Secrets：

| 名称 | 描述 | 示例 |
|------|------|------|
| OSSRH_USERNAME | Sonatype用户名 | your-username |
| OSSRH_TOKEN | Sonatype Token | your-token |
| GPG_KEYNAME | GPG密钥ID | 你的密钥ID |
| GPG_PRIVATE_KEY | GPG私钥 | -----BEGIN PGP PRIVATE KEY-----... |
| GPG_PASSPHRASE | GPG密码 | your-passphrase |

**注意**: GITHUB_TOKEN无需配置，系统自动提供。

## 🎯 实际演示

### 推送标签示例

```bash
# 创建并推送v3.0.0标签
git tag v3.0.0
git push origin v3.0.0

# 约5分钟后，查看结果：
# - Actions: https://github.com/NetCapture/JNet/actions
# - Release: https://github.com/NetCapture/JNet/releases
# - Packages: https://github.com/NetCapture/JNet/packages
```

### Release页面效果

Release工作流会自动创建包含以下内容的发布页面：

```
🎉 JNet Release 3.0.0

📦 构建信息
- 版本: 3.0.0
- 构建时间: 2025-11-06 12:59:00
- Java版本: 11

📋 包含内容
- jnt-3.0.0.jar
- jnt-3.0.0-sources.jar
- jnt-3.0.0-javadoc.jar

📥 Maven依赖坐标
<dependency>
  <groupId>com.netcapture</groupId>
  <artifactId>jnt</artifactId>
  <version>3.0.0</version>
</dependency>
```

## 📊 工作流状态徽章

可以在README.md中添加状态徽章：

```markdown
![CI](https://github.com/NetCapture/JNet/workflows/CI%20Build%20and%20Test/badge.svg)
![Release](https://github.com/NetCapture/JNet/workflows/Release%20Build%20and%20Publish/badge.svg)
```

## 🔍 监控和调试

### 查看工作流执行

1. **Actions页面**: https://github.com/NetCapture/JNet/actions
   - 查看所有工作流运行历史
   - 每个步骤的详细日志
   - 构建时间和状态

2. **单个运行详情**
   - 点击具体的工作流run
   - 查看每个job的日志
   - 下载构建产物

### 常见问题

**问题1**: PR提交后CI不运行
**解决**: 检查Actions权限设置

**问题2**: 推送标签后Release不触发
**解决**: 确保标签格式为 `v*.*.*` (如 v3.0.0)

**问题3**: 构建失败
**解决**: 查看Actions日志，定位失败步骤

**问题4**: 依赖无法下载
**解决**: 确保已启用GitHub Packages功能

## 📚 更多资源

- [GitHub Actions文档](https://docs.github.com/en/actions)
- [工作流语法参考](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions)
- [Actions市场](https://github.com/marketplace/actions)
- [GitHub Packages文档](https://docs.github.com/en/packages)
