# 🔒 GitHub Packages 授权修复指南

## ❌ 错误信息
```
Failed to deploy artifacts: Could not transfer artifact
from/to github: status code: 401, reason phrase: Unauthorized
```

## 🔍 原因分析
构建成功，但发布失败，因为GitHub Actions没有权限向GitHub Packages发布。

## ✅ 必须完成的配置

### 第一步：启用GitHub Packages

1. 打开GitHub仓库页面：https://github.com/NetCapture/JNet
2. 点击顶部的 **Settings** 标签（需要管理员权限）
3. 在左侧菜单中找到 **"Packages"** 选项
4. 在 "GitHub Packages" 部分，**勾选 "GitHub Packages"**
5. 点击底部的 **"Save"** 按钮

![GitHub Packages设置位置](https://docs.github.com/assets/cb-26607/images/help/packages/enable-github-packages.png)

### 第二步：设置Actions权限

1. 在同一个Settings页面，点击左侧 **"Actions"**
2. 找到 **"Workflow permissions"** 部分
3. 选择 **"Read and write permissions"**
4. ✅ 勾选 "Allow GitHub Actions to create and approve pull requests"
5. 点击 **"Save"** 按钮

### 第三步：验证Token权限（可选）

检查GITHUB_TOKEN是否自动配置：
- 默认已配置，无需手动设置
- 权限应该包含：`packages: write`, `contents: read`

## 🔄 重新触发发布

完成配置后，重新推送标签：

```bash
git tag v3.0.0
git push origin v3.0.0
```

## 📊 预期结果

约5-10分钟后：
- ✅ 构建成功
- ✅ 发布到GitHub Packages成功
- ✅ 创建GitHub Release成功

## 🔍 验证步骤

### 1. 检查Actions运行
访问：https://github.com/NetCapture/JNet/actions

应该看到：
- Status: ✅ completed
- Conclusion: success

### 2. 检查Packages
访问：https://github.com/NetCapture/JNet/packages

应该看到：
- jnt 3.0.0 包

### 3. 检查Releases
访问：https://github.com/NetCapture/JNet/releases

应该看到：
- Release v3.0.0
- JAR文件作为Assets

## ❗ 常见问题

### Q: 看不到Settings标签？
**A:** 需要仓库管理员权限。联系仓库所有者。

### Q: 配置后仍失败？
**A:**
1. 等待2-3分钟让配置生效
2. 删除并重新推送标签

### Q: 如何删除并重新推送标签？
```bash
git tag -d v3.0.0
git tag v3.0.0
git push --delete origin v3.0.0
git push origin v3.0.0
```

## 📞 需要帮助？

配置完成后运行：
```bash
# 重新推送标签
git tag v3.0.0 && git push origin v3.0.0

# 等待5分钟，然后检查：
# https://github.com/NetCapture/JNet/actions
# https://github.com/NetCapture/JNet/packages
# https://github.com/NetCapture/JNet/releases
```

---

**注意**: GitHub Packages功能需要仓库是公开的，或拥有GitHub Pro/Team许可证。
