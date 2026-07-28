package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;
import springware.groovydemo.service.GroovyScriptExecutor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GroovyScriptExecutorTest {

    @Autowired
    private GroovyScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor.clearAllCache();
    }

    @Test
    void testSimpleScript() {
        String script = "return 1 + 2";
        ScriptInput input = new ScriptInput();

        ScriptOutput output = executor.execute("simple-add", script, input);

        assertTrue(output.isSuccess());
        assertEquals(3, output.getResult());
    }

    @Test
    void testScriptWithInputParameters() {
        String script = """
            def a = input.get("a") as Integer
            def b = input.get("b") as Integer
            return a * b
            """;

        ScriptInput input = new ScriptInput()
                .put("a", 5)
                .put("b", 7);

        ScriptOutput output = executor.execute("multiply", script, input);

        assertTrue(output.isSuccess());
        assertEquals(35, output.getResult());
    }

    @Test
    void testScriptWithOutputData() {
        String script = """
            def name = input.get("name")
            def age = input.get("age") as Integer

            output.put("greeting", "Hello, " + name)
            output.put("birthYear", 2024 - age)
            output.setResult("Processed: " + name)

            return output.getResult()
            """;

        ScriptInput input = new ScriptInput()
                .put("name", "John")
                .put("age", 30);

        ScriptOutput output = executor.execute("greeting", script, input);

        assertTrue(output.isSuccess());
        assertEquals("Hello, John", output.getData().get("greeting"));
        assertEquals(1994, output.getData().get("birthYear"));
    }

    @Test
    void testScriptCaching() {
        String script = "return System.currentTimeMillis()";

        // First execution - compiles and caches
        assertFalse(executor.isCached("cache-test"));
        executor.execute("cache-test", script, new ScriptInput());
        assertTrue(executor.isCached("cache-test"));
        assertEquals(1, executor.getCacheSize());

        // Execute again - should use cached version
        executor.execute("cache-test", script, new ScriptInput());
        assertEquals(1, executor.getCacheSize());
    }

    @Test
    void testClearCache() {
        String script = "return 42";

        executor.execute("test1", script, new ScriptInput());
        executor.execute("test2", script, new ScriptInput());
        assertEquals(2, executor.getCacheSize());

        executor.clearCache("test1");
        assertEquals(1, executor.getCacheSize());
        assertFalse(executor.isCached("test1"));
        assertTrue(executor.isCached("test2"));

        executor.clearAllCache();
        assertEquals(0, executor.getCacheSize());
    }

    @Test
    void testCalculateDiscountScript() throws IOException {
        String script = loadScript("scripts/calculate-discount.groovy");

        ScriptInput input = new ScriptInput()
                .put("amount", new BigDecimal("1000"))
                .put("customerType", "VIP");

        ScriptOutput output = executor.execute("calculate-discount", script, input);

        assertTrue(output.isSuccess());
        assertEquals(new BigDecimal("800.00"), output.getData().get("finalAmount"));
        assertEquals(new BigDecimal("0.20"), output.getData().get("discountRate"));
        assertEquals(new BigDecimal("200.00"), output.getData().get("discountAmount"));
    }

    @Test
    void testValidateOrderScript_Valid() throws IOException {
        String script = loadScript("scripts/validate-order.groovy");

        ScriptInput input = new ScriptInput()
                .put("orderId", "ORD-001")
                .put("items", List.of("item1", "item2"))
                .put("totalAmount", new BigDecimal("100"));

        ScriptOutput output = executor.execute("validate-order", script, input);

        assertTrue(output.isSuccess());
        assertTrue((Boolean) output.getData().get("isValid"));
        assertTrue(((List<?>) output.getData().get("errors")).isEmpty());
    }

    @Test
    void testValidateOrderScript_Invalid() throws IOException {
        String script = loadScript("scripts/validate-order.groovy");

        ScriptInput input = new ScriptInput()
                .put("orderId", null)
                .put("items", List.of())
                .put("totalAmount", new BigDecimal("-10"));

        ScriptOutput output = executor.execute("validate-order-invalid", script, input);

        assertTrue(output.isSuccess());
        assertFalse((Boolean) output.getData().get("isValid"));

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) output.getData().get("errors");
        assertEquals(3, errors.size());
    }

    @Test
    void testScriptError() {
        String script = "throw new RuntimeException('Test error')";

        ScriptOutput output = executor.execute("error-script", script, new ScriptInput());

        assertFalse(output.isSuccess());
        assertNotNull(output.getErrorMessage());
    }

    @Test
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
            executor.execute("perf-test", script, input);
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

        // Calculate statistics
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

        // Assertions
        assertTrue(avgWithCache < avgWithoutCache,
                "Cached execution should be faster than uncached execution");
        assertTrue(speedup > 1.0,
                "Speedup should be greater than 1x");
    }

    private double calculateAverage(long[] times) {
        long sum = 0;
        for (long time : times) {
            sum += time;
        }
        return (double) sum / times.length;
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

    private String loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
