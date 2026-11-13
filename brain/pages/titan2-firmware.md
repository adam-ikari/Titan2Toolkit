---
id: titan2-firmware
title: "Titan 2 国际版(TEE)固件来源与版本"
category: reference
status: active
tags: [titan2, firmware, download]
created: "2026-11-12T23:57:07"
updated: "2026-11-12T23:58:28"
---

<!-- compiled_truth -->
## 区域固件

- **TEE** = 国际版/全球版（目标机固件）；**EEA** = 欧洲版。
- 固件为 Unihertz 官方 Google Drive 全量包，社区镜像备份在 `https://cloud.lichtmetzger.de/d/ccc9017f47a6459d8b37/`（Seafile，支持 HTTP Range，但限速约 63 KB/s）。

## 官方 Google Drive 目录结构

根目录 `Software_firmware`（folder id `0By1nhWOmuw2KdDhTUlFOZHpXQjg`）→ `Titan 2`（`1MnVTW1EI-G86PRBUIDD0QAXuE3lqtnTP`）→ 按 Android 版本：

- Android 15：`122rcnaWHDoRbJMmgMxdaRfMElZonQiOg`
  - `EuropeanUnion_EEA`：`1keMzVNiVaL15iaN1ZoLBOmWkDAmmeeph`
  - `None_EEA`（= TEE 国际版）：`1LYmZyadDAAZISZgllurBjqSs-fNVnEf9`
- Android 16：`1iEOXUlO6Hlf6yWL-D4NtUCV6sA_I-Ku3`
  - `EuropeanUnion_EEA`：`1EIiX5mdpkYEeduUXlZKmmDIt6H7qG8cA`
  - `None_EEA`（= TEE 国际版）：`11UdnVfohqPWUgC6I-Z_unjMYGQfJkera`

## 已知 TEE 全量包（Google Drive file id）

| 版本 | 文件 | file id |
| --- | --- | --- |
| 2026021022 | `2026021022_g71v78c2k_dfl_tee.zip` | `1V-d6mZMGPEjWBP9gzF0ROxtQ6HBACaOP` |
| 2026042212（最新） | `2026042212_g71v78c2k_dfl_tee.zip` | `1ldEPF4fj6JbVzSY0zhtFiTz-rf5iwN2w` |

## 包内容要点

- 全量包约 3.7 GB，内含 `images/super.img`（Android 动态分区：system / system_ext / product / vendor）。
- `images/boot.img`、`images/vendor_boot.img` 对应内核 6.1.145（Android 16 未换内核）。
- 提取单个系统 APK 需先解出 `super.img`，再用 lpunpack 拆分区、挂载 ext4；zip 成员是 deflate 压缩，无法只 Range 取 `super.img` 的局部。

## 本地存放

- 下载目录：仓库内 `firmware/`（持久，勿放 /tmp）。
- 目标文件：`firmware/2026042212_g71v78c2k_dfl_tee.zip`。
- 下载工具：`gdown <file-id> -O <path> --continue`（新版 gdown 的 id 是位置参数，不是 `--id`）。


## Timeline

- time: 2026-11-12T23:57:07
  kind: decision
  summary: "Created this page: Titan 2 国际版(TEE)固件来源与版本"
  source: web search 2026-11-12
  affects: [titan2-firmware]

- time: 2026-11-12T23:58:28
  kind: decision
  summary: "记录 Titan 2 国际版(TEE)固件来源、版本与本地存放路径"
  source: web search 2026-11-12
  affects: [titan2-firmware]
