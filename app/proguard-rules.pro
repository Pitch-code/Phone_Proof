# R8 runs on release builds with resource shrinking enabled.
#
# Compose and AndroidX ship their own consumer rules, so most of the app needs nothing here. Resist
# adding broad -keep rules: each one silently undoes part of the shrinking.
#
# There is one exception below, and it is deliberate.

# Keep line numbers so a release stack trace is readable, but hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------------------------------
# kotlinx.serialization
#
# This file used to state, as its justification for keeping nothing, that the project had "no
# reflection, no serialisation library, and no JNI". That was true when it was written and stopped
# being true the moment saved reports arrived: core:reports serialises every CheckResult through
# kotlinx.serialization.json.Json, and nobody revisited this file.
#
# The library does ship consumer rules, so this may well be redundant. It is here anyway, and that is
# a considered exception to the advice above, because of how the failure would present:
#
#   - R8 does not error when it strips a serializer. The release build succeeds.
#   - Unit tests never see R8 at all — it runs when the APK is packaged, not when tests run — so no
#     test in this repo can catch it.
#   - The symptom would be saved reports failing to load, in release only, for real users, after
#     review had already passed.
#
# An unnecessary keep costs a few kilobytes. Getting this wrong costs every user their report history
# with no way to find out beforehand from this machine.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
