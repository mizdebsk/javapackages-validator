package org.fedoraproject.javapackages.validator.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.fedoraproject.javapackages.validator.TestCommon;
import org.junit.jupiter.api.Test;

public class DuplicateFileCheckTest {
    private static final Path DUPLICATE_FILE1_RPM = Paths.get(TestCommon.RPM_PATH_PREFIX + "/duplicate-file1-1-1.noarch.rpm");
    private static final Path DUPLICATE_FILE2_RPM = Paths.get(TestCommon.RPM_PATH_PREFIX + "/duplicate-file2-1-1.noarch.rpm");

    @Test
    void testIllegalDuplicateFile() throws IOException {
        var result = new DuplicateFileCheck().check(Arrays.asList(DUPLICATE_FILE1_RPM, DUPLICATE_FILE2_RPM), null);
        assertEquals(1, result.size());
    }

    @Test
    void testAllowedDuplicateFile() throws IOException {
        var result = new DuplicateFileCheck().check(Arrays.asList(DUPLICATE_FILE1_RPM, DUPLICATE_FILE2_RPM), (filename, providerRpms) -> true);
        assertEquals(0, result.size());
    }
}