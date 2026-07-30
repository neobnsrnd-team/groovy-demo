package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;
import springware.groovydemo.service.ExecutionOptions;
import springware.groovydemo.service.GroovyScriptExecutor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Groovy + DB Integration Tests")
class GroovyDbIntegrationTest {

    @Autowired
    private GroovyScriptExecutor executor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ExecutionOptions DB_OPTIONS = ExecutionOptions.builder().allowDb(true).build();

    @BeforeEach
    void setUp() {
        executor.clearAllCache();
        jdbcTemplate.update("DELETE FROM product");
        insertSampleData();
    }

    private void insertSampleData() {
        jdbcTemplate.update(
            "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
            "ELEC-001", "Wireless Mouse", new BigDecimal("29.99"), 100, "Electronics"
        );
        jdbcTemplate.update(
            "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
            "ELEC-002", "Mechanical Keyboard", new BigDecimal("89.99"), 50, "Electronics"
        );
        jdbcTemplate.update(
            "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
            "FURN-001", "Office Chair", new BigDecimal("199.99"), 30, "Furniture"
        );
        jdbcTemplate.update(
            "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
            "FURN-002", "Standing Desk", new BigDecimal("399.99"), 20, "Furniture"
        );
        jdbcTemplate.update(
            "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
            "ACC-001", "Monitor Stand", new BigDecimal("45.00"), 80, "Accessories"
        );
    }

    @Test
    @DisplayName("Groovy 스크립트로 상품 목록 조회")
    void testSelectProducts() throws IOException {
        String script = loadScript("scripts/db-select-products.groovy");

        // 전체 조회
        ScriptInput input1 = new ScriptInput().put("limit", 10);
        ScriptOutput output1 = executor.execute("select-all", script, input1, DB_OPTIONS);

        assertTrue(output1.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allProducts = (List<Map<String, Object>>) output1.getResult();
        assertEquals(5, allProducts.size());

        System.out.println("\n[전체 상품 조회]");
        System.out.printf("%-12s %-25s %10s %10s %-15s%n", "Code", "Name", "Price", "Qty", "Category");
        System.out.println("-".repeat(75));
        for (Map<String, Object> p : allProducts) {
            System.out.printf("%-12s %-25s %10s %10s %-15s%n",
                p.get("CODE"), p.get("NAME"), p.get("PRICE"), p.get("QUANTITY"), p.get("CATEGORY"));
        }

        // 카테고리별 조회
        ScriptInput input2 = new ScriptInput()
            .put("category", "Electronics")
            .put("limit", 10);
        ScriptOutput output2 = executor.execute("select-electronics", script, input2, DB_OPTIONS);

        assertTrue(output2.isSuccess());
        assertEquals(2, output2.getData().get("count"));
    }

    @Test
    @DisplayName("Groovy 스크립트로 신규 상품 등록")
    void testInsertProduct() throws IOException {
        String script = loadScript("scripts/db-insert-product.groovy");

        ScriptInput input = new ScriptInput()
            .put("code", "NEW-001")
            .put("name", "New Product")
            .put("price", new BigDecimal("59.99"))
            .put("quantity", 100)
            .put("category", "Electronics");

        ScriptOutput output = executor.execute("insert-product", script, input, DB_OPTIONS);

        assertTrue(output.isSuccess());
        assertTrue((Boolean) output.getData().get("success"));
        assertEquals(1, output.getData().get("insertedRows"));

        System.out.println("\n[신규 상품 등록]");
        System.out.println("Product: " + output.getData().get("product"));

        // DB 확인
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product WHERE code = ?", Long.class, "NEW-001");
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("Groovy 스크립트로 중복 상품 등록 시 에러")
    void testInsertDuplicateProduct() throws IOException {
        String script = loadScript("scripts/db-insert-product.groovy");

        ScriptInput input = new ScriptInput()
            .put("code", "ELEC-001")  // 이미 존재하는 코드
            .put("name", "Duplicate Product")
            .put("price", new BigDecimal("99.99"))
            .put("quantity", 50)
            .put("category", "Electronics");

        ScriptOutput output = executor.execute("insert-duplicate", script, input, DB_OPTIONS);

        assertTrue(output.isSuccess());  // 스크립트 실행은 성공
        assertFalse((Boolean) output.getData().get("success"));  // 비즈니스 로직 실패

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) output.getData().get("errors");
        assertTrue(errors.get(0).contains("already exists"));

        System.out.println("\n[중복 상품 등록 시도]");
        System.out.println("Errors: " + errors);
    }

    @Test
    @DisplayName("Groovy 스크립트로 재고 증가/감소")
    void testUpdateStock() throws IOException {
        String script = loadScript("scripts/db-update-stock.groovy");

        // 재고 증가
        ScriptInput increaseInput = new ScriptInput()
            .put("code", "ELEC-001")
            .put("adjustment", 50);

        ScriptOutput increaseOutput = executor.execute("stock-increase", script, increaseInput, DB_OPTIONS);

        assertTrue(increaseOutput.isSuccess());
        assertEquals(100, increaseOutput.getData().get("previousQuantity"));
        assertEquals(150, increaseOutput.getData().get("newQuantity"));

        System.out.println("\n[재고 증가]");
        System.out.println("Code: " + increaseOutput.getData().get("code"));
        System.out.println("Previous: " + increaseOutput.getData().get("previousQuantity"));
        System.out.println("Adjustment: +" + increaseOutput.getData().get("adjustment"));
        System.out.println("New: " + increaseOutput.getData().get("newQuantity"));

        // 재고 감소
        ScriptInput decreaseInput = new ScriptInput()
            .put("code", "ELEC-001")
            .put("adjustment", -30);

        ScriptOutput decreaseOutput = executor.execute("stock-decrease", script, decreaseInput, DB_OPTIONS);

        assertTrue(decreaseOutput.isSuccess());
        assertEquals(150, decreaseOutput.getData().get("previousQuantity"));
        assertEquals(120, decreaseOutput.getData().get("newQuantity"));

        System.out.println("\n[재고 감소]");
        System.out.println("Previous: " + decreaseOutput.getData().get("previousQuantity"));
        System.out.println("Adjustment: " + decreaseOutput.getData().get("adjustment"));
        System.out.println("New: " + decreaseOutput.getData().get("newQuantity"));
    }

    @Test
    @DisplayName("Groovy 스크립트로 재고 부족 에러 처리")
    void testInsufficientStock() throws IOException {
        String script = loadScript("scripts/db-update-stock.groovy");

        ScriptInput input = new ScriptInput()
            .put("code", "ELEC-001")
            .put("adjustment", -200);  // 현재 재고 100보다 많이 감소

        ScriptOutput output = executor.execute("stock-insufficient", script, input, DB_OPTIONS);

        assertTrue(output.isSuccess());
        assertFalse((Boolean) output.getData().get("success"));
        assertTrue(output.getData().get("error").toString().contains("Insufficient stock"));

        System.out.println("\n[재고 부족 에러]");
        System.out.println("Error: " + output.getData().get("error"));
        System.out.println("Current Quantity: " + output.getData().get("currentQuantity"));
    }

    @Test
    @DisplayName("Groovy 스크립트로 카테고리별 재고 가치 계산")
    void testCalculateInventoryValue() throws IOException {
        String script = loadScript("scripts/db-calculate-inventory-value.groovy");

        // 전체 카테고리
        ScriptInput input = new ScriptInput();
        ScriptOutput output = executor.execute("inventory-value", script, input, DB_OPTIONS);

        assertTrue(output.isSuccess());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stats = (List<Map<String, Object>>) output.getData().get("categoryStats");

        System.out.println("\n[카테고리별 재고 가치]");
        System.out.printf("%-15s %10s %12s %15s %12s%n",
            "Category", "Products", "Total Qty", "Total Value", "Avg Price");
        System.out.println("-".repeat(70));

        for (Map<String, Object> stat : stats) {
            System.out.printf("%-15s %10s %12s %15s %12s%n",
                stat.get("CATEGORY"),
                stat.get("PRODUCT_COUNT"),
                stat.get("TOTAL_QUANTITY"),
                stat.get("TOTAL_VALUE"),
                String.format("%.2f", ((Number) stat.get("AVG_PRICE")).doubleValue())
            );
        }

        System.out.println("-".repeat(70));
        System.out.printf("Grand Total: %s%n", output.getData().get("grandTotal"));
    }

    @Test
    @DisplayName("Groovy 스크립트로 카테고리별 일괄 가격 조정")
    void testBulkPriceUpdate() throws IOException {
        String script = loadScript("scripts/db-bulk-price-update.groovy");

        // Electronics 카테고리 10% 인상
        ScriptInput input = new ScriptInput()
            .put("category", "Electronics")
            .put("adjustmentPercent", new BigDecimal("10"));

        ScriptOutput output = executor.execute("bulk-price-update", script, input, DB_OPTIONS);

        assertTrue(output.isSuccess());
        assertTrue((Boolean) output.getData().get("success"));
        assertEquals(2, output.getData().get("updatedCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) output.getData().get("changes");

        System.out.println("\n[일괄 가격 조정: Electronics +10%]");
        System.out.printf("%-12s %-25s %12s %12s %10s%n",
            "Code", "Name", "Before", "After", "Change");
        System.out.println("-".repeat(75));

        for (Map<String, Object> change : changes) {
            System.out.printf("%-12s %-25s %12s %12s %10s%n",
                change.get("code"),
                change.get("name"),
                change.get("priceBefore"),
                change.get("priceAfter"),
                "+" + change.get("change")
            );
        }
    }

    @Test
    @DisplayName("Groovy 스크립트 캐싱 + DB 연동 성능 테스트")
    void testCachingWithDbOperations() throws IOException {
        String script = loadScript("scripts/db-select-products.groovy");
        int iterations = 100;

        // Warm up cache
        executor.execute("perf-select", script, new ScriptInput().put("limit", 5), DB_OPTIONS);

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ScriptInput input = new ScriptInput()
                .put("category", "Electronics")
                .put("limit", 10);
            executor.execute("perf-select", script, input, DB_OPTIONS);
        }
        long duration = System.nanoTime() - start;

        double avgMs = (duration / 1_000_000.0) / iterations;

        System.out.println("\n[Groovy + DB 성능 테스트]");
        System.out.printf("  - Iterations: %d%n", iterations);
        System.out.printf("  - Total Time: %.2f ms%n", duration / 1_000_000.0);
        System.out.printf("  - Average Time: %.3f ms%n", avgMs);
        System.out.printf("  - Throughput: %.1f queries/sec%n", 1000.0 / avgMs);
    }

    private String loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
