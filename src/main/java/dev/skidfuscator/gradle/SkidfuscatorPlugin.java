package dev.skidfuscator.gradle;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValueFactory;
import dev.skidfuscator.dependanalysis.DependencyAnalyzer;
import dev.skidfuscator.dependanalysis.DependencyResult;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class SkidfuscatorPlugin implements Plugin<Project> {
    @Override
    public void apply(@NotNull Project project) {
        this.addExclude(project);

        NamedDomainObjectContainer<TransformerSpec> transformerContainer =
                project.getObjects().domainObjectContainer(TransformerSpec.class, name -> new TransformerSpec(name));

        SkidfuscatorExtension extension = project.getExtensions().create("skidfuscator", SkidfuscatorExtension.class, transformerContainer);

        project.afterEvaluate(p -> {
            Task jarTask = p.getTasks().findByName("jar");
            Task shadowJarTask = p.getTasks().findByName("shadowJar");
            Task finalTask = (shadowJarTask != null) ? shadowJarTask : jarTask;

            if (finalTask == null) {
                project.getLogger().lifecycle("No jar or shadowJar task found. Skidfuscator will not run automatically.");
                return;
            }

            // Add new task to collect dependencies
            Task collectDependencies = p.getTasks().create("collectSkidfuscatorDependencies", task -> {
                task.doLast(t -> {
                    File depsDir = new File(p.getBuildDir(), "skidfuscator/dependencies");
                    if (!depsDir.exists()) {
                        depsDir.mkdirs();
                    }

                    // Clear existing files
                    Arrays.stream(depsDir.listFiles()).forEach(File::delete);

                    // Collect all runtime dependencies
                    Set<File> deps = p.getConfigurations().getByName("compileClasspath")
                            .getResolvedConfiguration()
                            .getResolvedArtifacts()
                            .stream()
                            .map(artifact -> artifact.getFile())
                            .collect(Collectors.toSet());

                    // Log the initial list of dependencies
                    project.getLogger().lifecycle("Initial dependencies collected (" + deps.size() + "):");
                    deps.forEach(dep -> project.getLogger().lifecycle(" - " + dep.getAbsolutePath()));

                    // Copy dependencies to the deps directory
                    for (File dep : deps) {
                        try {
                            File destFile = new File(depsDir, dep.getName());
                            java.nio.file.Files.copy(
                                dep.toPath(),
                                destFile.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );
                        } catch (IOException e) {
                            project.getLogger().warn("Failed to copy dependency: " + dep.getName(), e);
                        }
                    }
                });
            });

            finalTask.doLast(task -> {
                // Run the collect dependencies task first
                collectDependencies.getActions().forEach(action -> action.execute(task));

                File skidDir = new File(p.getBuildDir(), "skidfuscator");
                if (!skidDir.exists()) {
                    skidDir.mkdirs();
                }

                String resolvedVersion;
                try {
                    resolvedVersion = resolveVersion(extension.getSkidfuscatorVersion());
                } catch (IOException e) {
                    project.getLogger().error("Failed to fetch latest Skidfuscator version: " + e.getMessage());
                    return;
                }

                File versionFile = new File(skidDir, ".version");
                String currentVersion = readVersionFile(versionFile);

                File skidJar = new File(p.getProjectDir(), ".skidfuscator/skidfuscator-" + resolvedVersion + ".jar");
                if (!skidJar.getParentFile().exists()) {
                    skidJar.getParentFile().mkdirs();
                }
                // If version changed or jar not present, re-download
                if (!"dev".equalsIgnoreCase(resolvedVersion) && resolvedVersion.equals(currentVersion) || !skidJar.exists()) {
                    project.getLogger().warn("Could not find Skidfuscator jar at " + skidJar.getAbsolutePath() + ", downloading...");
                    project.getLogger().lifecycle("Downloading Skidfuscator " + resolvedVersion + "...");
                    try {
                        downloadSkidfuscatorJar(resolvedVersion, skidJar);
                        writeVersionFile(versionFile, resolvedVersion);
                    } catch (IOException e) {
                        project.getLogger().error("Failed to download Skidfuscator: " + e.getMessage(), e);
                        return;
                    }
                }

                File outputJar;
                if (shadowJarTask != null) {
                    outputJar = new File(p.getBuildDir(), "libs/" + p.getName() + "-" + p.getVersion() + "-all.jar");
                } else {
                    outputJar = new File(p.getBuildDir(), "libs/" + p.getName() + "-" + p.getVersion() + ".jar");
                }

                if (!outputJar.exists()) {
                    project.getLogger().lifecycle("Output jar not found at " + outputJar.getAbsolutePath() + ", cannot run Skidfuscator.");
                    return;
                }

                // Add the dependencies directory as the single libs folder
                File depsDir = new File(p.getBuildDir(), "skidfuscator/dependencies");
                if (depsDir.exists() && depsDir.listFiles().length > 0) {
                    final List<String> reduced = new ArrayList<>();
                    final DependencyAnalyzer analyzer = new DependencyAnalyzer(
                            outputJar.toPath(),
                            depsDir.toPath()
                    );

                    try {
                        final DependencyResult result = analyzer.analyze();
                        for(DependencyResult.JarDependency jarDependency : result.getJarDependencies()) {
                            project.getLogger().lifecycle("JAR: " + jarDependency.getJarPath().getFileName());
                            project.getLogger().lifecycle("---------------------------------------------------");

                            for(DependencyResult.ClassDependency classDep : jarDependency.getClassesNeeded()) {
                                project.getLogger().lifecycle("  Class: " + classDep.getClassName());

                                for(String reason : classDep.getReasons()) {
                                    project.getLogger().lifecycle("    - " + reason);
                                }
                            }

                            project.getLogger().lifecycle("");
                        }
                        project.getLogger().lifecycle("Reducing dependencies...");
                        reduced.addAll(result.getJarDependencies().stream()
                                .map(DependencyResult.JarDependency::getJarPath)
                                .map(Path::toString)
                                .collect(Collectors.toList()));
                        project.getLogger().lifecycle("Reduced dependencies (" + reduced.size() + "):");
                    } catch (IOException e) {
                        project.getLogger().error("Failed to minimize analyzed dependencies: " + e.getMessage(), e);
                        return;
                    }

                    extension.getLibs().addAll(reduced);
                }

                File configFile = new File(skidDir, extension.getConfigFileName());
                try {
                    writeHoconConfig(extension, configFile);
                } catch (IOException e) {
                    project.getLogger().error("Failed to write config file: " + e.getMessage(), e);
                    return;
                }

                File resultJar = (extension.getOutput() != null)
                        ? new File(extension.getOutput())
                        : new File(outputJar.getParentFile(), outputJar.getName().replace(".jar", "-obf.jar"));

                List<String> args = new ArrayList<>();
                args.add("obfuscate");
                args.add("-cfg"); args.add(configFile.getAbsolutePath());
                args.add("-o"); args.add(resultJar.getAbsolutePath());
                args.add("--debug");

                if (extension.isPhantom()) args.add("-ph");
                if (extension.isFuckit()) args.add("-fuckit");
                if (extension.isDebug()) args.add("-dbg");
                if (extension.isNotrack()) args.add("-notrack");

                if (extension.getRuntime() != null) {
                    File rt = new File(extension.getRuntime());
                    if (rt.exists()) {
                        args.add("-rt");
                        args.add(rt.getAbsolutePath());
                    }
                }

                // Input jar last
                args.add(outputJar.getAbsolutePath());

                project.getLogger().lifecycle("Running Skidfuscator...");
                project.exec(spec -> {
                    spec.setExecutable("java");
                    List<String> fullArgs = new ArrayList<>();
                    fullArgs.add("-jar");
                    fullArgs.add(skidJar.getAbsolutePath());
                    fullArgs.addAll(args);
                    spec.setArgs(fullArgs);
                    spec.setIgnoreExitValue(false);
                });

                project.getLogger().lifecycle("Skidfuscation complete! Obfuscated jar at " + resultJar.getAbsolutePath());
            });
        });
    }

    private String resolveVersion(String requestedVersion) throws IOException {
        if (!"latest".equalsIgnoreCase(requestedVersion)) {
            return requestedVersion;
        }

        URL url = new URL("https://api.github.com/repos/skidfuscatordev/skidfuscator-java-obfuscator/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.connect();
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch latest release info. HTTP " + conn.getResponseCode());
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String json = br.lines().collect(Collectors.joining());
            int tagIndex = json.indexOf("\"tag_name\"");
            if (tagIndex == -1) {
                throw new IOException("Could not find tag_name in release JSON");
            }
            int start = json.indexOf(":", tagIndex) + 1;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            String tag = json.substring(start, end).replaceAll("\"", "").trim();
            return tag.startsWith("v") ? tag.substring(1) : tag;
        }
    }

    private void downloadSkidfuscatorJar(String version, File target) throws IOException {
        String urlStr = "https://github.com/skidfuscatordev/skidfuscator-java-obfuscator/releases/download/" + version + "/skidfuscator.jar";
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream(); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void writeHoconConfig(SkidfuscatorExtension ext, File configFile) throws IOException {
        Config config = buildConfig(ext);
        String rendered = config.root().render(
            ConfigRenderOptions.defaults()
                .setComments(false)
                .setJson(false)
                .setOriginComments(false)
        );

        try (FileWriter fw = new FileWriter(configFile)) {
            fw.write(rendered);
        }
    }

    private Config buildConfig(SkidfuscatorExtension ext) {
        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("exempt", ext.getExempt());
        rootMap.put("libraries", ext.getLibs());

        // Dynamically add transformers
        Map<String, Object> transformerMap = new HashMap<>();
        ext.getTransformers().getTransformers().forEach(spec -> {
            transformerMap.put(spec.getName(), spec.getProperties());
        });

        // Merge transformer configs at root
        rootMap.putAll(transformerMap);

        // Parse the map into a Config
        return ConfigFactory.parseMap(rootMap);
    }

    private String readVersionFile(File versionFile) {
        if (!versionFile.exists()) return "";
        try (BufferedReader br = new BufferedReader(new FileReader(versionFile))) {
            return br.readLine().trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void writeVersionFile(File versionFile, String version) {
        try (FileWriter fw = new FileWriter(versionFile)) {
            fw.write(version);
        } catch (IOException ignored) {}
    }

    private void addExclude(final Project project) {
        // Add gitignore handling at plugin application time
        File gitignore = new File(project.getRootDir(), ".gitignore");
        try {
            // Check if .skidfuscator is already in .gitignore
            boolean needsEntry = true;
            if (gitignore.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(gitignore))) {
                    if (reader.lines().anyMatch(line -> line.trim().equals(".skidfuscator"))) {
                        needsEntry = false;
                    }
                }
            }

            // Append .skidfuscator to .gitignore if needed
            if (needsEntry) {
                try (FileWriter writer = new FileWriter(gitignore, true)) {
                    // Add newline if file exists and doesn't end with one
                    if (gitignore.exists() && gitignore.length() > 0) {
                        String content = new String(java.nio.file.Files.readAllBytes(gitignore.toPath()));
                        if (!content.endsWith("\n")) {
                            writer.write("\n");
                        }
                    }
                    writer.write(".skidfuscator\n");
                }
                project.getLogger().lifecycle("Added .skidfuscator to .gitignore");
            }
        } catch (IOException e) {
            project.getLogger().warn("Failed to update .gitignore: " + e.getMessage());
        }
    }
}
