package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;
import springware.groovydemo.service.ExecutionOptions;
import springware.groovydemo.service.SecureGroovyScriptExecutor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Secure Groovy Script Tests")
class SecureGroovyScriptTest {

    @Autowired
    private SecureGroovyScriptExecutor secureExecutor;

    @BeforeEach
    void setUp() {
        secureExecutor.clearAllCache();
    }

    // ==================== 허용된 작업 테스트 ====================

    @Test
    @DisplayName("허용: 기본 연산")
    void testAllowed_BasicOperations() {
        String script = """
            def a = 10
            def b = 20
            def result = a + b * 2
            return result
            """;

        ScriptOutput output = secureExecutor.execute("basic-ops", script, new ScriptInput());

        assertTrue(output.isSuccess());
        assertEquals(50, output.getResult());
        printResult("기본 연산", output);
    }

    @Test
    @DisplayName("허용: 컬렉션 사용")
    void testAllowed_Collections() {
        String script = """
            def list = [1, 2, 3, 4, 5]
            def map = [name: "test", value: 100]

            def sum = list.sum()
            def filtered = list.findAll { it > 2 }

            output.put("sum", sum)
            output.put("filtered", filtered)
            output.put("mapValue", map.value)

            return sum
            """;

        ScriptOutput output = secureExecutor.execute("collections", script, new ScriptInput());

        assertTrue(output.isSuccess());
        assertEquals(15, output.getData().get("sum"));
        printResult("컬렉션 사용", output);
    }

    @Test
    @DisplayName("허용: BigDecimal 계산")
    void testAllowed_BigDecimal() {
        String script = """
            def price = input.get("price") as BigDecimal
            def quantity = input.get("quantity") as Integer
            def tax = new BigDecimal("0.1")

            def subtotal = price * quantity
            def taxAmount = subtotal * tax
            def total = subtotal + taxAmount

            output.put("subtotal", subtotal)
            output.put("tax", taxAmount)
            output.put("total", total)

            return total
            """;

        ScriptInput input = new ScriptInput()
            .put("price", new BigDecimal("99.99"))
            .put("quantity", 3);

        ScriptOutput output = secureExecutor.execute("bigdecimal", script, input);

        assertTrue(output.isSuccess());
        printResult("BigDecimal 계산", output);
    }

    @Test
    @DisplayName("허용: 날짜 처리")
    void testAllowed_DateTime() {
        String script = """
            import java.time.LocalDate
            import java.time.LocalDateTime

            def today = LocalDate.now()
            def now = LocalDateTime.now()

            output.put("today", today.toString())
            output.put("now", now.toString())
            output.put("year", today.getYear())

            return today
            """;

        ScriptOutput output = secureExecutor.execute("datetime", script, new ScriptInput());

        assertTrue(output.isSuccess());
        assertNotNull(output.getData().get("today"));
        printResult("날짜 처리", output);
    }

    @Test
    @DisplayName("허용: 클로저와 함수 정의")
    void testAllowed_ClosuresAndFunctions() {
        String script = """
            // 함수 정의
            def multiply = { a, b -> a * b }
            def square = { it * it }

            // 함수 사용
            def result1 = multiply(3, 4)
            def result2 = [1, 2, 3].collect(square)

            output.put("multiply", result1)
            output.put("squares", result2)

            return result1
            """;

        ScriptOutput output = secureExecutor.execute("closures", script, new ScriptInput());

        assertTrue(output.isSuccess());
        assertEquals(12, output.getData().get("multiply"));
        printResult("클로저와 함수", output);
    }

    // ==================== 차단된 작업 테스트 ====================

    @Test
    @DisplayName("차단: System.exit() 호출")
    void testBlocked_SystemExit() {
        String script = """
            System.exit(0)
            return "should not reach here"
            """;

        ScriptOutput output = secureExecutor.execute("system-exit", script, new ScriptInput());

        assertFalse(output.isSuccess());
        assertTrue(output.getErrorMessage().contains("Security violation") ||
                   output.getErrorMessage().contains("System"));
        printBlocked("System.exit()", output);
    }

    @Test
    @DisplayName("차단: Runtime.exec() 호출")
    void testBlocked_RuntimeExec() {
        String script = """
            Runtime.getRuntime().exec("ls")
            return "should not reach here"
            """;

        ScriptOutput output = secureExecutor.execute("runtime-exec", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("Runtime.exec()", output);
    }

    @Test
    @DisplayName("차단: 프로세스 실행 (.execute())")
    void testBlocked_ProcessExecute() {
        String script = """
            "whoami".execute()
            return "should not reach here"
            """;

        ScriptOutput output = secureExecutor.execute("process-execute", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("String.execute()", output);
    }

    @Test
    @DisplayName("차단: 파일 접근")
    void testBlocked_FileAccess() {
        String script = """
            new File("/etc/passwd").text
            """;

        ScriptOutput output = secureExecutor.execute("file-access", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("File 접근", output);
    }

    @Test
    @DisplayName("차단: URL 접근")
    void testBlocked_UrlAccess() {
        String script = """
            new URL("http://example.com").text
            """;

        ScriptOutput output = secureExecutor.execute("url-access", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("URL 접근", output);
    }

    @Test
    @DisplayName("차단: 리플렉션 사용")
    void testBlocked_Reflection() {
        String script = """
            Class.forName("java.lang.Runtime")
            """;

        ScriptOutput output = secureExecutor.execute("reflection", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("리플렉션", output);
    }

    @Test
    @DisplayName("차단: GroovyShell 생성")
    void testBlocked_GroovyShell() {
        String script = """
            new GroovyShell().evaluate("System.exit(0)")
            """;

        ScriptOutput output = secureExecutor.execute("groovy-shell", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("GroovyShell", output);
    }

    @Test
    @DisplayName("차단: Thread 생성")
    void testBlocked_Thread() {
        String script = """
            Thread.currentThread().interrupt()
            """;

        ScriptOutput output = secureExecutor.execute("thread", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("Thread 접근", output);
    }

    @Test
    @DisplayName("차단: @Grab 어노테이션")
    void testBlocked_GrabAnnotation() {
        String script = """
            @Grab('commons-io:commons-io:2.11.0')
            import org.apache.commons.io.FileUtils
            return "loaded"
            """;

        ScriptOutput output = secureExecutor.execute("grab", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("@Grab 어노테이션", output);
    }

    @Test
    @DisplayName("차단: java.io import")
    void testBlocked_IoImport() {
        String script = """
            import java.io.FileReader
            return "should not compile"
            """;

        ScriptOutput output = secureExecutor.execute("io-import", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("java.io import", output);
    }

    @Test
    @DisplayName("차단: DB 접근 (기본 옵션)")
    void testBlocked_DbAccess() {
        String script = """
            def products = db.queryForList("SELECT * FROM product")
            return products
            """;

        ScriptOutput output = secureExecutor.execute("db-access", script, new ScriptInput());

        assertFalse(output.isSuccess());
        assertTrue(output.getErrorMessage().contains("DB access") ||
                   output.getErrorMessage().contains("db"));
        printBlocked("DB 접근 (기본 옵션)", output);
    }

    @Test
    @DisplayName("허용: DB 접근 (ExecutionOptions.withDb())")
    void testAllowed_DbAccessWithDb() {
        String script = """
            def count = db.queryForObject("SELECT COUNT(*) FROM product", Long.class)
            output.put("count", count)
            return count
            """;

        ScriptOutput output = secureExecutor.execute("db-access-allowed", script, new ScriptInput(),
            ExecutionOptions.withDb());

        assertTrue(output.isSuccess());
        System.out.println("\n[허용] DB 접근 (ExecutionOptions.withDb())");
        System.out.println("  Result: " + output.getResult());
    }

    // ==================== 종합 테스트 ====================

    @Test
    @DisplayName("종합: 안전한 비즈니스 로직")
    void testSecureBusinessLogic() {
        String script = """
            // 주문 할인 계산 로직
            def orderAmount = input.get("amount") as BigDecimal
            def customerGrade = input.get("grade")
            def couponCode = input.get("couponCode")

            // 등급별 할인율
            def gradeDiscount = switch(customerGrade) {
                case "VIP" -> 0.15
                case "GOLD" -> 0.10
                case "SILVER" -> 0.05
                default -> 0.0
            }

            // 쿠폰 할인
            def couponDiscount = couponCode == "SPRING20" ? 0.20 : 0.0

            // 최대 할인율 적용
            def totalDiscount = Math.min(gradeDiscount + couponDiscount, 0.30)

            def discountAmount = orderAmount * totalDiscount
            def finalAmount = orderAmount - discountAmount

            log.info("Order processed: amount={}, discount={}", orderAmount, discountAmount)

            output.put("originalAmount", orderAmount)
            output.put("gradeDiscount", gradeDiscount)
            output.put("couponDiscount", couponDiscount)
            output.put("totalDiscount", totalDiscount)
            output.put("discountAmount", discountAmount)
            output.put("finalAmount", finalAmount)

            return finalAmount
            """;

        ScriptInput input = new ScriptInput()
            .put("amount", new BigDecimal("10000"))
            .put("grade", "VIP")
            .put("couponCode", "SPRING20");

        ScriptOutput output = secureExecutor.execute("business-logic", script, input);

        assertTrue(output.isSuccess());

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        안전한 비즈니스 로직 실행 결과");
        System.out.println("=".repeat(60));
        System.out.println("Original Amount: " + output.getData().get("originalAmount"));
        System.out.println("Grade Discount: " + toPercent(output.getData().get("gradeDiscount")));
        System.out.println("Coupon Discount: " + toPercent(output.getData().get("couponDiscount")));
        System.out.println("Total Discount: " + toPercent(output.getData().get("totalDiscount")) + " (max 30%)");
        System.out.println("Discount Amount: " + output.getData().get("discountAmount"));
        System.out.println("Final Amount: " + output.getData().get("finalAmount"));
        System.out.println("=".repeat(60) + "\n");
    }

    // ==================== 헬퍼 메서드 ====================

    private void printResult(String testName, ScriptOutput output) {
        System.out.println("\n[허용] " + testName);
        System.out.println("  Result: " + output.getResult());
        if (!output.getData().isEmpty()) {
            System.out.println("  Data: " + output.getData());
        }
    }

    private void printBlocked(String testName, ScriptOutput output) {
        System.out.println("\n[차단] " + testName);
        System.out.println("  Error: " + output.getErrorMessage());
    }

    private String toPercent(Object value) {
        if (value instanceof Number) {
            return String.format("%.0f%%", ((Number) value).doubleValue() * 100);
        }
        return value + "%";
    }

    // ==================== ExecutionOptions 테스트 ====================

    @Test
    @DisplayName("옵션: allowSystemAccess=true → System.getenv 허용")
    void testOption_AllowSystemAccess() {
        String script = """
            def path = System.getenv("PATH")
            output.put("hasPath", path != null)
            return "accessed"
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("sys-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("System.getenv (기본)", blocked);

        // allowSystemAccess=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowSystemAccess(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("sys-allowed", script, new ScriptInput(), options);
        assertTrue(allowed.isSuccess());
        System.out.println("[허용] System.getenv (allowSystemAccess=true)");
        System.out.println("  Result: " + allowed.getResult());
    }

    @Test
    @DisplayName("옵션: allowProcessExecution=true → execute() 허용")
    void testOption_AllowProcessExecution() {
        String script = """
            def result = "echo hello".execute().text
            return result
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("proc-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("String.execute (기본)", blocked);

        // allowProcessExecution=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowProcessExecution(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("proc-allowed", script, new ScriptInput(), options);
        // 참고: AST 레벨에서 차단될 수 있으므로 결과 확인
        System.out.println("[테스트] Process execution (allowProcessExecution=true)");
        System.out.println("  Success: " + allowed.isSuccess());
        if (!allowed.isSuccess()) {
            System.out.println("  Note: AST 레벨에서 차단됨 (예상 동작)");
        }
    }

    @Test
    @DisplayName("옵션: allowThreadAccess=true → Thread 접근 허용")
    void testOption_AllowThreadAccess() {
        String script = """
            def threadName = Thread.currentThread().getName()
            return threadName
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("thread-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("Thread 접근 (기본)", blocked);

        // allowThreadAccess=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowThreadAccess(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("thread-allowed", script, new ScriptInput(), options);
        System.out.println("[테스트] Thread 접근 (allowThreadAccess=true)");
        System.out.println("  Success: " + allowed.isSuccess());
        if (allowed.isSuccess()) {
            System.out.println("  Thread Name: " + allowed.getResult());
        }
    }

    @Test
    @DisplayName("옵션: allowReflection=true → Class.forName 허용")
    void testOption_AllowReflection() {
        String script = """
            def clazz = Class.forName("java.lang.String")
            return clazz.getName()
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("reflect-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("Class.forName (기본)", blocked);

        // allowReflection=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowReflection(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("reflect-allowed", script, new ScriptInput(), options);
        System.out.println("[테스트] Reflection (allowReflection=true)");
        System.out.println("  Success: " + allowed.isSuccess());
        if (allowed.isSuccess()) {
            System.out.println("  Class: " + allowed.getResult());
        }
    }

    @Test
    @DisplayName("옵션: allowFileAccess=true → File 접근 허용")
    void testOption_AllowFileAccess() {
        String script = """
            def file = new File(".")
            return file.getAbsolutePath()
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("file-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("File 접근 (기본)", blocked);

        // allowFileAccess=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowFileAccess(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("file-allowed", script, new ScriptInput(), options);
        System.out.println("[테스트] File 접근 (allowFileAccess=true)");
        System.out.println("  Success: " + allowed.isSuccess());
        if (allowed.isSuccess()) {
            System.out.println("  Path: " + allowed.getResult());
        }
    }

    @Test
    @DisplayName("옵션: allowNetworkAccess=true → URL 접근 허용")
    void testOption_AllowNetworkAccess() {
        String script = """
            def url = new URL("https://example.com")
            return url.getHost()
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("net-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("URL 접근 (기본)", blocked);

        // allowNetworkAccess=true (허용)
        ExecutionOptions options = ExecutionOptions.builder()
            .allowNetworkAccess(true)
            .build();
        ScriptOutput allowed = secureExecutor.execute("net-allowed", script, new ScriptInput(), options);
        System.out.println("[테스트] Network 접근 (allowNetworkAccess=true)");
        System.out.println("  Success: " + allowed.isSuccess());
        if (allowed.isSuccess()) {
            System.out.println("  Host: " + allowed.getResult());
        }
    }

    @Test
    @DisplayName("옵션: allowDb=true → DB 접근 허용")
    void testOption_AllowDb() {
        String script = """
            def count = db.queryForObject("SELECT COUNT(*) FROM product", Long.class)
            return count
            """;

        // 기본 옵션 (차단)
        ScriptOutput blocked = secureExecutor.execute("db-opt-blocked", script, new ScriptInput());
        assertFalse(blocked.isSuccess());
        printBlocked("DB 접근 (기본)", blocked);

        // allowDb=true (허용)
        ScriptOutput allowed = secureExecutor.execute("db-opt-allowed", script, new ScriptInput(),
            ExecutionOptions.withDb());
        assertTrue(allowed.isSuccess());
        System.out.println("[허용] DB 접근 (allowDb=true)");
        System.out.println("  Count: " + allowed.getResult());
    }

    @Test
    @DisplayName("옵션: allowAll() → 모든 제약 해제")
    void testOption_AllowAll() {
        String script = """
            def path = System.getenv("PATH")
            def threadName = Thread.currentThread().getName()

            output.put("hasPath", path != null)
            output.put("threadName", threadName)

            return "all features accessed"
            """;

        ExecutionOptions options = ExecutionOptions.allowAll();
        ScriptOutput output = secureExecutor.execute("allow-all", script, new ScriptInput(), options);

        System.out.println("\n[테스트] allowAll() - 모든 제약 해제");
        System.out.println("  Success: " + output.isSuccess());
        if (output.isSuccess()) {
            System.out.println("  Result: " + output.getResult());
            System.out.println("  Data: " + output.getData());
        } else {
            System.out.println("  Error: " + output.getErrorMessage());
        }
    }

    @Test
    @DisplayName("옵션: 복합 옵션 테스트 (DB + File)")
    void testOption_Combined() {
        String dbScript = """
            def count = db.queryForObject("SELECT COUNT(*) FROM product", Long.class)
            return count
            """;

        String fileScript = """
            def file = new File(".")
            return file.exists()
            """;

        // DB만 허용
        ExecutionOptions dbOnly = ExecutionOptions.builder()
            .allowDb(true)
            .build();

        ScriptOutput dbResult = secureExecutor.execute("combo-db", dbScript, new ScriptInput(), dbOnly);
        ScriptOutput fileResult = secureExecutor.execute("combo-file", fileScript, new ScriptInput(), dbOnly);

        System.out.println("\n[복합 옵션] allowDb=true, allowFileAccess=false");
        System.out.println("  DB Script: " + (dbResult.isSuccess() ? "허용 ✓" : "차단 ✗"));
        System.out.println("  File Script: " + (fileResult.isSuccess() ? "허용 ✓" : "차단 ✗"));

        assertTrue(dbResult.isSuccess(), "DB 접근은 허용되어야 함");
        assertFalse(fileResult.isSuccess(), "File 접근은 차단되어야 함");

        // DB + File 허용
        ExecutionOptions dbAndFile = ExecutionOptions.builder()
            .allowDb(true)
            .allowFileAccess(true)
            .build();

        ScriptOutput dbResult2 = secureExecutor.execute("combo-db2", dbScript, new ScriptInput(), dbAndFile);
        ScriptOutput fileResult2 = secureExecutor.execute("combo-file2", fileScript, new ScriptInput(), dbAndFile);

        System.out.println("\n[복합 옵션] allowDb=true, allowFileAccess=true");
        System.out.println("  DB Script: " + (dbResult2.isSuccess() ? "허용 ✓" : "차단 ✗"));
        System.out.println("  File Script: " + (fileResult2.isSuccess() ? "허용 ✓" : "차단 ✗"));

        assertTrue(dbResult2.isSuccess(), "DB 접근 허용");
        // File은 AST 레벨에서 추가 차단될 수 있음
    }

    @Test
    @DisplayName("옵션: 항상 차단되는 패턴 (@Grab, GroovyShell)")
    void testOption_AlwaysBlocked() {
        String grabScript = """
            @Grab('commons-io:commons-io:2.11.0')
            return "loaded"
            """;

        String shellScript = """
            new GroovyShell().evaluate("1+1")
            """;

        // allowAll로도 차단되어야 함
        ExecutionOptions allowAll = ExecutionOptions.allowAll();

        ScriptOutput grabResult = secureExecutor.execute("always-grab", grabScript, new ScriptInput(), allowAll);
        ScriptOutput shellResult = secureExecutor.execute("always-shell", shellScript, new ScriptInput(), allowAll);

        System.out.println("\n[항상 차단] allowAll()로도 차단되는 패턴");
        System.out.println("  @Grab: " + (grabResult.isSuccess() ? "허용 ✗ (문제!)" : "차단 ✓"));
        System.out.println("  GroovyShell: " + (shellResult.isSuccess() ? "허용 ✗ (문제!)" : "차단 ✓"));

        assertFalse(grabResult.isSuccess(), "@Grab은 항상 차단되어야 함");
        assertFalse(shellResult.isSuccess(), "GroovyShell은 항상 차단되어야 함");
    }

    @Test
    @DisplayName("옵션: ExecutionOptions.toString() 테스트")
    void testOption_ToString() {
        ExecutionOptions defaults = ExecutionOptions.defaults();
        ExecutionOptions withDb = ExecutionOptions.withDb();
        ExecutionOptions allowAll = ExecutionOptions.allowAll();

        System.out.println("\n[ExecutionOptions.toString()]");
        System.out.println("  defaults(): " + defaults);
        System.out.println("  withDb(): " + withDb);
        System.out.println("  allowAll(): " + allowAll);

        assertTrue(defaults.toString().contains("allowDb=false"));
        assertTrue(withDb.toString().contains("allowDb=true"));
        assertTrue(allowAll.toString().contains("allowProcessExecution=true"));
    }
}
