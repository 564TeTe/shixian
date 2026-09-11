package com.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Shared HTTP response for Excel templates and reports. */
public final class ExcelDownloadUtils {
    private ExcelDownloadUtils() {}

    public static ResponseEntity<byte[]> download(byte[] bytes, String name) {
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(bytes);
    }
}
