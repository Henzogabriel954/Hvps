# DanBot VPS Manager (Unofficial & Open Source)

Welcome! This is an open-source Android application designed to make life easier for [DanBot Hosting](https://danbot.host) users.

**Goal:** The main purpose of this app is to provide a quick and convenient way to manage your Virtual Private Servers (VPS) directly from your phone, without the hassle of constantly logging into the web panel via a browser.

## Why use this app?

-   **Convenience:** Start, stop, and restart your VPS with a single tap.
-   **Peace of Mind:** Get notified if your server goes offline or experiences high load, even when the app is closed.
-   **Simplicity:** No need to navigate complex web interfaces on a small mobile screen.
-   **Open Source:** The code is completely open for anyone to inspect, modify, and improve. Transparency and security are key.

## Features

### 🎨 Modern UI
-   **Material You Design:** A beautiful, modern interface that adapts to your phone's wallpaper colors (Android 12+).
-   **Intuitive Navigation:** Simple drawer-based navigation to switch between multiple servers.

### ⚡ Real-Time Monitoring & Control
-   **Live Stats:** Watch your VPS CPU usage and network traffic (RX/TX) update in real-time.
-   **Power Control:** Full control at your fingertips: Boot, Restart, Shutdown, and Kill.
-   **Resource Overview:** View your allocated RAM, CPU Cores, and Disk Space.

### 🚀 Proprietary Agent (New!)
Get deeper insights into your VPS with our optional lightweight monitoring agent.
-   **Real Usage Stats:** View actual **Memory** and **Disk** usage percentages, not just the allocated amount.
-   **Uptime Tracking:** See exactly how long your server has been running (e.g., "5d 12h 30m").
-   **Faster Updates:** Enjoy faster refresh rates (every 15s) when connected via the agent.
-   **Easy Setup:** Click "How to connect?" in the app settings to view the [installation guide](https://github.com/Henzogabriel954/Hvps-api-proprietaria).

### 🔔 Smart Notifications (Background Service)
-   **High CPU Alerts:** Set a custom threshold (e.g., 85%) and get notified if your server's CPU spikes.
-   **Status Monitoring:** Receive immediate alerts if your server goes **Offline** or comes back **Online**.
-   **Background Sync:** The app monitors your selected servers in the background, ensuring you never miss a critical event.

### 🔒 Privacy & Connectivity
-   **IP Privacy:** IPv4 and IPv6 addresses are blurred by default. Tap the "eye" icon to reveal them.
-   **One-Tap Copy:** Quickly copy IP addresses to your clipboard.
-   **Event History:** A local log of all alerts and status changes is kept on your device, so you can track what happened while you were away.

## How it works

This app communicates directly with the DanBot Hosting API using your personal API Key.
-   **Security:** Your API Key is stored securely on your device using Android's EncryptedSharedPreferences.
-   **Direct Connection:** No middleman servers. The app talks directly to `vps.danbot.host`.

## Getting Started

1.  **Get your API Key:** Log in to the [DanBot VPS Panel](https://vps.danbot.host/), go to Account Settings > API Credentials, and create a new API Key.
2.  **Install the App:** Download and install the APK on your Android device.
3.  **Connect:** Open the app, paste your API Key, and you're ready to go!

## Configuring Alerts

1.  Select a server from the menu.
2.  Tap the **Settings (Gear)** icon in the top right.
3.  Enable **High CPU Alerts** and set your desired threshold slider.
4.  Toggle **Notify when Offline** or **Online** based on your preference.
5.  *The app will now monitor this server in the background.*

## Contributing

This is a community-driven project. If you have ideas, found a bug, or want to add a feature:
-   Fork this repository.
-   Make your changes.
-   Submit a Pull Request!

---
*Disclaimer: This is an unofficial client created by the community. It is not directly affiliated with or endorsed by DanBot Hosting.*