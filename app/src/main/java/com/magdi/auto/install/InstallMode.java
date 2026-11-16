package com.magdi.auto.install;

/** How MM Auto installs an APK. Tried in this order of preference: CLASSIC, SHIZUKU, ROOT. */
public enum InstallMode {
    /** Android's own installer dialog. The install source is repaired afterwards when possible. */
    CLASSIC,
    /** Silent install through a Shizuku shell service with the Play Store as installer. */
    SHIZUKU,
    /** Silent install through su with the Play Store as installer. */
    ROOT
}
