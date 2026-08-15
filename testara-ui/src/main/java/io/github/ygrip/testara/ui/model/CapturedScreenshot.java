package io.github.ygrip.testara.ui.model;

/** Screenshot bytes together with the media type used for report attachment. */
public record CapturedScreenshot(byte[] bytes, String mimeType) {}
