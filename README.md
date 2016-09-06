
# cert4android

cert4android is an Android library for managing custom certificates which has
been developed for [DAVdroid](https://davdroid.bitfire.at). Feel free to use
it in your own open-source app.

Discussion: https://forums.bitfire.at/category/7/transport-level-security


# Features

* uses a service to manage custom certificates
* supports multiple threads and multiple processes (for instance, if you have an UI
  and a separate `:sync` process which should share the certificate information)


# License 

Copyright (C) bitfire web engineering (Ricki Hirner, Bernhard Stockmann).

This program comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome
to redistribute it under the conditions of the [GNU GPL v3](LICENSE).

