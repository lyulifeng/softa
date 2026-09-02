package io.softa.starter.file.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads a source file by its repository-relative path, from wherever the test was launched. */
final class SourceFiles {

    private SourceFiles() {}

    static String read(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException e) {
                    throw new IllegalStateException("cannot read " + candidate, e);
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not find " + relative + " from " + Path.of("").toAbsolutePath());
    }

    static int countOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
