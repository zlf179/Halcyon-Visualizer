<!--suppress ALL -->

<h1 align="center">Halcyon</h1>

<p align="center">
  <b>一款贴近 MIUI / HyperOS 体验的 Android 音乐播放器</b>
</p>


---

## ✨ 项目简介

**Halcyon** 是一款基于 **Jetpack Compose、Miuix 和 AndroidX Media3** 构建的 Android 本地音乐播放器。功能不再赘述。
本项目主要基于AI vibe coding进行开发，使用Halcyon作为基础，把其中的音乐可视化效果用sonic topography替换。但是效果还不是太完美，脉冲和脉冲带来的涟漪效果不能根据每一次Beat来进行显示。因此开源到Github希望有人能解决。

---

## 📱 运行要求

| 项目 | 要求                              |
|:--|:--------------------------------|
| Android 版本 | Android 11 / API 30 或更高版本       |
| Target SDK | Android 17 / API 37             |
| 默认 ABI | `arm64-v8a`                     |
| 网络 | WebDAV、LX 在线音源和在线歌词需要网络         |
| 视频权限 | Android 13+ 使用动态视频封面时可能需要视频媒体权限 |
| 悬浮窗权限 | 使用桌面歌词时需要                       |
| 通知权限 | Android 13 及以上需要                |

---


## 🛠 构建

```bash
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
./gradlew :app:assembleDebug -PellaAbi=arm64-v8a
```

Windows PowerShell：

```powershell
git clone https://github.com/Kifranei/Halcyon.git
cd Halcyon
.\gradlew.bat :app:assembleDebug -PellaAbi=arm64-v8a
```

Release 构建会优先读取以下环境变量：

```bash
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

如果未设置这些变量，会使用项目根目录下的 `release.jks`；如果没有可用的 release keystore，则 release 构建会直接失败，避免误产出 debug 签名的 release 包。

日常开发建议使用 `assembleDebug` 验证；`fastRelease` / release 构建仅在发版时使用。默认 native 库走预编译 `.so` 打包，如需更新 FFmpeg 或 lyrico-audiotag native，再手动运行对应脚本重新生成。提交后请同时推送 GitHub 与 GitLab 远端。

---

## 🎧 native 库

预编译的 FFmpeg 与 lyrico-audiotag native 库默认位于：

```text
ffmpeg-decoder/src/main/jniLibs/arm64-v8a/libffmpegJNI.so
lyrico-audiotag/src/main/jniLibs/arm64-v8a/liblyrico_taglib.so
```

如需在 fresh clone 后恢复 FFmpeg 预编译输入，请先运行：

```powershell
.\scripts\download_ffmpeg_prebuilt.ps1
```

如需在 Windows 上手动更新 FFmpeg native，请运行：

```powershell
.\build_ffmpeg.ps1
```

如需更新 lyrico-audiotag / TagLib native 产物，请运行：

```powershell
.\build_lyrico_taglib.ps1
```

普通 `assembleDebug` 不会默认重新编译 native；发版前确认 APK 内包含所需 arm64-v8a `.so`。如果只需要日常构建，无需拉取完整 FFmpeg 源码。

`liblyrico_taglib.so` 是 lyrico-audiotag 的 native 标签读写产物，用于本地音频文件的元数据读写。

---

## 🧱 开源与许可

Halcyon 主项目以 **Apache-2.0** 协议开源。第三方组件保留其各自许可证，详见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

---

## 👥 致谢

- **BetterLyrics** — 为模糊封面背景和歌词展示提供视觉参考。
- **Beautiful Lyrics** — 为动态背景、全屏歌词与歌词视觉体验提供参考。
- **Lyrico** — 为外部标签编辑器适配、歌曲标签读取和日志页面交互提供参考。
- **LX Music Mobile** — 提供 LX Music API 兼容实现与测试参考。
- **光锥音乐** — 界面设计与功能实现参考。
- 感谢 Halcyon 所使用的 Miuix、Media3、FFmpeg、Lyricon、SuperLyricApi、LyricGetter-API、lyrico-audiotag / Lyrico、TagLib、163KeyDecrypter、Kyant Backdrop、Coil、OkHttp、Reorderable、accompanist-lyrics-core、accompanist-lyrics-ui、Beautiful Lyrics 以及其它开源项目。
- 感谢sonic topography

---
