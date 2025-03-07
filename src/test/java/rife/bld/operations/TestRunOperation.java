/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.bld.operations;

import org.junit.jupiter.api.Test;
import rife.bld.NamedFile;
import rife.tools.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;

import static org.junit.jupiter.api.Assertions.*;

public class TestRunOperation {
    @Test
    void testInstantiation() {
        var operation = new RunOperation();
        assertNotNull(operation.workDirectory());
        assertTrue(operation.workDirectory().exists());
        assertTrue(operation.workDirectory().isDirectory());
        assertTrue(operation.workDirectory().canWrite());
        assertTrue(operation.environment().isEmpty());
        assertEquals("java", operation.javaTool());
        assertTrue(operation.javaOptions().isEmpty());
        assertTrue(operation.classpath().isEmpty());
        assertNull(operation.mainClass());
        assertNull(operation.module());
        assertTrue(operation.runOptions().isEmpty());
        assertNull(operation.outputProcessor());
        assertNull(operation.errorProcessor());
        assertNull(operation.process());
    }

    @Test
    void testPopulation()
    throws Exception {
        var environment = Map.of("env1", "val1", "env2", "val2", "env3", "val3");
        var environment_full = Map.of("env1", "val1", "env2", "val2", "env3", "val3", "env4", "val4");
        var work_directory = Files.createTempDirectory("test").toFile();
        try {
            var java_tool = "javatool";
            var run_java_option1 = "runJavaOption1";
            var run_java_option2 = "runJavaOption2";
            var run_classpath1 = "runClasspath1";
            var run_classpath2 = "runClasspath2";
            var run_option1 = "runOption1";
            var run_option2 = "runOption2";
            var main_class = "mainClass";
            var module = "module";
            Function<String, Boolean> run_output_consumer = (String) -> true;
            Function<String, Boolean> run_error_consumer = (String) -> true;

            var operation1 = new RunOperation();
            operation1
                .workDirectory(work_directory)
                .environment(environment)
                .environment("env4", "val4")
                .javaTool(java_tool)
                .javaOptions(List.of(run_java_option1, run_java_option2))
                .classpath(List.of(run_classpath1, run_classpath2))
                .mainClass(main_class)
                .module(module)
                .runOptions(List.of(run_option1, run_option2))
                .outputProcessor(run_output_consumer)
                .errorProcessor(run_error_consumer);

            assertEquals(work_directory, operation1.workDirectory());
            assertEquals(environment_full, operation1.environment());
            assertEquals(java_tool, operation1.javaTool());
            assertTrue(operation1.javaOptions().contains(run_java_option1));
            assertTrue(operation1.javaOptions().contains(run_java_option2));
            assertTrue(operation1.classpath().contains(run_classpath1));
            assertTrue(operation1.classpath().contains(run_classpath2));
            assertEquals(main_class, operation1.mainClass());
            assertEquals(module, operation1.module());
            assertTrue(operation1.runOptions().contains(run_option1));
            assertTrue(operation1.runOptions().contains(run_option2));
            assertSame(run_output_consumer, operation1.outputProcessor());
            assertSame(run_error_consumer, operation1.errorProcessor());

            var operation2 = new RunOperation();
            operation2.workDirectory(work_directory);
            operation2.environment(environment);
            operation2.environment("env4", "val4");
            operation2.javaTool(java_tool);
            operation2.javaOptions().add(run_java_option1);
            operation2.javaOptions().add(run_java_option2);
            operation2.classpath().add(run_classpath1);
            operation2.classpath().add(run_classpath2);
            operation2.mainClass(main_class);
            operation2.module(module);
            operation2.runOptions().add(run_option1);
            operation2.runOptions().add(run_option2);
            operation2.outputProcessor(run_output_consumer);
            operation2.errorProcessor(run_error_consumer);

            assertEquals(work_directory, operation2.workDirectory());
            assertEquals(environment_full, operation2.environment());
            assertEquals(java_tool, operation2.javaTool());
            assertTrue(operation2.javaOptions().contains(run_java_option1));
            assertTrue(operation2.javaOptions().contains(run_java_option2));
            assertTrue(operation2.classpath().contains(run_classpath1));
            assertTrue(operation2.classpath().contains(run_classpath2));
            assertEquals(main_class, operation2.mainClass());
            assertEquals(module, operation2.module());
            assertTrue(operation2.runOptions().contains(run_option1));
            assertTrue(operation2.runOptions().contains(run_option2));
            assertSame(run_output_consumer, operation2.outputProcessor());
            assertSame(run_error_consumer, operation2.errorProcessor());

            var operation3 = new RunOperation();
            operation3
                .classpath(run_classpath1, run_classpath2)
                .runOptions(run_option1, run_option2);

            assertTrue(operation3.classpath().contains(run_classpath1));
            assertTrue(operation3.classpath().contains(run_classpath2));
            assertTrue(operation3.runOptions().contains(run_option1));
            assertTrue(operation3.runOptions().contains(run_option2));
        } finally {
            FileUtils.deleteDirectory(work_directory);
        }
    }

    @Test
    void testExecute()
    throws Exception {
        var tmp = Files.createTempDirectory("test").toFile();
        try {
            var source_file1 = new File(tmp, "Source1.java");

            FileUtils.writeString("""
                public class Source1 {
                    public final String name_;
                    public Source1() {
                        name_ = System.getenv("execute_name") + System.getenv("execute_number");
                    }
                    
                    public static void main(String[] arguments)
                    throws Exception {
                        System.out.print(new Source1().name_);
                    }
                }
                """, source_file1);
            var build_main = new File(tmp, "buildMain");

            var compile_operation = new CompileOperation()
                .buildMainDirectory(build_main)
                .compileMainClasspath(List.of(build_main.getAbsolutePath()))
                .mainSourceFiles(List.of(source_file1));
            compile_operation.execute();
            assertTrue(compile_operation.diagnostics().isEmpty());

            var output = new StringBuilder();
            var run_operation = new RunOperation()
                .environment(Map.of("execute_name", "source"))
                .environment("execute_number", "1")
                .mainClass("Source1")
                .classpath(List.of(build_main.getAbsolutePath()))
                .outputProcessor(s -> {
                    output.append(s);
                    return true;
                });
            run_operation.execute();

            assertEquals("source1", output.toString());
        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

    @Test
    void testExecuteModule()
    throws Exception {
        var tmp = Files.createTempDirectory("test").toFile();
        try {
            var pkg = new File(tmp, "pkg");
            pkg.mkdirs();
            var source_file1 = new File(pkg, "Source1.java");
            var source_file2 = new File(pkg, "module-info.java");

            FileUtils.writeString("""
                package pkg;
                
                public class Source1 {
                    public final String name_;
                    public Source1() {
                        name_ = System.getenv("execute_name") + System.getenv("execute_number");
                    }
                    
                    public static void main(String[] arguments)
                    throws Exception {
                        System.out.print(new Source1().name_);
                    }
                }
                """, source_file1);

            FileUtils.writeString("""
                module pkg {
                }
                """, source_file2);

            var build_main = new File(tmp, "buildMain");

            var compile_operation = new CompileOperation()
                .buildMainDirectory(build_main)
                .compileMainClasspath(List.of(build_main.getAbsolutePath()))
                .mainSourceFiles(List.of(source_file1, source_file2))
                .moduleMainClass("pkg.Source1");
            compile_operation.execute();
            assertTrue(compile_operation.diagnostics().isEmpty());

            var destination_dir = new File(tmp, "destination");
            var destination_name = "pkg.jar";
            new JarOperation()
                .sourceDirectories(List.of(build_main))
                .destinationDirectory(destination_dir)
                .destinationFileName(destination_name)
                .execute();

            var output = new StringBuilder();
            var run_operation = new RunOperation()
                .environment(Map.of("execute_name", "source"))
                .environment("execute_number", "1")
                .module("pkg")
                .modulePath(new File(destination_dir, destination_name).getAbsolutePath())
                .outputProcessor(s -> {
                    output.append(s);
                    return true;
                });
            run_operation.execute();

            assertEquals("source1", output.toString());
        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

    @Test
    void testFromProject()
    throws Exception {
        var tmp = Files.createTempDirectory("test").toFile();
        try {
            var create_operation = new CreateAppOperation()
                .workDirectory(tmp)
                .packageName("com.example")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation.execute();

            new CompileOperation()
                .fromProject(create_operation.project()).execute();

            var check_result = new StringBuilder();
            new RunOperation()
                .fromProject(create_operation.project())
                .outputProcessor(s -> {
                    check_result.append(s);
                    return true;
                })
                .execute();
            assertEquals("Hello World!", check_result.toString());

        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

    @Test
    void testFromProjectModule()
    throws Exception {
        var tmp = Files.createTempDirectory("test").toFile();
        try {
            var create_operation = new CreateAppOperation()
                .workDirectory(tmp)
                .packageName("com.example")
                .projectName("myapp")
                .downloadDependencies(true);
            create_operation.execute();

            var source_module_info = new File(create_operation.project().srcMainJavaDirectory(), "module-info.java");
            FileUtils.writeString("""
                module com.example {
                }
                """, source_module_info);

            new CompileOperation()
                .fromProject(create_operation.project()).execute();

            var check_result = new StringBuilder();
            new RunOperation()
                .fromProject(create_operation.project())
                .modulePath(create_operation.project().buildMainDirectory().getAbsolutePath())
                .module("com.example")
                .outputProcessor(s -> {
                    check_result.append(s);
                    return true;
                })
                .execute();
            assertEquals("Hello World!", check_result.toString());

        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }
}
