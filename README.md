
# shree_tvhome

Dynamic Android TV home launcher designed with centralized settings/themes, automatic updates across linked components, D-Pad focus routing, low APK size, and low RAM overhead.

## Repository Tree & File Guide

```text
shree_tvhome/
├── .github/
│   └── workflows/
│       └── build-apk.yml                              # CI pipeline to build, verify (aapt2), and rotate debug APKs
├── app/
│   ├── build.gradle                                   # App module SDK config, R8 shrinking, and lean packaging rules
│   ├── proguard-rules.pro                             # R8 keep rules and log/assertion stripping for smaller APK & RAM use
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml                    # TV launcher activity, permissions, receiver, and notification service
│           ├── res/
│           │   ├── drawable/
│           │   │   ├── ic_launcher_background.xml     # Vector background layer for launcher icon
│           │   │   ├── ic_launcher_foreground.xml     # Vector foreground crest/monogram for launcher icon
│           │   │   └── tv_banner.xml                  # Android TV leanback banner composed from shared vector layers
│           │   ├── mipmap-anydpi-v26/
│           │   │   ├── ic_launcher.xml                # Adaptive square launcher icon definition
│           │   │   └── ic_launcher_round.xml          # Adaptive round launcher icon definition
│           │   └── values/
│           │       └── strings.xml                    # App display name and localized text resources
│           └── java/
│               └── shree/
│                   └── tvhome/                      # Main package: universal entry, navigation, home UI, and panel hub
│                       ├── MainActivity.kt          # Main activity lifecycle, receivers, intents, and screen coordination
│                       ├── FocusRouter.kt           # Natural D-Pad directional focus routing across bars, panels, and rows
│                       ├── HomeScreen.kt            # Homepage layout container for header, favorites, grid, and stage area
│                       ├── PanelHub.kt              # Universal panel controller (CPU) driving all panels, styles, and motion
│                       ├── TopHeaderBar.kt          # Top bar with profile greeting, voice, RAM, Wi-Fi, and panel triggers
│                       │
│                       ├── core/                      # Core state, themes, motion, backdrop, and system services
│                       │   ├── AppSettings.kt         # Single source of truth for persisted preferences and style listeners
│                       │   ├── Themes.kt              # Color palettes, accents, tile sizes/shapes, and adaptive contrast colors
│                       │   ├── AnimationController.kt # Centralized motion styles, interpolators, focus and panel animations
│                       │   ├── LiveBackdrop.kt        # Smart/time-based live canvas scenes and sampled gallery wallpaper engine
│                       │   ├── LayoutMetrics.kt       # Shared dp scaling, layout helpers, app query helper, and color blending
│                       │   ├── DeviceSpecs.kt         # Dynamic detection of TV inputs, audio outputs, Bluetooth, and settings
│                       │   ├── AlertsListener.kt      # Real-time notification listener feeding live alerts to the alerts panel
│                       │   ├── SleepReminderReceiver.kt # Scheduled bedtime/morning reminder receiver respecting Do Not Disturb
│                       │   └── MoodPresets.kt         # One-tap mood presets linking theme, accent, motion, and backdrop cleanly
│                       │
│                       ├── design/                    # Visual UI components for app shelves, tiles, clock, and dialogs
│                       │   ├── AppsShelfDesign.kt     # Populates hidden ribbon, favorites row, and main app catalog dynamically
│                       │   ├── SpatialGridDesign.kt   # Grid layout and D-Pad movement engine with smooth tile transitions
│                       │   ├── AppTileDesign.kt       # Builds app tiles with LRU icon cache, nameplates, focus, and actions
│                       │   ├── FocusAppGlowDesign.kt  # Renders tile shape clipping, glass sheen, and cached focus glow shaders
│                       │   ├── GreetingCardDesign.kt  # Time-aware greeting chip with avatar cutout and shimmer effect
│                       │   ├── ClockWeatherCardDesign.kt# Analog/digital clock and live city weather stage widget
│                       │   ├── AppMenuDialogDesign.kt # Context dialog for favorite ordering, hide/unhide, info, and uninstall
│                       │   └── VectorGlyphDesign.kt   # Lightweight custom Canvas vector icons to avoid extra bitmap assets
│                       │
│                       └── panels/                    # Feature panels orchestrated by PanelHub
│                           ├── PanelSurfaceKit.kt     # Shared panel headers, glass cards, segmented bars, and pill sliders
│                           ├── StyleCustomizePanel.kt # Customization controls for moods, profile, grid, shapes, and motion
│                           ├── QuickControlsPanel.kt  # Quick hardware sliders, ambient trigger, memory info, and OS shortcuts
│                           ├── ClockWidgetPanel.kt    # Clock & weather widget options (city, format, stage behavior)
│                           ├── PictureAudioPanel.kt   # Picture modes, brightness/warmth, sleep timer, audio, inputs, BT, storage
│                           ├── WifiPanel.kt           # Wi-Fi toggle, live link/IP/ping stats, scan, and connection dialog
│                           ├── WallpaperPanel.kt      # Smart modes, live scenes, and gallery wallpaper fit/zoom/pan/aura tools
│                           ├── AlertsPowerPanel.kt    # Live notifications list, DND toggle, ambient mode, and refresh actions
│                           └── AvatarEditorPanel.kt   # Profile name editor and circular avatar zoom/pan cropper dialog
├── build.gradle                                       # Root Gradle plugin version declarations
├── gradle.properties                                  # Gradle JVM memory and AndroidX / R8 build flags
├── settings.gradle                                    # Root project name, module inclusion, and repository policy
└── README.md                                          # Project overview and file-by-file documentation guide
