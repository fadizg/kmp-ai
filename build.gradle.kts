// Top-level build file. Plugins are declared with `apply false` so each
// subproject can apply only what it needs. Android and iOS plugins are
// gated at the subproject level via env vars (KMP_AI_ANDROID, KMP_AI_IOS).
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}
