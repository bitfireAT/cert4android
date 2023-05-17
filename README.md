
[![License](https://img.shields.io/github/license/bitfireAT/cert4android)](https://github.com/bitfireAT/cert4android/blob/main/LICENSE)
[![Tests](https://github.com/bitfireAT/cert4android/actions/workflows/test-dev.yml/badge.svg)](https://github.com/bitfireAT/cert4android/actions/workflows/test-dev.yml)
[![KDoc](https://img.shields.io/badge/documentation-KDoc-informational)](https://bitfireat.github.io/cert4android/)


# cert4android

cert4android is a library for Android to manage custom certificates which has
been developed for [DAVx⁵](https://www.davx5.com). Feel free to use
it in your own open-source app.

_This software is not affiliated to, nor has it been authorized, sponsored or otherwise approved
by Google LLC. Android is a trademark of Google LLC._

Generated KDoc: https://bitfireat.github.io/cert4android/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/cert4android/discussions).
We're happy about contributions! In case of bigger changes, please let us know in the discussions before.
Then make the changes in your own repository and send a pull request.


# Features

* uses a service to manage custom certificates
* supports multiple threads and multiple processes (for instance, if you have an UI
  and a separate `:sync` process which should share the certificate information)


# How to use

1. Clone cert4android as a submodule.
1. Add the submodule to `settings.gradle` / `app/build.gradle`.
1. Create an instance of `CustomCertManager` (`Context` is required to connect to the
   `CustomCertService`, which manages the custom certificates).
1. Use this instance as `X509TrustManager` in your calls (for instance, when setting up your HTTP client).
   Don't forget to get and use the `hostnameVerifier()`, too.
1. Close the instance when it's not required anymore (will disconnect from the
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


# License 

Copyright (C) Ricki Hirner and [contributors](https://github.com/bitfireAT/cert4android/graphs/contributors).

This program comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome
to redistribute it under the conditions of the [GNU GPL v3](LICENSE).

