package springware.groovydemo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;
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

        ScriptOutput output = secureExecutor.executeSecure("basic-ops", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("collections", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("bigdecimal", script, input);

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

        ScriptOutput output = secureExecutor.executeSecure("datetime", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("closures", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("system-exit", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("runtime-exec", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("process-execute", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("String.execute()", output);
    }

    @Test
    @DisplayName("차단: 파일 접근")
    void testBlocked_FileAccess() {
        String script = """
            new File("/etc/passwd").text
            """;

        ScriptOutput output = secureExecutor.executeSecure("file-access", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("File 접근", output);
    }

    @Test
    @DisplayName("차단: URL 접근")
    void testBlocked_UrlAccess() {
        String script = """
            new URL("http://example.com").text
            """;

        ScriptOutput output = secureExecutor.executeSecure("url-access", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("URL 접근", output);
    }

    @Test
    @DisplayName("차단: 리플렉션 사용")
    void testBlocked_Reflection() {
        String script = """
            Class.forName("java.lang.Runtime")
            """;

        ScriptOutput output = secureExecutor.executeSecure("reflection", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("리플렉션", output);
    }

    @Test
    @DisplayName("차단: GroovyShell 생성")
    void testBlocked_GroovyShell() {
        String script = """
            new GroovyShell().evaluate("System.exit(0)")
            """;

        ScriptOutput output = secureExecutor.executeSecure("groovy-shell", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("GroovyShell", output);
    }

    @Test
    @DisplayName("차단: Thread 생성")
    void testBlocked_Thread() {
        String script = """
            Thread.currentThread().interrupt()
            """;

        ScriptOutput output = secureExecutor.executeSecure("thread", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("grab", script, new ScriptInput());

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

        ScriptOutput output = secureExecutor.executeSecure("io-import", script, new ScriptInput());

        assertFalse(output.isSuccess());
        printBlocked("java.io import", output);
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

        ScriptOutput output = secureExecutor.executeSecure("business-logic", script, input);

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
}
