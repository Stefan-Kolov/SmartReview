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

    @Value("${smartreview.repo.max-cache-size-gb:5}")
    private long maxCacheSizeGb;

    public Map<String, String> cloneAndExtract(String repoUrl) throws GitAPIException, IOException {
        Path cacheDir = getCacheDir(repoUrl);

        evictOldestIfNeeded();

        if (Files.exists(cacheDir)) {
            if (hasNewCommits(cacheDir)) {
                log.info("New commits found, pulling latest: {}", repoUrl);
                pullLatest(cacheDir, repoUrl);
            } else {
                log.info("Cache hit, no new commits — reusing: {}", cacheDir);
            }
        } else {
            log.info("Cache miss, cloning: {}", repoUrl);
            Files.createDirectories(cacheDir.getParent());
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(cacheDir.toFile())
                    .setDepth(1)
                    .setTimeout(30)
                    .call()
                    .close();
        }

        log.info("Extracting source files from: {}", cacheDir);
        return extractSourceFiles(cacheDir);
    }

    public void evictCache(String repoUrl) {
        try {
            Path cacheDir = getCacheDir(repoUrl);
            if (Files.exists(cacheDir)) {
                deleteDirectory(cacheDir);
                log.info("Evicted cache for: {}", repoUrl);
            }
        } catch (IOException e) {
            log.warn("Failed to evict cache for {}: {}", repoUrl, e.getMessage());
        }
    }

    private Path getCacheDir(String repoUrl) {
        String dirName = repoUrl
                .replaceAll("https?://github\\.com/", "")
                .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                .replaceAll("\\.git$", "");
        return Path.of(cloneBaseDir, "cache", dirName);
    }

    private boolean hasNewCommits(Path cacheDir) {
        try (Git git = Git.open(cacheDir.toFile())) {
            git.fetch()
                    .setRemote("origin")
                    .setTimeout(15)
                    .call();

            String localCommit = git.getRepository()
                    .resolve("HEAD")
                    .getName();

            String remoteCommit = git.getRepository()
                    .resolve("origin/HEAD") != null
                    ? git.getRepository().resolve("origin/HEAD").getName()
                    : localCommit;

            boolean hasNew = !localCommit.equals(remoteCommit);
            log.info("Local: {} Remote: {} — {}",
                    localCommit.substring(0, 7),
                    remoteCommit.substring(0, 7),
                    hasNew ? "new commits found" : "up to date");

            return hasNew;

        } catch (Exception e) {
            log.warn("Could not check remote commits, will re-clone: {}", e.getMessage());
            return true;
        }
    }

    private void pullLatest(Path cacheDir, String repoUrl) {
        try (Git git = Git.open(cacheDir.toFile())) {
            git.pull()
                    .setTimeout(30)
                    .call();
            log.info("Pull complete for: {}", repoUrl);
        } catch (Exception e) {
            log.warn("Pull failed, evicting cache and will re-clone: {}", e.getMessage());
            try {
                deleteDirectory(cacheDir);
            } catch (IOException ex) {
                log.warn("Failed to delete corrupt cache: {}", ex.getMessage());
            }
        }
    }

    private void evictOldestIfNeeded() throws IOException {
        Path cacheRoot = Path.of(cloneBaseDir, "cache");
        if (!Files.exists(cacheRoot)) return;

        long totalSize = Files.walk(cacheRoot)
                .filter(Files::isRegularFile)
                .mapToLong(p -> p.toFile().length())
                .sum();

        long maxBytes = maxCacheSizeGb * 1024 * 1024 * 1024;

        if (totalSize > maxBytes) {
            Files.list(cacheRoot)
                    .filter(Files::isDirectory)
                    .min(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .ifPresent(oldest -> {
                        try {
                            log.info("Cache full ({} GB), evicting oldest: {}",
                                    maxCacheSizeGb, oldest.getFileName());
                            deleteDirectory(oldest);
                        } catch (IOException e) {
                            log.warn("Failed to evict oldest cache entry: {}", e.getMessage());
                        }
                    });
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

                if (!shouldReview(relativePath, fileName, content)) {
                    log.debug("Skipping low-value file: {}", relativePath);
                    return FileVisitResult.CONTINUE;
                }

                files.put(relativePath, content);
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Extracted {} source files", files.size());
        return files;
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

    private boolean shouldReview(String path, String fileName, String content) {
        if (path.contains("/test/") || path.contains("\\test\\")) return false;
        if (fileName.contains("Test") || fileName.contains("Spec")
                || fileName.contains(".test.") || fileName.contains(".spec.")) return false;

        if (path.contains("generated") || path.contains("migration")) return false;

        if (fileName.equals("application.properties")
                || fileName.equals("application.yml")
                || fileName.equals("application.yaml")
                || fileName.equals("pom.xml")
                || fileName.equals("build.gradle")
                || fileName.equals("package.json")
                || fileName.equals("package-lock.json")
                || fileName.equals("vite.config.js")
                || fileName.equals("vite.config.ts")
                || fileName.equals(".eslintrc.js")
                || fileName.equals("tailwind.config.js")) return false;

        long lineCount = content.lines().count();
        if (lineCount < 15) return false;

        boolean looksLikeDto = content.contains("record ") && lineCount < 30;
        boolean looksLikeEnum = content.contains("enum ") && lineCount < 40;
        return !looksLikeDto && !looksLikeEnum;
    }
}