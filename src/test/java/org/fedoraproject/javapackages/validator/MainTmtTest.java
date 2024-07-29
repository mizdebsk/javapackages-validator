package org.fedoraproject.javapackages.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.fedoraproject.javapackages.validator.spi.Decorated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTmtTest {
    @TempDir
    Path tmtTree;
    @TempDir
    Path tmtTestData;
    @TempDir
    Path artifactsDir;

    Main main;
    List<String> args;

    @BeforeEach
    void setUp() {
        main = MainTmt.create(tmtTestData, tmtTree);

        args = new ArrayList<>();
        args.add("-x");
        args.add("-f");
        args.add(artifactsDir.toString());

        TestFactory.validators.clear();
    }

    void copyResources(Path destDir, String... locations) throws Exception {
        for (String loc : locations) {
            Path path = Paths.get("src/test/resources").resolve(loc);
            Path dest = destDir.resolve(path.getFileName());
            Files.copy(path, dest);
        }
    }

    void writeResource(Path destDir, String name, String... lines) throws Exception {
        try (var bw = Files.newBufferedWriter(destDir.resolve(name), StandardCharsets.UTF_8)) {
            for (String line : lines) {
                bw.write(line);
                bw.write('\n');
            }
        }
    }

    void addValidator(String name, AnonymousValidator validator) {
        if (TestFactory.validators.isEmpty()) {
            args.add(TestFactory.class.getCanonicalName());
        }
        TestFactory.validators.add(new TestValidator(name, validator));
    }

    void runMain(int expRc) throws Exception {
        int rc = main.run(args.toArray(new String[args.size()]));
        assertEquals(expRc, rc, "check expected return code");
    }

    void expectResults(String... locations) {
        for (String loc : locations) {
            Path path = tmtTestData.resolve(loc);
            assertTrue(Files.isRegularFile(path), "Result " + path + "is present");
        }
    }

    String readResult(String loc) throws Exception {
        expectResults(loc);
        Path path = tmtTestData.resolve(loc);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void testCrashLog() throws Exception {
        // Corrupted empty file triggers the crash
        Files.createFile(artifactsDir.resolve("empty.rpm"));

        runMain(2);
        expectResults("results.yaml", "crash.log");

        assertTrue(readResult("results.yaml").contains("result: error"), "result is error");
        assertTrue(readResult("crash.log").contains("java.io.IOException: Unable to open RPM file"),
                "crash.log contains stack trace");
    }

    @Test
    void testHtmlEscaping() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");

        addValidator("/html-new-line", (rpms, v) -> {
            v.warn("first_line\nsecond_line\n{0}", Decorated.plain("&third_line"));
        });

        runMain(0);
        expectResults( //
                "results/html-new-line.html", //
                "results/html-new-line.log", //
                "results.yaml");

        assertTrue(readResult("results/html-new-line.html") //
                .contains("first_line<br>second_line"), //
                "new line is represented as <br>");
        assertTrue(readResult("results/html-new-line.html") //
                .contains("<text class=\"black\">&amp;third_line</text>"), //
                "HTML is correctly decorated and escaped");
        assertTrue(readResult("results.yaml").contains("result: warn"), "result is warn");
    }

    @Test
    void testCustomConfig() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");
        writeResource(tmtTree, "javapackages-validator.yaml", //
                "/myvalidator:", //
                "  - foo", //
                "  - bar", //
                "exclude-tests-matching:", //
                "  - /ot.er" //
        );

        List<String> theArgs = new ArrayList<>();
        addValidator("/myvalidator", (rpms, v) -> {
            if (v.getArgs() != null) {
                theArgs.addAll(v.getArgs());
            }
            v.pass("passed");
        });
        addValidator("/other", (rpms, v) -> {
            v.fail("other validator should be excluded");
        });

        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: pass"), "result is pass");

        assertEquals(2, theArgs.size());
        assertEquals("foo", theArgs.get(0));
        assertEquals("bar", theArgs.get(1));
    }

    @Test
    void testCompilation() throws Exception {
        writeResource(tmtTree, "Foo.java", "package dummy; class Foo {} enum BAR {}");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: skip"), "result is skip");
        expectResults( //
                "dummy/Foo.class", //
                "dummy/BAR.class", //
                "results.yaml");
    }

    @Test
    void testCompilationFailure() throws Exception {
        writeResource(tmtTree, "Boom.java", "package pkg; import java.util.foo.bar; class Boom{}");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        runMain(2);
        expectResults( //
                "crash.log", //
                "results.yaml");
        assertTrue(readResult("crash.log").contains("package java.util.foo does not exist"),
                "crash log contains the actual reason for compilation failure");
    }

    @Test
    void testNameSlash() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");
        addValidator("/", (rpms, v) -> {
            v.pass("passed");
        });
        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: pass"), "result is pass");
        assertTrue(readResult("results.yaml").contains("results/.log"), "log path looks correct");
        expectResults( //
                "results/.log", //
                "results/.html", //
                "results.yaml");
    }
}
