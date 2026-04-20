package com.smartreview.smartreview.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Slf4j
@Service
public class RepoService {

    @Value("${smartreview.repo.clone-base-dir}")
    private String cloneBaseDir;

    @Value("${smartreview.repo.max-file-size-kb}")
    private long maxFileSizeKb;

    @Value("${smartreview.repo.supported-extensions}")
    private List<String> supportedExtensions;

    public Map<String, String> cloneAndExtract(String repoUrl) throws GitAPIException, IOException {
        Path cloneTarget = prepareCloneDirectory();
        log.info("Cloning {} into {}", repoUrl, cloneTarget);

        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(cloneTarget.toFile())
                .setDepth(1)
                .call()) {

            log.info("Clone complete. Extracting source files...");
            return extractSourceFiles(cloneTarget);
        }
    }

    private Map<String, String> extractSourceFiles(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (name.startsWith(".") || name.equals("node_modules") || name.equals("target")
                        || name.equals("build") || name.equals("dist") || name.equals("vendor")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();

                boolean supported = supportedExtensions.stream()
                        .anyMatch(fileName::endsWith);
                if (!supported) return FileVisitResult.CONTINUE;

                long sizeKb = attrs.size() / 1024;
                if (sizeKb > maxFileSizeKb) {
                    log.debug("Skipping large file ({} KB): {}", sizeKb, file);
                    return FileVisitResult.CONTINUE;
                }

                String relativePath = root.relativize(file).toString();
                String content = Files.readString(file);
                files.put(relativePath, content);

                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Extracted {} source files", files.size());
        return files;
    }

    public void cleanup(String repoUrl) {
        try {
            Path cloneTarget = resolveCloneDir(repoUrl);
            if (Files.exists(cloneTarget)) {
                deleteDirectory(cloneTarget);
                log.info("Cleaned up cloned repo at {}", cloneTarget);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up cloned repo: {}", e.getMessage());
        }
    }

    private Path prepareCloneDirectory() throws IOException {
        Path base = Path.of(cloneBaseDir);
        Files.createDirectories(base);
        return Files.createTempDirectory(base, "repo-");
    }

    private Path resolveCloneDir(String repoUrl) {
        String repoName = repoUrl.replaceAll("[^a-zA-Z0-9]", "_");
        return Path.of(cloneBaseDir, repoName);
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public String detectLanguage(String filePath) {
        if (filePath.endsWith(".java"))  return "Java";
        if (filePath.endsWith(".py"))    return "Python";
        if (filePath.endsWith(".ts"))    return "TypeScript";
        if (filePath.endsWith(".js"))    return "JavaScript";
        if (filePath.endsWith(".go"))    return "Go";
        if (filePath.endsWith(".cs"))    return "C#";
        if (filePath.endsWith(".cpp"))   return "C++";
        if (filePath.endsWith(".c"))     return "C";
        if (filePath.endsWith(".rb"))    return "Ruby";
        if (filePath.endsWith(".php"))   return "PHP";
        return "Unknown";
    }
}