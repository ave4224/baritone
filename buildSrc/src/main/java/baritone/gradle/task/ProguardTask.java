/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.gradle.task;

import baritone.gradle.util.Determinizer;
import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Pair;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Brady
 * @since 10/11/2018
 */
public class ProguardTask extends BaritoneGradleTask {

    private static final Pattern TEMP_LIBRARY_PATTERN = Pattern.compile("-libraryjars 'tempLibraries\\/([a-zA-Z0-9/_\\-\\.]+)\\.jar'");

    @Input
    private String url;

    @Input
    private String extract;

    @Input
    private String versionManifest;

    private Map<String, String> versionDownloadMap;
    private List<String> requiredLibraries;

    @TaskAction
    protected void exec() throws Exception {
        super.verifyArtifacts();

        // "Haha brady why don't you make separate tasks"
        processArtifact();
        downloadProguard();
        extractProguard();
        generateConfigs();
        downloadVersionManifest();
        acquireDependencies();
        proguardApi();
        proguardStandalone();
        cleanup();
    }

    private void processArtifact() throws Exception {
        if (Files.exists(this.artifactUnoptimizedPath)) {
            Files.delete(this.artifactUnoptimizedPath);
        }

        Determinizer.determinize(this.artifactPath.toString(), this.artifactUnoptimizedPath.toString());
    }

    private void downloadProguard() throws Exception {
        Path proguardZip = getTemporaryFile(PROGUARD_ZIP);
        if (!Files.exists(proguardZip)) {
            write(new URL(this.url).openStream(), proguardZip);
        }
    }

    private void extractProguard() throws Exception {
        Path proguardJar = getTemporaryFile(PROGUARD_JAR);
        if (!Files.exists(proguardJar)) {
            ZipFile zipFile = new ZipFile(getTemporaryFile(PROGUARD_ZIP).toFile());
            ZipEntry zipJarEntry = zipFile.getEntry(this.extract);
            write(zipFile.getInputStream(zipJarEntry), proguardJar);
            zipFile.close();
        }
    }

    private void generateConfigs() throws Exception {
        Files.copy(getRelativeFile(PROGUARD_CONFIG_TEMPLATE), getTemporaryFile(PROGUARD_CONFIG_DEST), REPLACE_EXISTING);

        // Setup the template that will be used to derive the API and Standalone configs
        List<String> template = Files.readAllLines(getTemporaryFile(PROGUARD_CONFIG_DEST));
        template.add(0, "-injars " + this.artifactPath.toString());
        template.add(1, "-outjars " + this.getTemporaryFile(PROGUARD_EXPORT_PATH));

        // Acquire the RT jar using "java -verbose". This doesn't work on Java 9+
        Process p = new ProcessBuilder("java", "-verbose").start();
        String out = IOUtils.toString(p.getInputStream(), "UTF-8").split("\n")[0].split("Opened ")[1].replace("]", "");
        template.add(2, "-libraryjars '" + out + "'");

        // API config doesn't require any changes from the changes that we made to the template
        Files.write(getTemporaryFile(PROGUARD_API_CONFIG), template);

        // For the Standalone config, don't keep the API package
        List<String> standalone = new ArrayList<>(template);
        standalone.removeIf(s -> s.contains("# this is the keep api"));
        Files.write(getTemporaryFile(PROGUARD_STANDALONE_CONFIG), standalone);

        // Discover all of the libraries that we will need to acquire from gradle
        this.requiredLibraries = new ArrayList<>();
        template.forEach(line -> {
            if (!line.startsWith("#")) {
                Matcher m = TEMP_LIBRARY_PATTERN.matcher(line);
                if (m.find()) {
                    this.requiredLibraries.add(m.group(1));
                }
            }
        });
    }

    private void downloadVersionManifest() throws Exception {
        Path manifestJson = getTemporaryFile(VERSION_MANIFEST);
        write(new URL(this.versionManifest).openStream(), manifestJson);

        // Place all the versions in the map with their download URL
        this.versionDownloadMap = new HashMap<>();
        JsonObject json = readJson(Files.readAllLines(manifestJson)).getAsJsonObject();
        JsonArray versions = json.getAsJsonArray("versions");
        versions.forEach(element -> {
            JsonObject object = element.getAsJsonObject();
            this.versionDownloadMap.put(object.get("id").getAsString(), object.get("url").getAsString());
        });
    }

    private void acquireDependencies() throws Exception {

        // Create a map of all of the dependencies that we are able to access in this project
        // Likely a better way to do this, I just pair the dependency with the first valid configuration
        Map<String, Pair<Configuration, Dependency>> dependencyLookupMap = new HashMap<>();
        getProject().getConfigurations().stream().filter(Configuration::isCanBeResolved).forEach(config ->
                config.getAllDependencies().forEach(dependency ->
                        dependencyLookupMap.putIfAbsent(dependency.getName() + "-" + dependency.getVersion(), Pair.of(config, dependency))));

        // Create the directory if it doesn't already exist
        Path tempLibraries = getTemporaryFile(TEMP_LIBRARY_DIR);
        if (!Files.exists(tempLibraries)) {
            Files.createDirectory(tempLibraries);
        }

        // Iterate the required libraries to copy them to tempLibraries
        for (String lib : this.requiredLibraries) {
            // Download the version jar from the URL acquired from the version manifest
            if (lib.startsWith("minecraft")) {
                String version = lib.split("-")[1];
                Path versionJar = getTemporaryFile("tempLibraries/" + lib + ".jar");
                if (!Files.exists(versionJar)) {
                    JsonObject versionJson = PARSER.parse(new InputStreamReader(new URL(this.versionDownloadMap.get(version)).openStream())).getAsJsonObject();
                    String url = versionJson.getAsJsonObject("downloads").getAsJsonObject("client").getAsJsonPrimitive("url").getAsString();
                    write(new URL(url).openStream(), versionJar);
                }
                continue;
            }

            // Find a configuration/dependency pair that matches the desired library
            Pair<Configuration, Dependency> pair = null;
            for (Map.Entry<String, Pair<Configuration, Dependency>> entry : dependencyLookupMap.entrySet()) {
                if (entry.getKey().startsWith(lib)) {
                    pair = entry.getValue();
                }
            }

            // The pair must be non-null
            Objects.requireNonNull(pair);

            // Find the library jar file, and copy it to tempLibraries
            for (File file : pair.getLeft().files(pair.getRight())) {
                if (file.getName().startsWith(lib)) {
                    Files.copy(file.toPath(), getTemporaryFile("tempLibraries/" + lib + ".jar"), REPLACE_EXISTING);
                }
            }
        }
    }

    private void proguardApi() throws Exception {
        runProguard(getTemporaryFile(PROGUARD_API_CONFIG));
        Determinizer.determinize(this.proguardOut.toString(), this.artifactApiPath.toString());
    }

    private void proguardStandalone() throws Exception {
        runProguard(getTemporaryFile(PROGUARD_STANDALONE_CONFIG));
        Determinizer.determinize(this.proguardOut.toString(), this.artifactStandalonePath.toString());
    }

    private void cleanup() {
        try {
            Files.delete(this.proguardOut);
        } catch (IOException ignored) {}
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setExtract(String extract) {
        this.extract = extract;
    }

    public void setVersionManifest(String versionManifest) {
        this.versionManifest = versionManifest;
    }

    private void runProguard(Path config) throws Exception {
        // Delete the existing proguard output file. Proguard probably handles this already, but why not do it ourselves
        if (Files.exists(this.proguardOut)) {
            Files.delete(this.proguardOut);
        }

        Path proguardJar = getTemporaryFile(PROGUARD_JAR);
        Process p = new ProcessBuilder("java", "-jar", proguardJar.toString(), "@" + config.toString())
                .directory(getTemporaryFile("").toFile()) // Set the working directory to the temporary folder]
                .start();

        // We can't do output inherit process I/O with gradle for some reason and have it work, so we have to do this
        this.printOutputLog(p.getInputStream());
        this.printOutputLog(p.getErrorStream());

        // Halt the current thread until the process is complete, if the exit code isn't 0, throw an exception
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("Proguard exited with code " + exitCode);
        }
    }

    private void printOutputLog(InputStream stream) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
