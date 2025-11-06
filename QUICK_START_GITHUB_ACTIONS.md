# ⚡ GitHub Actions 快速入门

## 🚀 一键发布

推送Git标签即可自动发布！

```bash
# 发布新版本
git tag v3.0.0
git push origin v3.0.0

# 🎉 等待5分钟，自动完成：
#   ✅ 构建JAR文件
#   ✅ 发布到GitHub Packages
#   ✅ 创建GitHub Release
#   ✅ 生成下载链接
```

## 📍 查看结果

- **构建进度**: https://github.com/NetCapture/JNet/actions
- **发布页面**: https://github.com/NetCapture/JNet/releases
- **下载JAR**: 点击Release中的Assets下载
- **Maven依赖**: https://github.com/NetCapture/JNet/packages

## 📦 Maven坐标

```xml
<dependency>
  <groupId>com.netcapture</groupId>
  <artifactId>jnt</artifactId>
  <version>3.0.0</version>
</dependency>
```

## ⚙️ 一次性配置

**必须操作**：在GitHub Web界面启用Packages功能

1. 访问: https://github.com/NetCapture/JNet/settings
2. 点击左侧 "Packages"
3. 勾选 "GitHub Packages"
4. 保存

## 🛠️ 两种工作流

| 工作流 | 触发条件 | 功能 |
|--------|----------|------|
| **CI** | 推送代码/PR | 🧪 运行测试<br>📦 构建JAR<br>🔒 安全扫描 |
| **Release** | 推送标签 `v*.*.*` | 📤 发布到Packages<br>🎉 创建Release<br>⬇️ 上传JAR文件 |

## 🎯 下一步

推送标签测试自动发布：

```bash
git tag v3.0.0 && git push origin v3.0.0
```

然后访问 https://github.com/NetCapture/JNet/actions 查看构建进度！

## 📚 完整文档

- 📘 [发布指南](GitHub_Packages_使用指南.md) - 详细的GitHub Packages配置
- 📘 [Actions使用](GITHUB_ACTIONS_RELEASE.md) - GitHub Actions完整说明
- 📘 [工作流概述](WORKFLOW_SUMMARY.md) - 两套工作流详细介绍
