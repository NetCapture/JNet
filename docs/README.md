# JNet GitHub Pages 文档

这个目录包含 JNet 项目的现代化 GitHub Pages 网站。

## 📁 文件结构

```
docs/
├── index.html      # 主页面 - 现代化展示网站
├── app.js          # 动态内容加载器
└── README.md       # 本文档
```

## 🚀 网站特性

### 1. **现代化设计**
- 🎨 渐变背景和动画效果
- ✨ 浮动粒子背景
- 💫 交互式悬停效果
- 📱 完全响应式设计

### 2. **动态内容**
- 📊 实时 GitHub 统计数据（Stars, Forks, Issues）
- 📦 自动获取最新 Release 信息
- 🔄 每次更新自动同步版本信息
- ⚡ 实时更新发布时间

### 3. **功能展示**
- 🎯 核心特性卡片
- 💻 语法高亮代码示例
- 📊 性能对比表格
- 🏗️ 架构设计图
- 📝 版本更新日志

### 4. **交互体验**
- 🎬 滚动动画（Fade-in）
- ⌨️ 键盘快捷键（G: GitHub, D: Download）
- 🖱️ 悬停效果
- 📱 移动端优化

## 🔄 自动化更新

GitHub Pages 通过 `pages.yml` workflow 自动更新：

### 触发条件
- 推送到 `main` 分支（当 `update.md`, `README.md`, `docs/**` 变化时）
- 新 Release 发布时
- 手动触发

### 更新内容
1. **版本信息** - 从 GitHub API 获取最新 Release
2. **统计数据** - Stars, Forks, Issues
3. **更新时间** - 自动记录最后更新时间
4. **变更日志** - 解析 Release Notes

## 🎨 设计亮点

### 视觉效果
- **渐变背景**: 深色主题 + 动态渐变
- **粒子动画**: 20个浮动粒子，随机轨迹
- **卡片悬停**: 上浮 + 阴影 + 边框动画
- **按钮特效**: 涟漪扩散效果

### 用户体验
- **状态指示器**: 右上角实时状态显示
- **平滑滚动**: 锚点链接平滑跳转
- **延迟加载**: 元素按顺序淡入
- **键盘导航**: 快捷键支持

### 内容组织
- **Hero Section**: 项目标题和 CTA 按钮
- **Stats**: 关键数据展示
- **Features**: 6大核心特性
- **Code Demo**: 语法高亮代码
- **Performance**: 对比表格
- **Architecture**: 架构图和设计模式
- **Changelog**: 版本更新记录
- **Final CTA**: 最终号召行动

## 🛠️ 技术栈

### 前端
- **HTML5**: 语义化结构
- **CSS3**: CSS 变量、动画、Flexbox/Grid
- **JavaScript (ES6+)**: 异步编程、API 调用

### 数据源
- **GitHub API**: 实时获取仓库和 Release 数据
- **本地 JSON**: 版本信息缓存

### 部署
- **GitHub Pages**: 静态托管
- **GitHub Actions**: 自动化部署

## 📊 性能优化

1. **单文件部署**: 所有资源内联，减少 HTTP 请求
2. **懒加载动画**: 滚动时触发，减少初始加载压力
3. **缓存策略**: API 数据缓存，避免频繁请求
4. **CSS 优化**: 使用 CSS 变量，减少重复代码

## 🔧 自定义配置

### 修改主题颜色
在 `index.html` 的 `<style>` 标签中修改 CSS 变量：
```css
:root {
    --primary: #2563eb;      /* 主色调 */
    --secondary: #10b981;    /* 辅助色 */
    --accent: #f59e0b;       /* 强调色 */
}
```

### 修改粒子数量
在 `index.html` 的内联脚本中：
```javascript
const particleCount = 20;  // 修改为想要的数量
```

### 修改动画速度
在 CSS 中调整：
```css
@keyframes float {
    0%, 100% { ... }
    100% { transform: translateY(-100vh); }  /* 修改移动距离 */
}
```

## 🚦 使用指南

### 本地测试
```bash
# 进入 docs 目录
cd docs

# 启动本地服务器（Python）
python -m http.server 8000

# 或使用 Node.js
npx http-server -p 8000

# 访问 http://localhost:8000
```

### 部署到 GitHub Pages
```bash
# 1. 提交 docs 目录
git add docs/
git commit -m "更新 GitHub Pages"

# 2. 推送到 main 分支
git push origin main

# 3. GitHub Actions 会自动部署
# 或手动触发 workflow
```

## 📝 更新流程

### 每次发布新版本时

1. **更新 `update.md`**
   ```markdown
   ## 🚀 3.5.0 (2025-01-XX)

   ### ✨ 新增功能
   - 功能描述...

   ### 🔧 改进
   - 改进描述...

   ### 🐛 修复
   - 修复描述...
   ```

2. **创建 Release**
   ```bash
   git tag v3.5.0
   git push origin v3.5.0
   ```

3. **GitHub Pages 自动更新**
   - workflow 触发
   - 获取最新 Release 信息
   - 更新页面数据
   - 部署到 GitHub Pages

## 🎯 页面结构说明

### 1. Hero Section
- 项目标题和副标题
- 4个核心徽章
- 3个 CTA 按钮

### 2. Stats Section
- GitHub Stars
- Forks
- Open Issues
- 核心代码行数

### 3. Features Section
- 本期亮点（动态）
- 6个核心特性卡片

### 4. Code Demo
- 5个代码示例
- 语法高亮
- 悬停交互

### 5. Performance
- 4个库对比
- 7个维度对比
- 高亮 JNet 优势

### 6. Architecture
- 架构层次图
- 6种设计模式标签

### 7. Changelog
- 最新版本详情
- 分类展示（新增/改进/修复）
- Release 链接

### 8. Final CTA
- 号召行动
- GitHub 链接
- 文档链接

### 9. Footer
- 快速链接
- 版权信息
- 版本号
- 更新时间

## 🔍 API 调用说明

### GitHub API 端点
```javascript
// 仓库信息
GET https://api.github.com/repos/NetCapture/JNet

// 最新 Release
GET https://api.github.com/repos/NetCapture/JNet/releases/latest

// Readme
GET https://api.github.com/repos/NetCapture/JNet/readme
```

### 数据格式
```json
{
  "lastUpdate": "2026-01-01T16:00:00Z",
  "version": "3.4.1",
  "releaseDate": "2026-01-01",
  "releaseName": "Release v3.4.1",
  "releaseBody": "...",
  "stats": {
    "stars": 123,
    "forks": 45,
    "issues": 6
  }
}
```

## 🐛 故障排除

### 页面不更新
1. 检查 GitHub Actions 是否成功运行
2. 查看 workflow 日志
3. 清除浏览器缓存（Ctrl+Shift+R）

### API 限流
- GitHub API 有速率限制（60次/小时未认证）
- 页面会优雅降级，显示静态数据

### 样式显示异常
1. 检查浏览器是否支持 CSS 变量
2. 确认 JavaScript 是否启用
3. 查看控制台错误信息

## 📈 未来计划

### v1.0 (当前)
- ✅ 现代化设计
- ✅ 动态数据加载
- ✅ 自动化部署
- ✅ 响应式设计

### v2.0 (规划中)
- [ ] 多语言支持（中/英）
- [ ] 交互式代码编辑器
- [ ] 实时性能测试
- [ ] 用户反馈系统

### v3.0 (远期)
- [ ] 3D 架构可视化
- [ ] 视频演示
- [ ] 交互式教程
- [ ] 社区贡献展示

## 📞 联系方式

- **GitHub**: https://github.com/NetCapture/JNet
- **Issues**: https://github.com/NetCapture/JNet/issues
- **文档**: https://netcapture.github.io/JNet/

## 📄 许可证

Apache 2.0 License

---

**最后更新**: 2024-12-31
**维护者**: sanbo
