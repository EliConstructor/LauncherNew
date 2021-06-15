/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.skcraft.launcher.model.minecraft.JavaVersion;
import com.skcraft.launcher.util.Environment;
import com.skcraft.launcher.util.EnvironmentParser;
import com.skcraft.launcher.util.Platform;
import com.skcraft.launcher.util.WinRegistry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Finds the best Java runtime to use.
 */
public final class JavaRuntimeFinder {

    private JavaRuntimeFinder() {
    }

    public static List<JavaRuntime> getAvailableRuntimes() {
        Environment env = Environment.getInstance();
        List<JavaRuntime> entries = new ArrayList<>();
        File launcherDir;

        if (env.getPlatform() == Platform.WINDOWS) {
            try {
                String launcherPath = WinRegistry.readString(WinRegistry.HKEY_CURRENT_USER,
                        "SOFTWARE\\Mojang\\InstalledProducts\\Minecraft Launcher", "InstallLocation");

                launcherDir = new File(launcherPath);
            } catch (Throwable ignored) {
                launcherDir = new File(System.getenv("APPDATA"), ".minecraft");
            }

            try {
                getEntriesFromRegistry(entries, "SOFTWARE\\JavaSoft\\Java Runtime Environment");
                getEntriesFromRegistry(entries, "SOFTWARE\\JavaSoft\\Java Development Kit");
            } catch (Throwable ignored) {
            }
            Collections.sort(entries);
        } else if (env.getPlatform() == Platform.LINUX) {
            launcherDir = new File(System.getenv("HOME"), ".minecraft");
        } else {
            return Collections.emptyList();
        }

        if (!launcherDir.isDirectory()) {
            return entries;
        }

        File runtimes = new File(launcherDir, "runtime");
        for (File potential : Objects.requireNonNull(runtimes.listFiles())) {
            if (potential.getName().startsWith("jre-x")) {
                boolean is64Bit = potential.getName().equals("jre-x64");

                entries.add(new JavaRuntime(potential.getAbsoluteFile(), readVersionFromRelease(potential), is64Bit));
            } else {
                String runtimeName = potential.getName();

                String[] children = potential.list();
                if (children == null || children.length == 0) continue;
                String platformName = children[0];

                String[] parts = platformName.split("-");
                if (parts.length < 2) continue;

                String arch = parts[1];
                boolean is64Bit = arch.equals("x64");

                File javaDir = new File(potential, String.format("%s/%s", platformName, runtimeName));

                entries.add(new JavaRuntime(javaDir.getAbsoluteFile(), readVersionFromRelease(javaDir), is64Bit));
            }
        }

        return entries;
    }

    /**
     * Return the path to the best found JVM location.
     *
     * @return the JVM location, or null
     */
    public static File findBestJavaPath() {
        List<JavaRuntime> entries = getAvailableRuntimes();
        if (entries.size() > 0) {
            return new File(entries.get(0).getDir(), "bin");
        }
        
        return null;
    }

    public static Optional<JavaRuntime> findBestJavaRuntime(JavaVersion targetVersion) {
        List<JavaRuntime> entries = getAvailableRuntimes();

        return entries.stream().sorted()
                .filter(runtime -> runtime.getMajorVersion() == targetVersion.getMajorVersion())
                .findFirst();
    }

    public static JavaRuntime getRuntimeFromPath(String path) {
        File target = new File(path);

        return new JavaRuntime(target, readVersionFromRelease(target), guessIf64Bit(target));
    }
    
    private static void getEntriesFromRegistry(List<JavaRuntime> entries, String basePath)
            throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        List<String> subKeys = WinRegistry.readStringSubKeys(WinRegistry.HKEY_LOCAL_MACHINE, basePath);
        for (String subKey : subKeys) {
            JavaRuntime entry = getEntryFromRegistry(basePath, subKey);
            if (entry != null) {
                entries.add(entry);
            }
        }
    }
    
    private static JavaRuntime getEntryFromRegistry(String basePath, String version)  throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        String regPath = basePath + "\\" + version;
        String path = WinRegistry.readString(WinRegistry.HKEY_LOCAL_MACHINE, regPath, "JavaHome");
        File dir = new File(path);
        if (dir.exists() && new File(dir, "bin/java.exe").exists()) {
            return new JavaRuntime(dir, version, guessIf64Bit(dir));
        } else {
            return null;
        }
    }
    
    private static boolean guessIf64Bit(File path) {
        try {
            String programFilesX86 = System.getenv("ProgramFiles(x86)");
            return programFilesX86 == null || !path.getCanonicalPath().startsWith(new File(programFilesX86).getCanonicalPath());
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String readVersionFromRelease(File javaPath) {
        File releaseFile = new File(javaPath, "release");
        if (releaseFile.exists()) {
            try {
                Map<String, String> releaseDetails = EnvironmentParser.parse(releaseFile);

                return releaseDetails.get("JAVA_VERSION");
            } catch (IOException ignored) {
            }
        }

        return null;
    }
}
