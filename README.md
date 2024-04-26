# Encrypted Logging [![Build status](https://badge.buildkite.com/df7ab10ed997a525c11a0f9676f9a323015bdc9d39ee69f315.svg)](https://buildkite.com/automattic/encrypted-logging)


A tool for encrypting and sending user logs to the internal Automattic's service. 

## Motivation
We use it to increase privacy of our users by not sharing their logs with any 3rd party company like crash logging services or support SDKs.

## Context and history
Originally, this feature was implemented as a part of [WordPress-FluxC-Android](https://github.com/wordpress-mobile/WordPress-FluxC-Android). To allow other clients to use Encrypted Logging as well, we decided to extract it to a separate library. It was done using `git filter-repo` tool, so the Git history is preserved (although it's relative to the original, WordPress-FluxC-Android repository).

## How to use it

1. Add a dependency
```groovy
dependency {
    implementation "com.automattic:encryptedlogging:<newest_version>"
}
```

2. Provide App Secret and Encryption Key. Because the SDK uses event bus, the `AutomatticEncryptedLogging` should be a singleton.
```kotlin
@Provides
@Singleton
fun provideEncryptedLogging(
    @ApplicationContext context: Context
): EncryptedLogging {
    return AutomatticEncryptedLogging(
        context,
        // Can be found in secrets
        encryptedLoggingKey = BuildConfig.ENCRYPTION_KEY,
        // Ping Apps Infrastructure about this one
        clientSecret = BuildConfig.APP_SECRET
    )
}
```

3. Initialize SDK in Application
```kotlin
class MyApplication : Application() {

    @Inject lateinit var encryptedLogging: EncryptedLogging

    override fun onCreate() {
        super.onCreate()

        // Reset upload state in case app was closed during the upload of previous logs
        encryptedLogging.resetUploadStates()

        // Launch upload of stored logs in app's coroutine scope
        applicationScope.launch {
            encryptedLogging.uploadEncryptedLogs()
        }
    }
}
```

4. Enqueue sending logs
```kotlin
class SupportForm() { 

    fun sendLogs(): String {
         val uuid = UUID.randomUUID().toString()
         encryptedLogging.enqueueSendingEncryptedLogs(
            uuid,
            <log file>,
            shouldUploadImmediately = true
        )
        // Attach UUID to support ticket or crashlogging issue
        return uuid
    }

}
```