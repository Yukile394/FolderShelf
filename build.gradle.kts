// Kok build.gradle.kts
//
// Android Gradle Plugin (AGP) 9.2+ dahili Kotlin destegi sundugu icin
// burada ayrica "org.jetbrains.kotlin.android" eklentisini uygulamiyoruz.
// Bu bilincli bir tercihtir: AGP 9.0 ve sonrasinda Kotlin destegi
// dogrudan AGP icine gomulmustur, eski kotlin-android eklentisini
// eklemek artik derleme hatasina yol acar.
plugins {
    id("com.android.application") version "9.2.0" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
