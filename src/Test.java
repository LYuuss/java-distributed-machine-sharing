/**
 * 
 *  @author Lyes Djemaa
 *  @version 1.0
 * Our class Test, 
 *  it will work as follow
 * <p> 
 *  - Initialize a registry, by default on port 1099
 *  - Read a file line by line
 *      Note that all machine must be listed before any executor otherwise the system will probably not work
 *  - each non-empty line describes ONE JVM to launch
 *  - then, launch all those JVMs with ProcessBuilder
 *  - let them run for T seconds, then stop them
 *
 * </p>
 * Usage:
 *   java Test <config-file> <duration-seconds> [registry-port]
 *
 * Example of execution :
 *   java Test config.txt 60
 */

import main.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.rmi.registry.LocateRegistry;



public class Test {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Test <config-file> <duration-seconds> [registry-port]");
            System.exit(1);
        }

        String configPath = args[0];
        long durationSeconds = Long.parseLong(args[1]);
        int registryPort = (args.length >= 3) ? Integer.parseInt(args[2]) : 1099;

        // 1) Start RMI registry in this JVM
        try {
            LocateRegistry.createRegistry(registryPort);
            System.out.println("[Test] RMI registry started on port " + registryPort);
        } catch (Exception e) {
            System.err.println("[Test] Could not create registry on port " + registryPort +
                               " (maybe already running): " + e);
        }

        // 2) Read config file and split commands into Machines and Executors
        List<List<String>> machineCommands = new ArrayList<>();
        List<List<String>> executorCommands = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(configPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                List<String> tokens = splitCommandLine(line);
                if (tokens.isEmpty()) continue;

                // Allow lines starting with "java main.Machine ..." or just "main.Machine ..."
                if ("java".equals(tokens.get(0))) {
                    tokens.remove(0);
                    if (tokens.isEmpty()) continue;
                }

                String mainClass = tokens.get(0);

                if ("main.Machine".equals(mainClass)) {
                    machineCommands.add(tokens);
                } else if ("main.Executor".equals(mainClass)) {
                    executorCommands.add(tokens);
                } else {
                    System.err.println("[Test] Warning: unknown main class in config line: " + mainClass);
                    // you can ignore or treat as generic; for now we ignore others
                }
            }
        } catch (IOException e) {
            System.err.println("[Test] Error reading config file '" + configPath + "': " + e);
            e.printStackTrace();
            System.exit(1);
        }

        List<Process> children = new ArrayList<>();

        // 3) Launch all Machines first
        try {
            for (List<String> tokens : machineCommands) {
                List<String> command = new ArrayList<>();
                command.add("java");
                command.addAll(tokens);

                System.out.println("[Test] Starting machine: " + command);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO(); // share stdout/stderr with parent
                Process p = pb.start();
                children.add(p);
            }
        } catch (IOException e) {
            System.err.println("[Test] Error starting machine processes: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        // Small delay to let machines export themselves in the registry
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 4) Launch all Executors
        try {
            for (List<String> tokens : executorCommands) {
                List<String> command = new ArrayList<>();
                command.add("java");
                command.addAll(tokens);

                System.out.println("[Test] Starting executor: " + command);
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.inheritIO();
                Process p = pb.start();
                children.add(p);
            }
        } catch (IOException e) {
            System.err.println("[Test] Error starting executor processes: " + e);
            e.printStackTrace();
            System.exit(1);
        }

        // 5) Let everything run for T seconds
        System.out.println("[Test] All processes started, sleeping for " + durationSeconds + " seconds...");
        try {
            TimeUnit.SECONDS.sleep(durationSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 6) Terminate children
        System.out.println("[Test] Time is up, terminating child processes...");
        for (Process p : children) {
            p.destroy();
        }
    }

    /**
     * Simple command-line splitter:
     *   - splits on whitespace
     *   - text inside "double quotes" is kept as a single token
     *   - quotes are removed
     */
    private static List<String> splitCommandLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); ++i) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }
}
