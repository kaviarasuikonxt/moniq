 package com.moniq.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;


public class q {
        public static void main(String args[]) {
        // Get the current project working directory
        Path projectRoot = Paths.get(System.getProperty("user.dir"));

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(path -> {
                     System.out.println("File: " + path.getFileName());
                     System.out.println("Path: " + path.toAbsolutePath());
                     System.out.println("-----------------------------------");
                 });
        } catch (IOException e) {
            e.printStackTrace();
        }}
    }