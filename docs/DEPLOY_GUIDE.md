# JNet GitHub Pages 部署指南

## 🚀 快速部署

### 1. 本地测试

```bash
cd /Users/sanbo/code/JNet/docs
python3 -m http.server 8000
```

访问: http://localhost:8000

### 2. 功能验证

访问测试页面: http://localhost:8000/test.html

点击"运行所有测试"按钮,确保所有测试通过。

### 3. 部署到 GitHub Pages

#### 方法 1: 自动部署 (推荐)

GitHub Actions 已配置,推送代码后自动部署:

```bash
git add docs/
git commit -m "feat: 更新 GitHub Pages - 多语言和搜索功能"
git push origin main
```

#### 方法 2: 手动部署

```bash
# 确保在项目根目录
cd /Users/sanbo/code/JNet

# 推送 docs 目录到 gh-pages 分支
git subtree push --prefix docs origin gh-pages
```

### 4. 访问部署后的页面

- **GitHub Pages URL**: https://netcapture.github.io/JNet/
- **测试页面**: https://netcapture.github.io/JNet/test.html
