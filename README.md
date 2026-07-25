# PC VPN Bridge

[English](README.md) | [Русский](README.ru.md)

An Android application for tunneling smartphone traffic through a local computer proxy (SOCKS5 / HTTP CONNECT).

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![Material3](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)
![License](https://img.shields.io/badge/License-MIT-orange.svg)

---

## 📖 Quick Setup & User Guide

### Step 1. Enable LAN Sharing on your PC

To allow your smartphone to connect to your PC's proxy over Wi-Fi, you must enable **Allow LAN** in your PC's VPN client.

#### 🔹 Example: Karing (Recommended)
1. Open **Karing** on your computer.
2. Go to **Settings** ⚙️.
3. Enable **"Allow LAN"** (*Allow connections from local network*).
4. **Default Port**: Karing uses port **`4066`** by default (which matches the default port in PC VPN Bridge).

#### 🔹 Other PC Proxy Clients (v2rayN, Clash, Hiddify, NekoBox)
- Open client settings and turn on **Allow LAN** / **Share over LAN**.
- Note the SOCKS5 or HTTP proxy port (e.g. `10808`, `7890`, `1080`).

---

### Step 2. Enable `.local` Hostnames on Windows (Highly Recommended)

If you want to connect using your computer's name (e.g., `matebook.local` or `pc.local`) instead of typing IP addresses:

1. **Works on ANY Windows PC**: Although named "Apple Bonjour", this official service installs on **ALL Windows computers** (ASUS, Lenovo, Dell, HP, custom desktops, etc.).
2. **Why this is better than IP addresses**: Local IP addresses (`192.168.x.x`) are dynamic and change whenever your router reboots or when you switch Wi-Fi networks. By using `.local` (mDNS), your smartphone will **always connect automatically on ANY Wi-Fi network**, even if your PC's IP address changes!
3. **Download**: Install **[Bonjour Print Services for Windows (Official Apple Download)](https://support.apple.com/kb/DL999)** (*BonjourPrinterSetup.exe*).
4. Once installed, your PC will automatically respond to `.local` queries from your smartphone in ~10ms.

---

### Step 3. Connect Android App

1. Install **PC VPN Bridge** on your Android smartphone.
2. Enter your PC's **Computer Name** (e.g., `matebook.local`) or **IP Address** (e.g., `192.168.1.50`).
3. Set the **Port** (default is **`4066`** for Karing).
4. Tap **Connect**!

---

## ✨ Key Features

- ⚡ **Protocol Auto-Detection**: Automatically detects **SOCKS5**, **HTTP CONNECT**, or **Direct TCP** on your PC proxy port.
- 🌐 **Native `.local` Hostname Resolution (mDNS RFC 6762)**: Sends multicast UDP queries over Wi-Fi to resolve PC IP addresses in 10ms.
- 📺 **YouTube 4K & Media Streaming**:
  - **MSS 1460 Payload Segmentation**: Large video streams are segmented into MTU-compliant 1500-byte IP packets.
  - **Protected Dual-Engine DNS**: Resolves real IPv4 addresses via Google Public DNS `8.8.8.8:53` bypassing ISP DNS poisoning.
- 🛡️ **Crash-Proof Synchronization**:
  - Atomic thread safety via `Mutex`.
  - Instant socket port reuse (`reuseAddress = true`).
  - Guaranteed in-order TCP payload delivery.
- 📊 **Real-Time Diagnostic Log Console**: Live streaming of connection states, DNS queries, and tunnel logs directly in the app.
- 🎨 **Jetpack Compose Material 3 UI**: Multilingual support (English & Russian, English by default), Dark/Light theme switching, adaptive launcher icon.

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
