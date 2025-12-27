import com.android.build.api.variant.impl.VariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.refocus"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.example.refocus"
        minSdk = 26
        targetSdk = 36
        versionCode = 8       // APK配布時に毎回インクリメント
        versionName = "0.5.1" // (大きな区切り・互換性のない変更).(後方互換ありの機能追加).(バグ修正など)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    sourceSets {
        // Room の schema JSON を migration テストで参照するために androidTest の assets に含める
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

kapt {
    arguments {
        // Room がスキーマJSONを吐き出すディレクトリ
        arg("room.schemaLocation", "$projectDir/schemas")
        // お好みで（ビルド高速化系）
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.android)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.javapoet)
    kapt(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room の migration schema 読み込みは kotlinx.serialization を使う．
    // kotlinx.serialization の runtime が「Room 側の serializer（Room がビルド時に想定した ABI）」とズレると，
    // `AbstractMethodError: ... GeneratedSerializer.typeParametersSerializers()` のような実行時エラーで落ちることがある．
    //
    // 本プロジェクトでは，Room（room-migration）が 1.8.x 系で生成された serializer を含むため，
    // runtime 側も 1.8.x に揃えておかないとテスト実行時に
    // `AbstractMethodError: ... GeneratedSerializer.typeParametersSerializers()` で落ちることがある．
    // そこで，app（debugRuntimeClasspath）と androidTest の両方で 1.8.1 に揃えて固定する．
    constraints {
        val serializationVersion = "1.8.1"

        listOf("implementation", "androidTestImplementation").forEach { conf ->
            add(conf, "org.jetbrains.kotlinx:kotlinx-serialization-core") {
                version { strictly(serializationVersion) }
            }
            add(conf, "org.jetbrains.kotlinx:kotlinx-serialization-json") {
                version { strictly(serializationVersion) }
            }
            add(conf, "org.jetbrains.kotlinx:kotlinx-serialization-json-okio") {
                version { strictly(serializationVersion) }
            }
        }
    }
}

/**
 * Domain レイヤの境界を守るための，軽量なガード．
 *
 * ここでは「import 文」に限定して禁止依存を検知する．
 * Kotlin/Java の import 以外（FQCN 直書きなど）は検知できないが，
 * レイヤ境界の逸脱を早期に止める用途としては十分有効．
 */
val domainSourceRoots = listOf(
    file("src/main/java/com/example/refocus/domain"),
    file("src/main/kotlin/com/example/refocus/domain"),
)

val featureSourceRoots = listOf(
    file("src/main/java/com/example/refocus/feature"),
    file("src/main/kotlin/com/example/refocus/feature"),
)
val systemSourceRoots = listOf(
    file("src/main/java/com/example/refocus/system"),
    file("src/main/kotlin/com/example/refocus/system"),
)


tasks.register("checkDomainBoundaries") {
    group = "verification"
    description = "Fails if domain layer depends on Android/UI/data layers via imports."

    doLast {
        // Domain は純 Kotlin を前提にし，Android/UI/データ層へ直接依存しない．
        val forbiddenImportPrefixes = listOf(
            "android.",
            "androidx.",
            "com.google.android.",
            // app/system は Android 実装に寄るため，domain からは直接参照しない
            "com.example.refocus.app.",
            "com.example.refocus.system.",
            "com.example.refocus.data.",
            "com.example.refocus.feature.",
            "com.example.refocus.ui.",
            // プリセットやデフォルト値などの「アプリ設定」は domain へ流れ込ませない
            "com.example.refocus.config.",
        )

        val violations = mutableListOf<String>()

        domainSourceRoots
            .filter { it.exists() }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach { file ->
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                if (!trimmed.startsWith("import ")) return@forEachIndexed

                                // Kotlin: import a.b.C / Java: import a.b.C;
                                val imported = trimmed
                                    .removePrefix("import ")
                                    .trim()
                                    .removeSuffix(";")

                                if (forbiddenImportPrefixes.any { imported.startsWith(it) }) {
                                    violations += "${file.relativeTo(projectDir)}:${index + 1}: $trimmed"
                                }
                            }
                        }
                    }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Domain boundary violations found (forbidden imports in domain):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

/**
 * Feature（UI）レイヤは，system 実装へ直接依存しない．
 *
 * - platform 依存は gateway / domain 経由で注入する
 * - 「import 文」に限定した軽量ガード
 */
tasks.register("checkFeatureBoundaries") {
    group = "verification"
    description = "Fails if feature layer depends on system/data layers via imports."

    doLast {
        val forbiddenImportPrefixes = listOf(
            "com.example.refocus.system.",
            "com.example.refocus.data.",
            // feature は app 層へ依存しない（navigation/DI などは app 側が feature を参照する）
            "com.example.refocus.app.",
        )

        val violations = mutableListOf<String>()

        featureSourceRoots
            .filter { it.exists() }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach { file ->
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                if (!trimmed.startsWith("import ")) return@forEachIndexed

                                val imported = trimmed
                                    .removePrefix("import ")
                                    .trim()
                                    .removeSuffix(";")

                                if (forbiddenImportPrefixes.any { imported.startsWith(it) }) {
                                    violations += "${file.relativeTo(projectDir)}:${index + 1}: $trimmed"
                                }
                            }
                        }
                    }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Feature boundary violations found (forbidden imports in feature):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}



/**
 * System（Android 実装）レイヤは，app / feature / config / data へ依存しない．
 *
 * - system は OS 依存やサービス，レシーバ，通知，オーバーレイ制御などを担う
 * - 画面やナビゲーションの統合（app），画面実装（feature），プリセット（config），repository 実装（data）は参照しない
 */
tasks.register("checkSystemBoundaries") {
    group = "verification"
    description = "Fails if system layer depends on app/feature/config/data layers via imports."

    doLast {
        val forbiddenImportPrefixes = listOf(
            "com.example.refocus.app.",
            "com.example.refocus.feature.",
            // プリセットやデフォルト値などの「アプリ設定」は app 側に閉じる
            "com.example.refocus.config.",
            // system は repository 実装に直接依存せず，domain 経由で注入する
            "com.example.refocus.data.",
        )

        val violations = mutableListOf<String>()

        systemSourceRoots
            .filter { it.exists() }
            .forEach { root ->
                root.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .forEach { file ->
                        file.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                val trimmed = line.trim()
                                if (!trimmed.startsWith("import ")) return@forEachIndexed

                                val imported = trimmed
                                    .removePrefix("import ")
                                    .trim()
                                    .removeSuffix(";")

                                if (forbiddenImportPrefixes.any { imported.startsWith(it) }) {
                                    violations += "${file.relativeTo(projectDir)}:${index + 1}: $trimmed"
                                }
                            }
                        }
                    }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("System boundary violations found (forbidden imports in system):")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}
// 既存の CI / 手元チェックの流れに自然に乗せる
tasks.named("check").configure {
    dependsOn("checkDomainBoundaries")
    dependsOn("checkFeatureBoundaries")
    dependsOn("checkSystemBoundaries")
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val appName = "refocus"
        val date = SimpleDateFormat("yyyyMMdd").format(Date())
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                val vName = output.versionName.get()
                output.outputFileName =
                    "${appName}-android-v${vName}-debug-${date}.apk"
            }
        }
    }
}
