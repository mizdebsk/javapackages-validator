package org.fedoraproject.javapackages.validator.validators;

import static org.fedoraproject.javapackages.validator.TestCommon.assertFailOne;
import static org.fedoraproject.javapackages.validator.TestCommon.assertPass;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.fedoraproject.javapackages.validator.TestCommon;
import org.junit.jupiter.api.Test;

public class JpmsProvidesValidatorTest {
    private static final Path JPMS_AUTOMATIC = TestCommon.RPM_PATH_PREFIX.resolve(Paths.get("noarch/jpms-automatic-1-1.noarch.rpm"));
    private static final Path JPMS_INVALID = TestCommon.RPM_PATH_PREFIX.resolve(Paths.get("noarch/jpms-invalid-1-1.noarch.rpm"));

    @Test
    public void testAutomaticModuleName() throws Exception {
        var validator = new JpmsProvidesValidator();
        validator.validate(TestCommon.fromPaths(JPMS_AUTOMATIC));
        assertPass(validator.build());
    }

    @Test
    public void testInvalidModule() throws Exception {
        var validator = new JpmsProvidesValidator();
        validator.validate(TestCommon.fromPaths(JPMS_INVALID));
        assertFailOne(validator.build());
    }
}
