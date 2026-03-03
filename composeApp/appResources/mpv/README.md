# MPV Native Binaries Placement

Place desktop native MPV binaries under this directory so `MpvNativeLoader` can resolve and load them at runtime.

Expected layout:

- `appResources/mpv/macos-*/libmpv.*`
- `appResources/mpv/linux-*/libmpv.so*`
- `appResources/mpv/windows-*/mpv-2.dll`

Current architecture tokens used by loader:

- `macos-arm64`, `macos-x64`
- `linux-arm64`, `linux-x64`
- `windows-arm64`, `windows-x64`
