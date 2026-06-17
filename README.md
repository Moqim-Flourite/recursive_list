# 📋 Recursive List

一个基于「月 → 周 → 日」三层递归结构的 Android 计划管理应用，帮助你从宏观目标拆解到每日行动。

## ✨ 核心功能

### 🎯 三层递归计划体系
- **月计划** — 设定长期目标和时间范围
- **周计划** — 将月目标拆解为每周推进
- **日计划** — 按时段（清晨/上午/中午/下午/晚上）安排具体任务

### 🤖 AI 智能导入
- 一键复制标准模板，粘贴给任意 AI（豆包、Kimi、ChatGPT、Gemini 等）
- AI 生成结构化计划后，粘贴回 APP 自动解析导入
- 支持月/周/日三级计划同时导入，智能时段识别

### 📊 多维能力 Surface
- 自定义主题 & 壁纸（静态/动态）
- 计划完成度统计

### 🔄 自动更新
- APP 内置从 GitHub Release 检查更新功能
- 设置页一键检查更新，对比版本号，下载安装

### 🎨 玻璃态 UI
- Material 3 设计语言
- 毛玻璃效果卡片
- 流畅动画与手势

## 🚀 快速开始

### 环境要求
- Android Studio Hedgehog (2023.1) 或更高
- JDK 17+
- Android SDK 34+
- Kotlin 2.0+

### 构建
```bash
git clone https://github.com/Moqim-Flourite/recursive_list.git
cd recursive_list
./gradlew :app:assembleDebug
```

安装到设备：
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 📦 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin 2.0 | 开发语言 |
| Jetpack Compose | 声明式 UI |
| Material 3 | 设计系统 |
| Room | 本地数据库 |
| Hilt | 依赖注入 |
| Navigation Compose | 页面导航 |
| Coil | 图片加载 |
| OkHttp | 网络请求（更新检查） |

## 🏗️ 项目结构

```
app/src/main/java/com/moqim/list/
├── data/
│   ├── local/          # Room 数据库、DAO、实体
│   └── importplan/     # AI 文本模板 & 解析器
├── di/                 # Hilt 依赖注入
├── feature/
│   ├── home/           # 首页
│   ├── plans/          # 计划中心（核心）
│   ├── surface/        # 壁纸 & 主题
│   └── settings/       # 设置
├── service/            # 壁纸服务
└── ui/theme/           # 主题、颜色、字体
```

## 📝 版本历史

- **v1.3** (2026-06-17) — WiFi 同步功能
  - 手机计划数据通过局域网同步到电脑端
  - mDNS 自动发现 + 缓存地址 + 手动配置 IP 三级 fallback
  - 全量快照：月→周→日计划 + 执行任务 + 习惯模板 + 打卡记录
  - WorkManager 每天凌晨定时同步 + 手动触发
  - 共用 TimeTrackerApp 的 sync-server 电脑端服务

- **v1.0** (2026-05-29) — 首发版本
  - 三层递归计划体系
  - AI 文本导入功能
  - 玻璃态 UI
  - 壁纸系统
  - GitHub 自动更新检查

## 📄 License

MIT License

---

> 🤖 Built with Kotlin & Jetpack Compose
