plugins {
    id("com.android.application") version "9.2.1" apply false
    // AGP builds Kotlin itself; naming the Kotlin plugin here sets the version it uses.
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("com.google.gms.google-services") version "4.6.0" apply false
}
