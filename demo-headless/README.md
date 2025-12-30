# Sendspin Headless Demo

Headless background player with no UI - perfect for embedded devices, kiosks, or background audio systems.

## Features

- **Auto-starts on app install** - Service starts immediately
- **Auto-starts on boot** - Survives device reboots
- **No UI required** - Pure background operation
- **Auto-discovery** - Finds and connects to Sendspin servers automatically
- **Foreground service** - Won't be killed by Android

## Installation

```bash
./gradlew :demo-headless:installDebug
```

## How It Works

1. **On Install**: HeadlessApplication starts HeadlessService immediately
2. **On Boot**: BootReceiver starts HeadlessService automatically
3. **Service**: Runs as foreground service with persistent notification
4. **Auto-Connect**: Discovers and connects to Sendspin servers via mDNS
5. **Playback**: Maintains synchronized audio playback in background

## Checking Status

```bash
# Check if service is running
adb shell dumpsys activity services | grep HeadlessService

# View logs
adb logcat -s HeadlessService HeadlessApplication AutoConnectManager SendspinAndroidClient

# Stop service
adb shell am stopservice com.mph070770.sendspinandroid.headless/.HeadlessService

# Start service manually
adb shell am startservice com.mph070770.sendspinandroid.headless/.HeadlessService
```

## Use Cases

- **Embedded devices** - Raspberry Pi, Android TV boxes
- **Kiosks** - Public spaces, retail stores
- **Home automation** - Background music systems
- **Multi-room audio** - Distributed audio without screens
- **IoT devices** - Smart speakers, audio endpoints

## Notes

- Service shows persistent notification (required for foreground service)
- Service automatically reconnects if connection is lost
- Service restarts if app is killed (START_STICKY)
- Requires RECEIVE_BOOT_COMPLETED permission for auto-start on boot
