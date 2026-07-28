package springware.groovydemo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import springware.groovydemo.dto.Product;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class BulkInsertService {

    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final String INSERT_SQL =
        "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public BulkInsertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Read CSV file and bulk insert with default batch size (1000)
     */
    public BulkInsertResult bulkInsertFromCsv(InputStream inputStream) throws IOException {
        return bulkInsertFromCsv(inputStream, DEFAULT_BATCH_SIZE);
    }

    /**
     * Read CSV file and bulk insert with custom batch size
     */
    @Transactional
    public BulkInsertResult bulkInsertFromCsv(InputStream inputStream, int batchSize) throws IOException {
        List<Product> products = parseCsv(inputStream);
        return bulkInsert(products, batchSize);
    }

    /**
     * Bulk insert products with batch processing
     */
    @Transactional
    public BulkInsertResult bulkInsert(List<Product> products, int batchSize) {
        long startTime = System.currentTimeMillis();
        int totalInserted = 0;
        int batchCount = 0;

        List<Product> batch = new ArrayList<>(batchSize);

        for (Product product : products) {
            batch.add(product);

            if (batch.size() >= batchSize) {
                insertBatch(batch);
                totalInserted += batch.size();
                batchCount++;
                batch.clear();
            }
        }

        // Insert remaining records
        if (!batch.isEmpty()) {
            insertBatch(batch);
            totalInserted += batch.size();
            batchCount++;
        }

        long duration = System.currentTimeMillis() - startTime;

        return new BulkInsertResult(totalInserted, batchCount, batchSize, duration);
    }

    /**
     * Insert a batch of products using JdbcTemplate batchUpdate
     */
    private void insertBatch(List<Product> batch) {
        jdbcTemplate.batchUpdate(INSERT_SQL, batch, batch.size(),
            (ps, product) -> {
                ps.setString(1, product.getCode());
                ps.setString(2, product.getName());
                ps.setBigDecimal(3, product.getPrice());
                ps.setInt(4, product.getQuantity());
                ps.setString(5, product.getCategory());
            });
    }

    /**
     * Parse CSV file to Product list
     * Expected format: code,name,price,quantity,category
     */
    private List<Product> parseCsv(InputStream inputStream) throws IOException {
        List<Product> products = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split(",");
                if (fields.length >= 5) {
                    Product product = new Product(
                        fields[0].trim(),                          // code
                        fields[1].trim(),                          // name
                        new BigDecimal(fields[2].trim()),          // price
                        Integer.parseInt(fields[3].trim()),        // quantity
                        fields[4].trim()                           // category
                    );
                    products.add(product);
                }
            }
        }

        return products;
    }

    /**
     * Get total count of products in database
     */
    public long getProductCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product", Long.class);
        return count != null ? count : 0;
    }

    /**
     * Clear all products from database
     */
    @Transactional
    public void clearAll() {
        jdbcTemplate.update("DELETE FROM product");
    }

    /**
     * Result of bulk insert operation
     */
    public record BulkInsertResult(
        int totalInserted,
        int batchCount,
        int batchSize,
        long durationMs
    ) {
        public double recordsPerSecond() {
            return durationMs > 0 ? (totalInserted * 1000.0) / durationMs : 0;
        }

        @Override
        public String toString() {
            return String.format(
                "BulkInsertResult{inserted=%d, batches=%d, batchSize=%d, duration=%dms, speed=%.1f records/sec}",
                totalInserted, batchCount, batchSize, durationMs, recordsPerSecond()
            );
        }
    }
}
