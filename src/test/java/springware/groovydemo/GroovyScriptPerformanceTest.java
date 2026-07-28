package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.service.GroovyScriptExecutor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DisplayName("Groovy Script Performance Tests")
class GroovyScriptPerformanceTest {

    @Autowired
    private GroovyScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor.clearAllCache();
    }

    @Test
    @DisplayName("Cache 사용 vs 미사용 성능 비교")
    void testPerformance_CachedVsUncached() {
        int iterations = 100;
        String script = """
            def amount = input.get("amount") as BigDecimal
            def rate = input.get("rate") as BigDecimal
            def result = amount * rate
            for (int i = 0; i < 100; i++) {
                result = result + (amount * 0.01)
            }
            return result
            """;

        ScriptInput input = new ScriptInput()
                .put("amount", new BigDecimal("1000"))
                .put("rate", new BigDecimal("0.05"));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Groovy Script Performance Comparison");
        System.out.println("=".repeat(60));

        // ===== Test 1: Without Cache (compile every time) =====
        long[] withoutCacheTimes = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            executor.clearAllCache(); // Force recompilation
            long start = System.nanoTime();
            executor.execute("perf-test-" + i, script, input);
            withoutCacheTimes[i] = System.nanoTime() - start;
        }

        // ===== Test 2: With Cache (compile once, reuse) =====
        executor.clearAllCache();
        // First execution to populate cache
        executor.execute("perf-test-cached", script, input);

        long[] withCacheTimes = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            executor.execute("perf-test-cached", script, input);
            withCacheTimes[i] = System.nanoTime() - start;
        }

        // Calculate and print statistics
        printResults(iterations, withoutCacheTimes, withCacheTimes);

        // Assertions
        double avgWithoutCache = calculateAverage(withoutCacheTimes);
        double avgWithCache = calculateAverage(withCacheTimes);
        double speedup = avgWithoutCache / avgWithCache;

        assertTrue(avgWithCache < avgWithoutCache,
                "Cached execution should be faster than uncached execution");
        assertTrue(speedup > 1.0,
                "Speedup should be greater than 1x");
    }

    @Test
    @DisplayName("다양한 스크립트 복잡도별 성능 비교")
    void testPerformance_ByScriptComplexity() {
        int iterations = 50;

        String simpleScript = "return 1 + 2";

        String mediumScript = """
            def a = input.get("a") as Integer
            def b = input.get("b") as Integer
            def sum = 0
            for (int i = a; i <= b; i++) {
                sum += i
            }
            return sum
            """;

        String complexScript = """
            def items = input.get("items") as List
            def discount = input.get("discount") as BigDecimal

            def total = items.stream()
                .map { it as BigDecimal }
                .reduce(BigDecimal.ZERO, { a, b -> a.add(b) })

            def discountAmount = total.multiply(discount)
            def finalTotal = total.subtract(discountAmount)

            output.put("subtotal", total)
            output.put("discount", discountAmount)
            output.put("total", finalTotal)

            return finalTotal
            """;

        ScriptInput simpleInput = new ScriptInput();
        ScriptInput mediumInput = new ScriptInput().put("a", 1).put("b", 100);
        ScriptInput complexInput = new ScriptInput()
                .put("items", java.util.List.of(
                        new BigDecimal("100"),
                        new BigDecimal("200"),
                        new BigDecimal("300")))
                .put("discount", new BigDecimal("0.1"));

        System.out.println("\n" + "=".repeat(70));
        System.out.println("        Performance by Script Complexity (Cache ON)");
        System.out.println("=".repeat(70));

        // Warm up cache
        executor.execute("simple", simpleScript, simpleInput);
        executor.execute("medium", mediumScript, mediumInput);
        executor.execute("complex", complexScript, complexInput);

        // Measure cached performance
        long[] simpleTimes = measureCachedPerformance("simple", simpleScript, simpleInput, iterations);
        long[] mediumTimes = measureCachedPerformance("medium", mediumScript, mediumInput, iterations);
        long[] complexTimes = measureCachedPerformance("complex", complexScript, complexInput, iterations);

        System.out.printf("\n%-15s %15s %15s %15s%n", "Complexity", "Avg (ms)", "Min (ms)", "Max (ms)");
        System.out.println("-".repeat(70));
        printRow("Simple", simpleTimes);
        printRow("Medium", mediumTimes);
        printRow("Complex", complexTimes);
        System.out.println("=".repeat(70) + "\n");
    }

    @Test
    @DisplayName("동시 실행 시뮬레이션 성능 테스트")
    void testPerformance_MultipleScripts() {
        int scriptCount = 10;
        int executionsPerScript = 20;

        String scriptTemplate = """
            def value = input.get("value") as Integer
            def result = value * %d
            for (int i = 0; i < 50; i++) {
                result += i
            }
            return result
            """;

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Multiple Scripts Performance Test");
        System.out.println("=".repeat(60));
        System.out.printf("\n  - Scripts: %d%n", scriptCount);
        System.out.printf("  - Executions per script: %d%n", executionsPerScript);
        System.out.printf("  - Total executions: %d%n", scriptCount * executionsPerScript);

        // First round: All compilations (cold cache)
        executor.clearAllCache();
        long coldStart = System.nanoTime();
        for (int s = 0; s < scriptCount; s++) {
            String script = String.format(scriptTemplate, s + 1);
            ScriptInput input = new ScriptInput().put("value", 10);
            for (int e = 0; e < executionsPerScript; e++) {
                executor.execute("script-" + s, script, input);
            }
        }
        long coldDuration = System.nanoTime() - coldStart;

        // Second round: All cached (warm cache)
        long warmStart = System.nanoTime();
        for (int s = 0; s < scriptCount; s++) {
            String script = String.format(scriptTemplate, s + 1);
            ScriptInput input = new ScriptInput().put("value", 10);
            for (int e = 0; e < executionsPerScript; e++) {
                executor.execute("script-" + s, script, input);
            }
        }
        long warmDuration = System.nanoTime() - warmStart;

        double coldMs = coldDuration / 1_000_000.0;
        double warmMs = warmDuration / 1_000_000.0;
        double speedup = coldMs / warmMs;

        System.out.println("\n[ Results ]");
        System.out.printf("  - Cold Cache (first round):  %,.2f ms%n", coldMs);
        System.out.printf("  - Warm Cache (second round): %,.2f ms%n", warmMs);
        System.out.printf("  - Speed Up: %,.1fx faster%n", speedup);
        System.out.println("=".repeat(60) + "\n");

        assertTrue(warmDuration < coldDuration);
    }

    private long[] measureCachedPerformance(String name, String script, ScriptInput input, int iterations) {
        long[] times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            executor.execute(name, script, input);
            times[i] = System.nanoTime() - start;
        }
        return times;
    }

    private void printResults(int iterations, long[] withoutCacheTimes, long[] withCacheTimes) {
        double avgWithoutCache = calculateAverage(withoutCacheTimes) / 1_000_000.0;
        double avgWithCache = calculateAverage(withCacheTimes) / 1_000_000.0;
        long minWithoutCache = findMin(withoutCacheTimes) / 1_000_000;
        long minWithCache = findMin(withCacheTimes) / 1_000_000;
        long maxWithoutCache = findMax(withoutCacheTimes) / 1_000_000;
        long maxWithCache = findMax(withCacheTimes) / 1_000_000;
        double totalWithoutCache = calculateSum(withoutCacheTimes) / 1_000_000.0;
        double totalWithCache = calculateSum(withCacheTimes) / 1_000_000.0;

        double improvement = ((avgWithoutCache - avgWithCache) / avgWithoutCache) * 100;
        double speedup = avgWithoutCache / avgWithCache;

        System.out.println("\n[ Test Configuration ]");
        System.out.println("  - Iterations: " + iterations);
        System.out.println("  - Script: Business calculation with loop");

        System.out.println("\n[ Results: WITHOUT Cache (Compile Every Time) ]");
        System.out.printf("  - Total Time:   %,.2f ms%n", totalWithoutCache);
        System.out.printf("  - Average Time: %,.3f ms%n", avgWithoutCache);
        System.out.printf("  - Min Time:     %,d ms%n", minWithoutCache);
        System.out.printf("  - Max Time:     %,d ms%n", maxWithoutCache);

        System.out.println("\n[ Results: WITH Cache (Compile Once, Reuse) ]");
        System.out.printf("  - Total Time:   %,.2f ms%n", totalWithCache);
        System.out.printf("  - Average Time: %,.3f ms%n", avgWithCache);
        System.out.printf("  - Min Time:     %,d ms%n", minWithCache);
        System.out.printf("  - Max Time:     %,d ms%n", maxWithCache);

        System.out.println("\n[ Performance Comparison ]");
        System.out.printf("  - Performance Improvement: %,.1f%%%n", improvement);
        System.out.printf("  - Speed Up: %,.1fx faster%n", speedup);
        System.out.printf("  - Time Saved per Execution: %,.3f ms%n", avgWithoutCache - avgWithCache);
        System.out.printf("  - Total Time Saved (%d runs): %,.2f ms%n", iterations, totalWithoutCache - totalWithCache);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  Conclusion: Cache provides " + String.format("%.1fx", speedup) + " performance improvement!");
        System.out.println("=".repeat(60) + "\n");
    }

    private void printRow(String label, long[] times) {
        double avg = calculateAverage(times) / 1_000_000.0;
        long min = findMin(times) / 1_000_000;
        long max = findMax(times) / 1_000_000;
        System.out.printf("%-15s %15.3f %15d %15d%n", label, avg, min, max);
    }

    private double calculateAverage(long[] times) {
        return (double) calculateSum(times) / times.length;
    }

    private long calculateSum(long[] times) {
        long sum = 0;
        for (long time : times) {
            sum += time;
        }
        return sum;
    }

    private long findMin(long[] times) {
        long min = Long.MAX_VALUE;
        for (long time : times) {
            if (time < min) min = time;
        }
        return min;
    }

    private long findMax(long[] times) {
        long max = Long.MIN_VALUE;
        for (long time : times) {
            if (time > max) max = time;
        }
        return max;
    }
}
