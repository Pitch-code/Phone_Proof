# R8 runs on release builds with resource shrinking enabled.
#
# Nothing app-specific needs keeping yet: there is no reflection, no serialisation library, and
# no JNI in the project. Compose and AndroidX ship their own consumer rules.
#
# When Room and the billing client arrive, their consumer rules cover them too — resist adding
# broad -keep rules, since each one silently undoes part of the shrinking.

# Keep line numbers so a release stack trace is readable, but hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
