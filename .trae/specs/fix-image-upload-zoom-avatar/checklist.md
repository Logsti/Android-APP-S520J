# Checklist: 修复图片上传/放大/头像同步

- [ ] PhotoCompressor 只开一次输入流
- [ ] MediaRepository 上传有重试（2次，仅网络异常）
- [ ] MediaRepository.uploadAll 并行上传
- [ ] ImageViewerDialog 全屏查看 + 双指缩放 + 点击关闭
- [ ] ModuleEditScreen 缩略图可点击放大
- [ ] 登录/注册响应回填 avatar 到 prefs
- [ ] 启动时 fetchMe 同步自头像+昵称
- [ ] 全量单测通过
- [ ] APK 构建成功
