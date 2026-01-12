
[![License](https://img.shields.io/github/license/bitfireAT/cert4android)](https://github.com/bitfireAT/cert4android/blob/main/LICENSE)
[![Translations status](https://hosted.weblate.org/widget/davx5/cert4android-lib/svg-badge.svg)](https://hosted.weblate.org/engage/davx5/)
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

Please help to [translate DAVx⁵ / cert4android](https://hosted.weblate.org/engage/davx5/):

[![Translation status (big)](https://hosted.weblate.org/widget/davx5/cert4android-lib/287x66-grey.png)](https://hosted.weblate.org/engage/davx5/)

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

Example of initializing an okhttp client:

```kotlin
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
```

You can overwrite resources when you want, just have a look at the `res/strings`
directory. Especially `certificate_notification_connection_security` and
`trust_certificate_unknown_certificate_found` should contain your app name.

To view the available gradle tasks for the library: `./gradlew cert4android:tasks`
(the `cert4android` module is defined in `settings.gradle`).


# License 

Copyright (C) Ricki Hirner and [contributors](https://github.com/bitfireAT/cert4android/graphs/contributors).

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
