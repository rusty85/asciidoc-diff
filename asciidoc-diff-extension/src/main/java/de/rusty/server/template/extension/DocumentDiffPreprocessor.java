package de.rusty.server.template.extension;

import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.Preprocessor;
import org.asciidoctor.extension.PreprocessorReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.System.lineSeparator;

/**
 * This class provides utilities to process documents based on `git diff` results.
 * It leverages the template to highlight changes in the documents.
 * <p>
 * By fetching the `git diff`, the class identifies changes between the latest tag and the HEAD
 * of a given document. The identified differences are then highlighted using a
 * Velocity template and reflected in the processed document.
 * </p>
 *
 * @author rusty87
 */
public class DocumentDiffPreprocessor extends Preprocessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentDiffPreprocessor.class);
    private static final Pattern EXCLUDE_LINES = Pattern.compile("^(?!(\\+|toc::.+|:|\\[|<<<|'''|=+|\\|=+)).*");
    private static final String CYAN = "\033[0;36m";
    private static final String COLOR_RESET = "\033[0m";
    private static final String PROJECT_ROOT_ATTRIBUTE = "project-root";

    /**
     * Processes the given document and applies changes based on the git diff result.
     * It retrieves the differences of a specific file between its latest tag and the HEAD.
     * The changes are then highlighted using a template.
     *
     * @param document The document being processed. It is expected to contain an attribute named
     *                 by the constant PROJECT_ROOT_ATTRIBUTE to determine the root directory for processing.
     * @param reader   The PreprocessorReader that provides the content lines of the document for processing.
     * @throws IllegalStateException If there's an error while executing the git diff command
     *                               or if there's an issue starting the process.
     */
    @Override
    public void process(Document document, PreprocessorReader reader) {

        final List<String> lines = reader.readLines();
        final List<String> modifiedLines = new ArrayList<>();

        final File baseDir = new File(document.getAttribute(PROJECT_ROOT_ATTRIBUTE).toString());
        final Set<String> diffLines;

        try {
            diffLines = executeDiffCommand(baseDir);
        } catch (IOException e) {

            throw new IllegalStateException("Startup of the process failed!", e);
        }

        for (String line : lines) {

            String highlightedLine = Optional.of(line)
                    .filter(diffLines::contains)
                    .filter(l -> !"".equals(l))
                    .map(DocumentDiffPreprocessor::highlightLine)
                    .orElse(line);

            modifiedLines.add(highlightedLine);
        }
        LOGGER.info("Highlighted lines count {}{}{}", CYAN, modifiedLines.size(), COLOR_RESET);

        reader.restoreLines(modifiedLines);
    }

    /**
     * Highlights a given line of text based on predefined highlighting patterns.
     *
     * <p>This method iterates through predefined highlighting patterns and applies
     * the appropriate formatting to the input line if a match is found. For table cell
     * highlighting, it specifically looks for lines that start with a "|". If no matching
     * highlighting pattern is found, a warning is logged, and the method returns {@code null}.</p>
     *
     * @param line The input line of text to be highlighted.
     * @return The highlighted line if a matching pattern is found; {@code null} otherwise.
     */
    private static String highlightLine(String line) {

        for (Highlighting highlighting : Highlighting.values()) {
            if (Highlighting.TABLE_CELL.equals(highlighting) && line.startsWith("|")) {

                Matcher matcher = highlighting.getPattern().matcher(line);
                StringBuilder highlightedTableCell = new StringBuilder();
                while (matcher.find()) {
                    highlightedTableCell.append("|")
                            .append("[.highlight]#")
                            .append(matcher.group(1).trim())
                            .append("#");
                }
                return highlightedTableCell.toString();
            }

            if (highlighting.getPattern().asPredicate().test(line)) {
                return line.replaceAll(highlighting.getPattern().pattern(), highlighting.getReplacement());
            }
        }
        LOGGER.warn("Highlighting is not supported {}{}{}", CYAN, line, COLOR_RESET);
        return null;
    }

    /**
     * Executes the 'git diff' command in the parent directory of the provided base directory
     * to retrieve differences between the latest tag and the HEAD for the given file.
     * Only lines added (starting with "+") are collected and returned.
     *
     * @param baseDir The directory whose parent will be used as the context for executing the command.
     *                Additionally, the name of the baseDir and files with '.adoc' extension will be used as the target for the diff.
     * @return A list of lines that have been added (starting with "+") between the latest tag and HEAD for the target file.
     * @throws IOException If there's an error while reading the process output or if the process execution fails.
     */
    private Set<String> executeDiffCommand(File baseDir) throws IOException {

        final Set<String> diffLines;
        Process process = getProcess(baseDir.getParent(),
                "git",
                "diff",
                executeDescribeCommand(baseDir),
                "HEAD",
                "-U0",
                "--",
                baseDir.getName(),
                "*.adoc");

        try (final BufferedReader readerDiff = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            diffLines = readerDiff.lines()
                    .filter(l -> l.startsWith("+"))
                    .map(l -> l.substring(1))
                    .filter(EXCLUDE_LINES.asPredicate())
                    .collect(Collectors.toSet());
        }

        checkExitCode(process, "git diff <TAG> HEAD -U0 -- <BASE_DIR> *.adoc");

        return diffLines;
    }

    /**
     * Executes the 'git describe' command in the parent directory of the provided base directory
     * to retrieve the latest tag (abbreviated to its shortest unique prefix).
     *
     * @param baseDir The directory whose parent will be used as the context for executing the command.
     * @return The latest git tag found in the parent directory of the provided base directory.
     * @throws IOException If there's an error while reading the process output or if the process execution fails.
     */
    private static String executeDescribeCommand(File baseDir) throws IOException {

        StringBuilder output = new StringBuilder();
        Process process = getProcess(baseDir.getParent(), "git", "describe", "--tags", "--abbrev=0");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(lineSeparator());
            }
        }
        checkExitCode(process, "git describe --tags --abbrev=0");

        return output.toString().trim();
    }

    /**
     * Creates and starts a process based on a specified base directory and command sequence.
     *
     * @param baseDir The base directory where the process should be started.
     * @param command The command sequence to start the process.
     * @return A started {@link Process} based on the provided parameters.
     * @throws IOException If there's an error while starting the process.
     */
    private static Process getProcess(String baseDir, String... command) throws IOException {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        processBuilder.directory(new File(baseDir));

        return processBuilder.start();
    }

    /**
     * Checks the exit code of the provided process. If the exit code is not 0 (indicating an error or abnormal termination),
     * an IOException is thrown detailing the error code.
     *
     * @param process The process whose exit code is to be checked.
     * @param command The command sequence to start the process.
     * @throws IOException If the process exit code is not 0, or if waiting for the process is interrupted.
     */
    private static void checkExitCode(Process process, String command) throws IOException {

        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            LOGGER.warn("Waiting for the process was interrupted.", e);
            Thread.currentThread().interrupt();
        }

        if (exitCode != 0) {
            throw new IOException(process + " execution failed! Please check the results of " + CYAN + command + COLOR_RESET + " manually");
        }
    }
}
