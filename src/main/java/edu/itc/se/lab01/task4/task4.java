package edu.itc.se.lab01.task4;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class task4 {

    // 1. CPU-bound part: Callable generator for Prime Counting
    static Callable<Long> PrimeCounter(long from, long to) {
        return () -> {
            long count = 0;
            for (long n = Math.max(from, 2); n < to; n++) {
                boolean prime = true;
                for (long d = 2; d * d <= n; d++) {
                    if (n % d == 0) {
                        prime = false;
                        break;
                    }
                }
                if (prime) count++;
            }
            return count;
        };
    }

    // 2. I/O-bound part: Callable generator for Fake Download
    static Callable<String> FakeDownload(String url) {
        return () -> {
            // Extract a task ID from the URL to determine failure/timeout behaviors
            int taskId = Integer.parseInt(url.substring(url.lastIndexOf('-') + 1));
            
            // 3. Failure handling: Every 50th task throws
            if (taskId % 50 == 0) {
                throw new IOException("boom");
            }
            
            // 4. Timeouts: One task sleeps for 5 seconds
            if (taskId == 499) {
                Thread.sleep(5000); 
            } else {
                Thread.sleep(200); // simulated network
            }
            
            return url + " -> " + url.length();
        };
    }

    static long countPrimesSingleThreaded(long from, long to) {
        try {
            return PrimeCounter(from, to).call();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        
        // --- 1. CPU-BOUND PART ---
        final long LIMIT = 4_000_000, CHUNKS = 16;
        long t0 = System.nanoTime();
        long single = countPrimesSingleThreaded(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);

        t0 = System.nanoTime();
        // 5. Shutdown: Using Java 19+ try-with-resources for executors
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;
            
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunkSize;
                long to = (i == CHUNKS - 1) ? LIMIT : from + chunkSize;
                futures.add(pool.submit(PrimeCounter(from, to)));
            }

            long total = 0;
            for (Future<Long> future : futures) {
                total += future.get();
            }
            System.out.printf("pool:            %d primes in %d ms%n", total, (System.nanoTime() - t0) / 1_000_000);
        }

        // --- 2. I/O-BOUND PART ---
        System.out.println("\n--- I/O Tasks: Platform Threads (Pool of 20) ---");
        runIoTasks(Executors.newFixedThreadPool(20));

        System.out.println("\n--- I/O Tasks: Virtual Threads ---");
        runIoTasks(Executors.newVirtualThreadPerTaskExecutor());

        System.out.println("\n--- 6. Bonus: CompletableFuture (Virtual Threads) ---");
        runCompletableFutureTasks();
        
        // JVM will exit cleanly because we used try-with-resources for all Executors
    }

    static void runIoTasks(ExecutorService pool) {
        long t0 = System.nanoTime();
        int ok = 0, failed = 0, timedOut = 0;
        
        try (pool) { // Auto-shutdown via try-with-resources
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 1; i <= 500; i++) {
                futures.add(pool.submit(FakeDownload("http://test.com/file-" + i)));
            }

            for (Future<String> future : futures) {
                try {
                    future.get(1, TimeUnit.SECONDS);
                    ok++;
                } catch (TimeoutException e) {
                    timedOut++;
                    future.cancel(true);
                } catch (ExecutionException e) {
                    failed++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("Result: ok=%d failed=%d timeout=%d in %d ms%n", ok, failed, timedOut, elapsed);
    }

    // 6. Bonus
    static void runCompletableFutureTasks() {
        long t0 = System.nanoTime();
        int ok = 0, failed = 0, timedOut = 0;
        
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> cfs = new ArrayList<>();
            
            for (int i = 1; i <= 500; i++) {
                final String url = "http://test.com/file-" + i;
                CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                    try {
                        return FakeDownload(url).call();
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, pool)
                .orTimeout(1, TimeUnit.SECONDS)
                .handle((res, ex) -> {
                    if (ex != null) {
                        if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
                            return "TIMEOUT";
                        }
                        return "FAILED";
                    }
                    return res;
                });
                cfs.add(cf);
            }

            // allOf wait
            CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<String> cf : cfs) {
                String res = cf.join();
                if ("TIMEOUT".equals(res)) timedOut++;
                else if ("FAILED".equals(res)) failed++;
                else ok++;
            }
            
            long elapsed = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("Result: ok=%d failed=%d timeout=%d in %d ms%n", ok, failed, timedOut, elapsed);
        }
    }
}