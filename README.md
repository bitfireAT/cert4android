
[![License](https://img.shields.io/github/license/bitfireAT/cert4android)](https://github.com/bitfireAT/cert4android/blob/main/LICENSE)
[![Translation status](https://hosted.weblate.org/widget/cert4android/status-badge.png)](https://hosted.weblate.org/engage/cert4android/)
[![KDoc](https://img.shields.io/badge/documentation-KDoc-informational)](https://bitfireat.github.io/cert4android/)

_This software is not affiliated to, nor has it been authorized, sponsored or otherwise approved
by Google LLC. Android is a trademark of Google LLC._


# cert4android

cert4android is a library for Android to manage custom certificates which has
been developed for [DAVx⁵](https://www.davx5.com). Feel free to use
it in your own open-source app.

Generated KDoc: https://bitfireat.github.io/cert4android/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/cert4android/discussions).
We're happy about contributions! In case of bigger changes, please let us know in the discussions before.
Then make the changes in your own repository and send a pull request.


# Features

* uses a service to manage custom certificates
* supports multiple threads and multiple processes (for instance, if you have an UI
  and a separate `:sync` process which should share the certificate information)


# Contributions

Please help to [translate cert4android](https://hosted.weblate.org/engage/cert4android/):

[![Translation status (big)](https://hosted.weblate.org/widget/cert4android/287x66-grey.png)](https://hosted.weblate.org/engage/cert4android/)

For other topics, you can [create an issue](https://github.com/bitfireAT/cert4android/issues)
or [submit a PR](https://github.com/bitfireAT/cert4android/pulls) over Github.


# How to use

1. Add the [jitpack.io](https://jitpack.io) repository to your project's level `build.gradle`:
    ```groovy
    allprojects {
        repositories {
            // ... more repos
            maven { url "https://jitpack.io" }
        }
    }
    ```
   or if you are using `settings.gradle`:
    ```groovy
    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            // ... more repos
            maven { url "https://jitpack.io" }
        }
    }
    ```
2. Add the dependency to your module's `build.gradle` file:
    ```groovy
    dependencies {
       implementation 'com.github.bitfireAT:cert4android:<version>'
    }
    ```
3. Create an instance of `CustomCertManager` (`Context` is required to connect to the
   `CustomCertService`, which manages the custom certificates).
4. Use this instance as `X509TrustManager` in your calls (for instance, when setting up your HTTP client).
   Don't forget to get and use the `hostnameVerifier()`, too.
5. Close the instance when it's not required anymore (will disconnect from the
   `CustomCertService`, thus allowing it to be destroyed).

Example of initialzing an okhttp client:

    val keyManager = ...
    CustomCertManager(...).use { trustManager ->
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            if (keyManager != null) arrayOf(keyManager) else null,
            arrayOf(trustManager),
            null
        )
        val builder = OkHttpClient.Builder()
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
               .hostnameVerifier(hostnameVerifier)
        val httpClient = builder.build()
        // use httpClient
    }


You can overwrite resources when you want, just have a look at the `res/strings`
directory. Especially `certificate_notification_connection_security` and
`trust_certificate_unknown_certificate_found` should contain your app name.

To view the available gradle tasks for the library: `./gradlew cert4android:tasks`
(the `cert4android` module is defined in `settings.gradle`).


# License 

Copyright (C) Ricki Hirner and [contributors](https://github.com/bitfireAT/cert4android/graphs/contributors).

This program comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome
to redistribute it under the conditions of the [GNU GPL v3](LICENSE).

