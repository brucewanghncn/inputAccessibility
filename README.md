# Input Accessibility - 双屏输入辅助

## 项目简介

一款专为双屏安卓设备（目前已适配ayaneo pocket ds）设计的输入辅助应用，通过无障碍服务（Accessibility Service）自动检测主屏幕的输入框焦点，并在副屏显示输入界面，提升双屏设备的输入体验。

## 主要功能

- 🖥️ **双屏支持**：自动在副屏显示输入界面
- ⌨️ **输入同步**：副屏输入框与主屏目标输入框实时同步
- 🔄 **属性同步**：自动同步 hint、文本内容、输入类型和 IME 选项（不一定准确）
- 🌓 **深色模式**：完整支持系统深色模式，自动切换主题
- 🎯 **自动焦点管理**：输入框失焦时自动关闭副屏输入界面

## 使用方法

### 安装配置

1. 安装应用到双屏设备
2. 打开应用，点击"打开无障碍服务设置"
3. 在系统设置中启用"Input Accessibility"服务

### 使用流程

1. 在主屏任意应用中点击输入框
2. 副屏自动弹出输入界面
3. 在副屏输入，内容实时同步到主屏
4. 点击输入法的完成/搜索等按钮，触发主屏对应操作
5. 主屏输入框失焦后，副屏输入界面自动关闭

## 项目结构

```
app/src/main/
├── java/com/brucewang/inputaccessibility/
│   ├── MainActivity.kt                    # 主界面
│   ├── InputAccessibilityService.kt       # 无障碍服务
│   └── InputActivity.kt                   # 副屏输入界面
├── res/
│   ├── layout/
│   │   ├── activity_main.xml              # 主界面布局
│   │   └── activity_input.xml             # 输入界面布局
│   ├── values/
│   │   ├── colors.xml                     # 浅色主题颜色
│   │   ├── strings.xml                    # 字符串资源
│   │   └── themes.xml                     # 主题定义
│   └── values-night/
│       ├── colors.xml                     # 深色主题颜色
│       └── themes.xml                     # 深色主题
└── AndroidManifest.xml
```

## 已知问题

- 部分输入框无法响应Enter操作
- 部分应用使用自定义输入框控件，无法弹出副屏输入界面
- IME选项同步不一定准确
- 输入窗口不一定能自动关闭，此时需要手动关闭
- 在Ayaneo pocket ds仅主屏显示时，代码中无法判断副屏已关闭，仍然会尝试弹出输入界面

## 作者

Bruce Wang

---

**注意**：本应用需要无障碍服务权限，仅用于在副屏显示输入界面，不会收集或上传任何用户数据。

