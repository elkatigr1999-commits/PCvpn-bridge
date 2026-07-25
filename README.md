# PC VPN Bridge

[English](README.md) | [Русский](README.ru.md)

An Android application for tunneling smartphone traffic through a local computer proxy (SOCKS5 / HTTP CONNECT).

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![Material3](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

---

## 🚀 Overview

**PC VPN Bridge** turns any SOCKS5 or HTTP CONNECT proxy running on your computer (v2rayN, Clash, Hiddify, NekoBox, HTTP Custom, 3proxy, etc.) into a full system-wide Android VPN tunnel.

Designed for maximum speed, bypass of network throttling, and seamless streaming (Telegram, YouTube 4K, Instagram, etc.).

---

## ✨ Features

- ⚡ **Protocol Auto-Detection**: Automatically detects **SOCKS5**, **HTTP CONNECT**, or **Direct TCP** on your PC proxy port (default `4066`).
- 🌐 **Native `.local` Hostname Resolution (mDNS RFC 6762)**: Sends multicast UDP queries over Wi-Fi to resolve PC IP addresses (`matebook.local`) in 10ms without router dependencies.
- 📺 **YouTube 4K & Media Streaming**:
  - **MSS 1460 Payload Segmentation**: Large video streams are segmented into MTU-compliant 1500-byte IP packets.
  - **Protected Dual-Engine DNS**: Resolves real IPv4 addresses via Google Public DNS `8.8.8.8:53` bypassing ISP DNS poisoning.
- 🛡️ **Crash-Proof Synchronization**:
  - Atomic thread safety via `Mutex`.
  - Instant socket port reuse (`reuseAddress = true`).
  - Guaranteed in-order TCP payload delivery.
- 📊 **Real-Time Diagnostic Log Console**:
  - Live streaming of connection states, DNS queries, and tunnel logs directly in the app.
  - One-click log copying to clipboard.
- 🎨 **Jetpack Compose Material 3 UI**:
  - Multilingual support (English & Russian, English by default).
  - Modern borderless design, Dark/Light theme switching, adaptive launcher icon.

---

## 🛠️ Connection Architecture

```
[ Android Smartphone ]
        │
        ├──> VpnService (TUN Interface 10.0.0.2 / MTU 1500)
        │         │
        │         └──> Socks5Tun2Socks (Protected DNS 8.8.8.8 + MSS 1460)
        │                   │
        └─── (Wi-Fi) ───────┼────────> [ PC / Proxy 192.168.x.x:4066 ]
                            │                     │
                            └─────────────────────┴──> (Internet / PC VPN)
```

---

## 📱 Build & Installation

### Requirements
- Android 8.0 (API Level 26) or higher
- JDK 17 / Kotlin 1.9+
- Android Studio or Gradle CLI

### Building from Source

```bash
# Clone the repository
git clone https://github.com/elkatigr1999-commits/PCvpn-bridge.git
cd PCvpn-bridge

# Build Debug APK
./gradlew assembleDebug
```

The compiled APK will be located at: `PCVPN-Bridge-v1.0.apk`.

---

## 📄 License

Distributed under the **MIT License**.
