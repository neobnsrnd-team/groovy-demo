package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import springware.groovydemo.dto.DemoProduct;
import springware.groovydemo.service.BulkInsertService;
import springware.groovydemo.service.BulkInsertService.BulkInsertResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("BulkInsertService Tests")
class BulkInsertServiceTest {

    @Autowired
    private BulkInsertService bulkInsertService;

    @BeforeEach
    void setUp() {
        bulkInsertService.clearAll();
    }

    @Test
    @DisplayName("Sample CSV 파일 읽기 및 Bulk Insert")
    void testBulkInsertFromSampleCsv() throws IOException {
        ClassPathResource resource = new ClassPathResource("data/sample-products.csv");

        BulkInsertResult result = bulkInsertService.bulkInsertFromCsv(resource.getInputStream());

        System.out.println("\n[Sample CSV Bulk Insert Result]");
        System.out.println(result);

        assertEquals(10, result.totalInserted());
        assertEquals(10, bulkInsertService.getProductCount());
    }

    @Test
    @DisplayName("10,000건 Bulk Insert - Batch Size 1000")
    void testBulkInsert_10000Records_BatchSize1000() {
        List<DemoProduct> products = generateProducts(10_000);

        BulkInsertResult result = bulkInsertService.bulkInsert(products, 1000);

        printResult("10,000 Records (Batch: 1000)", result);

        assertEquals(10_000, result.totalInserted());
        assertEquals(10, result.batchCount());
        assertEquals(10_000, bulkInsertService.getProductCount());
    }

    @Test
    @DisplayName("50,000건 Bulk Insert - Batch Size 1000")
    void testBulkInsert_50000Records_BatchSize1000() {
        List<DemoProduct> products = generateProducts(50_000);

        BulkInsertResult result = bulkInsertService.bulkInsert(products, 1000);

        printResult("50,000 Records (Batch: 1000)", result);

        assertEquals(50_000, result.totalInserted());
        assertEquals(50, result.batchCount());
        assertEquals(50_000, bulkInsertService.getProductCount());
    }

    @Test
    @DisplayName("Batch Size 비교 테스트 (100 vs 500 vs 1000 vs 5000)")
    void testBulkInsert_BatchSizeComparison() {
        int totalRecords = 20_000;
        int[] batchSizes = {100, 500, 1000, 5000};

        System.out.println("\n" + "=".repeat(70));
        System.out.println("        Batch Size Performance Comparison");
        System.out.println("        Total Records: " + String.format("%,d", totalRecords));
        System.out.println("=".repeat(70));
        System.out.printf("\n%-15s %15s %15s %20s%n", "Batch Size", "Duration (ms)", "Batches", "Records/sec");
        System.out.println("-".repeat(70));

        for (int batchSize : batchSizes) {
            bulkInsertService.clearAll();
            List<DemoProduct> products = generateProducts(totalRecords);

            BulkInsertResult result = bulkInsertService.bulkInsert(products, batchSize);

            System.out.printf("%-15d %15d %15d %20.1f%n",
                batchSize, result.durationMs(), result.batchCount(), result.recordsPerSecond());

            assertEquals(totalRecords, result.totalInserted());
        }

        System.out.println("=".repeat(70) + "\n");
    }

    @Test
    @DisplayName("대용량 CSV 파일 시뮬레이션 (100,000건)")
    void testBulkInsert_LargeCsvSimulation() throws IOException {
        int totalRecords = 100_000;
        String csvData = generateCsvData(totalRecords);
        InputStream inputStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Large CSV File Simulation");
        System.out.println("=".repeat(60));
        System.out.println("  - Total Records: " + String.format("%,d", totalRecords));
        System.out.println("  - Batch Size: 1,000");

        long startTime = System.currentTimeMillis();
        BulkInsertResult result = bulkInsertService.bulkInsertFromCsv(inputStream, 1000);
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("\n[ Results ]");
        System.out.printf("  - Records Inserted: %,d%n", result.totalInserted());
        System.out.printf("  - Batch Count: %d%n", result.batchCount());
        System.out.printf("  - Insert Duration: %,d ms%n", result.durationMs());
        System.out.printf("  - Total Duration (incl. parsing): %,d ms%n", totalTime);
        System.out.printf("  - Insert Speed: %,.1f records/sec%n", result.recordsPerSecond());
        System.out.println("=".repeat(60) + "\n");

        assertEquals(totalRecords, result.totalInserted());
        assertEquals(totalRecords, bulkInsertService.getProductCount());
    }

    @Test
    @DisplayName("단건 Insert vs Batch Insert 성능 비교")
    void testSingleVsBatchInsertComparison() {
        int totalRecords = 5_000;

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Single Insert vs Batch Insert Comparison");
        System.out.println("        Total Records: " + String.format("%,d", totalRecords));
        System.out.println("=".repeat(60));

        // Single insert (batch size = 1)
        bulkInsertService.clearAll();
        List<DemoProduct> products1 = generateProducts(totalRecords);
        BulkInsertResult singleResult = bulkInsertService.bulkInsert(products1, 1);

        // Batch insert (batch size = 1000)
        bulkInsertService.clearAll();
        List<DemoProduct> products2 = generateProducts(totalRecords);
        BulkInsertResult batchResult = bulkInsertService.bulkInsert(products2, 1000);

        double speedup = (double) singleResult.durationMs() / batchResult.durationMs();

        System.out.println("\n[ Single Insert (Batch Size: 1) ]");
        System.out.printf("  - Duration: %,d ms%n", singleResult.durationMs());
        System.out.printf("  - Speed: %,.1f records/sec%n", singleResult.recordsPerSecond());

        System.out.println("\n[ Batch Insert (Batch Size: 1000) ]");
        System.out.printf("  - Duration: %,d ms%n", batchResult.durationMs());
        System.out.printf("  - Speed: %,.1f records/sec%n", batchResult.recordsPerSecond());

        System.out.println("\n[ Comparison ]");
        System.out.printf("  - Speedup: %.1fx faster%n", speedup);
        System.out.printf("  - Time Saved: %,d ms%n", singleResult.durationMs() - batchResult.durationMs());
        System.out.println("=".repeat(60) + "\n");

        assertTrue(batchResult.durationMs() < singleResult.durationMs(),
            "Batch insert should be faster than single insert");
    }

    private List<DemoProduct> generateProducts(int count) {
        List<DemoProduct> products = new ArrayList<>(count);
        String[] categories = {"Electronics", "Furniture", "Accessories", "Lighting", "Office"};
        Random random = new Random(42);

        for (int i = 1; i <= count; i++) {
            DemoProduct product = new DemoProduct(
                "PROD-" + String.format("%06d", i),
                "Product Name " + i,
                BigDecimal.valueOf(10 + random.nextDouble() * 990).setScale(2, java.math.RoundingMode.HALF_UP),
                random.nextInt(500) + 1,
                categories[random.nextInt(categories.length)]
            );
            products.add(product);
        }

        return products;
    }

    private String generateCsvData(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("code,name,price,quantity,category\n");

        String[] categories = {"Electronics", "Furniture", "Accessories", "Lighting", "Office"};
        Random random = new Random(42);

        for (int i = 1; i <= count; i++) {
            sb.append(String.format("PROD-%06d,Product Name %d,%.2f,%d,%s%n",
                i,
                i,
                10 + random.nextDouble() * 990,
                random.nextInt(500) + 1,
                categories[random.nextInt(categories.length)]
            ));
        }

        return sb.toString();
    }

    private void printResult(String testName, BulkInsertResult result) {
        System.out.println("\n[" + testName + "]");
        System.out.println(result);
    }
}
