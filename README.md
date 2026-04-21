# BigCharts

[![JitPack](https://www.jitpack.io/v/vinnynm/bigcharts.svg)](https://www.jitpack.io/#vinnynm/bigcharts)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)

A modern, high-performance Android charting library built with **Jetpack Compose** and **Material Design 3**. BigCharts makes it easy to create beautiful, responsive, and animated charts in your Android applications with minimal boilerplate code.

## 📋 Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage Examples](#usage-examples)
- [Contributing](#contributing)
- [License](#license)

## ✨ Features

- 📊 **Beautiful Chart Components** - Pre-built chart types optimized for Jetpack Compose
- 🎨 **Material Design 3 Theme Support** - Seamless integration with Material Design 3
- ⚡ **High Performance** - Optimized rendering with smooth animations
- 📱 **Responsive Design** - Automatically adapts to all screen sizes and orientations
- 🔧 **Easy-to-Use API** - Simple, intuitive API for developers of all levels
- 📦 **Extended Material Icons** - Access to comprehensive icon library
- 🎯 **Type-Safe** - Built with Kotlin for type safety and null safety
- 🌙 **Dark Mode Support** - Full support for light and dark themes

## 📱 Requirements

| Requirement | Version |
|------------|---------|
| **Minimum SDK** | API 24 (Android 7.0) |
| **Target SDK** | API 36 (Android 15) |
| **Kotlin** | 1.9 or higher |
| **Java** | 11+ |
| **Gradle** | 8.0+ |

## 📦 Installation

### Step 1: Add JitPack Repository

In your project's root `build.gradle.kts` or in the module-level `build.gradle.kts`, add the JitPack repository:

```kotlin
repositories {
    google()
    mavenCentral()
    maven("https://www.jitpack.io")
}
```

**Note**: If you're using `dependencyResolutionManagement`, add it to your `settings.gradle.kts` instead:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://www.jitpack.io")
    }
}
```

### Step 2: Add the Dependency

In your app's `build.gradle.kts` file, add BigCharts to your dependencies:

```kotlin
dependencies {
    // BigCharts library
    implementation("com.github.vinnynm:bigcharts:panther")
    
    // Other dependencies...
}
```

**Find the latest version**: Check [JitPack](https://www.jitpack.io/#vinnynm/bigcharts) for the most recent release.

### Step 3: Sync Gradle

- Click **"Sync Now"** in Android Studio, or
- Run: `./gradlew clean build`

## 🚀 Quick Start

### Basic Setup

1. Ensure your Activity/Fragment uses Jetpack Compose
2. Import the BigCharts composables
3. Add chart components to your Compose UI

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import com.enigma.bigcharts.ui.theme.BigChartsTheme

@Composable
fun MyChartScreen() {
    BigChartsTheme {
        // Your chart components go here
    }
}
```

## 💡 Usage Examples

### Example 1: Simple Chart in Compose

```kotlin
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SimpleChartExample() {
    // Chart implementation here
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        // Add your chart component
    }
}
```

### Example 2: Using BigCharts in an Activity

```kotlin
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.enigma.bigcharts.ui.theme.BigChartsTheme

class ChartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BigChartsTheme {
                ChartScreen()
            }
        }
    }
}
```

## 🏗️ Project Structure

```
BigCharts/
├── app/                    # Demo application
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/     # Kotlin source files
│   │   │   └── res/        # Resources
│   │   └── test/           # Unit tests
│   └── build.gradle.kts    # App module configuration
├── gradle/                 # Gradle wrapper files
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Gradle settings
└── README.md              # This file
```

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Design System**: Material Design 3
- **Minimum API Level**: 24
- **Build System**: Gradle with Kotlin DSL

### Key Dependencies

```kotlin
// Jetpack Compose
androidx.compose.ui:compose-ui
androidx.compose.material3:material3

// Material Icons
androidx.compose.material:material-icons-extended-android

// Android Core
androidx.core:core-ktx
androidx.activity:activity-compose
androidx.lifecycle:lifecycle-runtime-ktx
```

## 🤝 Contributing

We welcome contributions! Follow these steps:

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/your-username/bigcharts.git`
3. **Create** a feature branch: `git checkout -b feature/amazing-feature`
4. **Commit** your changes: `git commit -m 'Add amazing feature'`
5. **Push** to your branch: `git push origin feature/amazing-feature`
6. **Open** a Pull Request with a clear description

### Development Setup

```bash
# Clone the repository
git clone https://github.com/vinnynm/bigcharts.git
cd bigcharts

# Open in Android Studio and build
./gradlew build

# Run tests
./gradlew test

# Run the demo app
./gradlew installDebug
```

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 💬 Support

- **Issues**: [GitHub Issues](https://github.com/vinnynm/bigcharts/issues)
- **Discussions**: [GitHub Discussions](https://github.com/vinnynm/bigcharts/discussions)

## ⚠️ Project Status

This library is in **active development**. While the API is relatively stable, breaking changes may occur in minor releases before v1.0 is finalized.

## 🙏 Acknowledgments

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose)
- Designed with [Material Design 3](https://m3.material.io/)
- Distributed via [JitPack](https://www.jitpack.io)

---

**Made with ❤️ by the BigCharts team**

*Last updated: April 2026*
