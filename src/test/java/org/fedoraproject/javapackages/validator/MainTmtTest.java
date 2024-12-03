package org.fedoraproject.javapackages.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
            Path path = Path.of("src/test/resources").resolve(loc);
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
        TestFactory.validators.add(new TestValidator(name, validator));
    }

    void runMain(int expRc) throws Exception {
        args.add(TestFactory.class.getCanonicalName());
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
    void testEmptyFactory() throws Exception {
        args.add(TestFactory.class.getCanonicalName());
        runMain(2);
        expectResults("results.yaml");
        assertTrue(readResult("results.yaml").contains("result: error"), "result is error");
    }

    @Test
    void testCrashLog() throws Exception {
        // Corrupted empty file triggers the crash
        Files.createFile(artifactsDir.resolve("empty.rpm"));
        addValidator("/something", (rpms, v) -> {
            // not expected to be ran, crash happens before the code gets here
        });
        runMain(2);
        expectResults("results.yaml", "crash.log");

        assertTrue(readResult("results.yaml").contains("result: error"), "result is error");
        assertTrue(readResult("crash.log").contains("java.io.IOException: Unable to open RPM file"),
                "crash.log contains stack trace");
    }

    @Test
    void testHtmlEscaping() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");

        addValidator("/html-new-line", (rpms, v) ->
            v.warn("first_line\nsecond_line\n{0}", Decorated.plain("&third_line")));

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
        addValidator("/other", (rpms, v) -> v.fail("other validator should be excluded"));

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
        addValidator("/skipper", (rpms, v) -> v.skip("skeep"));
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
    void testCompilerProperties() throws Exception {
        writeResource(tmtTree, "Foo.java", "package dummy; class Foo {} enum BAR {}");
        writeResource(tmtTree, "javapackages-validator.properties", "compiler.release=4242");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        runMain(2);
        expectResults( //
                "crash.log", //
                "results.yaml");
        assertTrue(readResult("crash.log").contains("release version 4242 not supported"),
                "crash log indicates that --release was passed to javac");
    }

    @Test
    void testCompileDependencies() throws Exception {
        writeResource(tmtTree, "Fact.java", "package my; import org.apache.oro.text.regex.Pattern; class Fact {}");

        writeResource(tmtTree, "javapackages-validator.properties", "dependencies=oro:oro:2.0.8");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        addValidator("/skipper", (rpms, v) -> v.skip("skeep"));
        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: skip"), "result is skip");
        expectResults( //
                "my/Fact.class", //
                "local-repo/oro/oro/2.0.8/oro-2.0.8.jar", //
                "results.yaml");
    }

    @Test
    void testCompileMalformedDependency() throws Exception {
        writeResource(tmtTree, "javapackages-validator.properties", "dependencies=foo");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        runMain(2);
        expectResults( //
                "crash.log", //
                "results.yaml");
        assertTrue(readResult("crash.log").contains("Bad artifact coordinates foo"),
                "crash log contains message about bad artifact coordinates");
    }

    @Test
    void testCompileMissingDependency() throws Exception {
        writeResource(tmtTree, "javapackages-validator.properties", "dependencies=foo:bar:1.2.3",
                "repositories=/dummy/repo");
        args.add("-sp");
        args.add(tmtTree.toString());
        args.add("-d");
        args.add(tmtTestData.toString());
        runMain(2);
        expectResults( //
                "crash.log", //
                "results.yaml");
        assertTrue(
                readResult("crash.log")
                        .contains("ArtifactNotFoundException: Could not find artifact foo:bar:jar:1.2.3 in repo0"),
                "crash log contains ArtifactNotFoundException");
    }

    @Test
    void testNameSlash() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");
        addValidator("/", (rpms, v) -> v.pass("passed"));
        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: pass"), "result is pass");
        assertTrue(readResult("results.yaml").contains("results/.log"), "log path looks correct");
        expectResults( //
                "results/.log", //
                "results/.html", //
                "results.yaml");
    }

    @Test
    void testParallel() throws Exception {
        copyResources(artifactsDir, "arg_file_iterator/dangling-symlink-1-1.noarch.rpm");
        CountDownLatch cdA = new CountDownLatch(1);
        CountDownLatch cdB = new CountDownLatch(1);
        addValidator("/one", (rpms, v) -> {
            System.err.println("Running one...");
            cdA.countDown();
            if (cdB.await(2, TimeUnit.SECONDS)) {
                System.err.println("PASS");
                v.pass("passed");
            } else {
                System.err.println("FAIL");
                v.fail("failed");
            }
            System.err.println("Done one...");
        });
        addValidator("/two", (rpms, v) -> {
            System.err.println("Running two...");
            cdB.countDown();
            if (cdA.await(2, TimeUnit.SECONDS)) {
                System.err.println("PASS");
                v.pass("passed");
            } else {
                System.err.println("FAIL");
                v.fail("failed");
            }
            System.err.println("Done two...");

        });
        runMain(0);
        assertTrue(readResult("results.yaml").contains("result: pass"), "result is pass");
        assertFalse(readResult("results.yaml").contains("result: fail"), "result is pass");
    }
}
