/*
 * Copyright 2024 Erik C. Thauvin (https://erik.thauvin.net/)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.bld.operations;

import rife.bld.operations.exceptions.ExitStatusException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.spi.ToolProvider;

/**
 * Provides common features for tool providers.
 *
 * @author <a href="https://erik.thauvin.net/">Erik C. Thauvin</a>
 * @since 2.1.0
 */
public abstract class AbstractToolProviderOperation<T extends AbstractToolProviderOperation<T>>
        extends AbstractOperation<AbstractToolProviderOperation<T>> {
    private final List<String> toolArgs_ = new ArrayList<>();
    private final String toolName_;

    /**
     * Provides the name of the tool.
     *
     * @param toolName the tool name
     */
    public AbstractToolProviderOperation(String toolName) {
        toolName_ = toolName;
    }

    /**
     * Runs an instance of the tool.
     * <p>
     * On success, command line arguments are automatically cleared.
     *
     * @throws Exception if an error occurred
     */
    @Override
    public void execute() throws Exception {
        if (toolArgs_.isEmpty()) {
            System.err.println("No " + toolName_ + " command line arguments specified.");
            throw new ExitStatusException(ExitStatusException.EXIT_FAILURE);
        }

        var tool = ToolProvider.findFirst(toolName_).orElseThrow(() ->
                new IllegalStateException("No " + toolName_ + " tool found."));

        var status = tool.run(System.out, System.err, toolArgs_.toArray(new String[0]));
        if (status != 0) {
            System.out.println(tool.name() + ' ' + String.join(" ", toolArgs_));
        }

        ExitStatusException.throwOnFailure(status);

        toolArgs_.clear();
    }

    /**
     * Adds arguments to pass to the tool.
     *
     * @param args tbe list of arguments
     * @return this operation
     */
    @SuppressWarnings({"unchecked"})
    public T toolArgs(List<String> args) {
        toolArgs_.addAll(args);
        return (T) this;
    }

    /**
     * Adds arguments to pass to the tool.
     *
     * @param args one or more arguments
     * @return this operation
     */
    public T toolArgs(String... args) {
        return toolArgs(List.of(args));
    }

    /**
     * Returns the tool's arguments.
     *
     * @return the arguments
     */
    public List<String> toolArgs() {
        return toolArgs_;
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files one or more files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    public T toolArgsFromFile(String... files) throws IOException {
        return toolArgsFromFileStrings(List.of(files));
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files one or more files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    public T toolArgsFromFile(Path... files) throws IOException {
        return toolArgsFromFilePaths(List.of(files));
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files the list of files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    public T toolArgsFromFile(List<File> files) throws IOException {
        return toolArgsFromFileStrings(files.stream().map(File::getAbsolutePath).toList());
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files one or more files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    public T toolArgsFromFile(File... files) throws IOException {
        return toolArgsFromFile(List.of(files));
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files the list of files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    public T toolArgsFromFilePaths(List<Path> files) throws IOException {
        return toolArgsFromFileStrings(files.stream().map(Path::toFile).map(File::getAbsolutePath).toList());
    }

    /**
     * Parses arguments to pass to the tool from the given files.
     *
     * @param files the list of files
     * @return this operation instance
     * @throws FileNotFoundException if a file cannot be found
     */
    @SuppressWarnings({"unchecked"})
    public T toolArgsFromFileStrings(List<String> files) throws IOException {
        var args = new ArrayList<String>();

        for (var file : files) {
            try (var reader = Files.newBufferedReader(Paths.get(file), Charset.defaultCharset())) {
                var tokenizer = new CommandLineTokenizer(reader);
                String token;
                while ((token = tokenizer.nextToken()) != null) {
                    args.add(token);
                }
            }
        }

        toolArgs(args);

        return (T) this;
    }

    /**
     * Adds arguments to pass to the tool.
     *
     * @param args the argument-value pairs to add
     * @return this operation
     */
    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    protected T toolArgs(Map<String, String> args) {
        args.forEach((k, v) -> {
            toolArgs_.add(k);
            if (v != null && !v.isEmpty()) {
                toolArgs_.add(v);
            }
        });
        return (T) this;
    }

    /**
     * Tokenize command line arguments.
     *
     * <ul>
     * <li>Arguments containing spaces should be quoted</li>
     * <li>Escape sequences and comments are supported</li>
     * </ul>
     */
    public static class CommandLineTokenizer {
        private final StringBuilder buf_ = new StringBuilder();
        private final Reader input_;
        private int ch_;

        public CommandLineTokenizer(Reader input) throws IOException {
            input_ = input;
            ch_ = input.read();
        }

        public String nextToken() throws IOException {
            trimWhitespaceOrComments();
            if (ch_ == -1) {
                return null;
            }

            buf_.setLength(0); // reset buffer

            char quote = 0;
            while (ch_ != -1) {
                if (ch_ == '\'' || ch_ == '"') { // quotes
                    if (quote == 0) { // begin quote
                        quote = (char) ch_;
                    } else if (quote == ch_) { // end quote
                        quote = 0;
                    } else {
                        buf_.append((char) ch_);
                    }
                } else if (ch_ == '\\') { // escaped
                    ch_ = input_.read();
                    buf_.append(handleEscapeSequence());
                } else if (quote == 0 && Character.isWhitespace(ch_)) { // whitespaces
                    break;
                } else {
                    buf_.append((char) ch_);
                }
                ch_ = input_.read();
            }
            return buf_.toString();
        }

        private char handleEscapeSequence() {
            return switch (ch_) {
                case -1 -> '\\';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'f' -> '\f';
                default -> (char) ch_;
            };
        }

        private void trimWhitespaceOrComments() throws IOException {
            while (ch_ != -1) {
                if (Character.isWhitespace(ch_)) { // Skip whitespaces
                    ch_ = input_.read();
                } else if (ch_ == '#') {
                    // Skip the entire comment until a new line or end of input
                    do {
                        ch_ = input_.read();
                    } while (ch_ != -1 && ch_ != '\n' && ch_ != '\r');
                } else {
                    break;
                }
            }
        }
    }
}