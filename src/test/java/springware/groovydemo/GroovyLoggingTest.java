package springware.groovydemo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;
import springware.groovydemo.service.GroovyScriptExecutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Groovy Logging Tests")
class GroovyLoggingTest {

    @Autowired
    private GroovyScriptExecutor executor;

    @Test
    @DisplayName("Groovy 스크립트에서 다양한 로깅 방법 테스트")
    void testLoggingMethods() throws IOException {
        String script = loadScript("scripts/logging-demo.groovy");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Groovy Logging Demo");
        System.out.println("=".repeat(60));

        ScriptInput input = new ScriptInput()
            .put("name", "Groovy")
            .put("value", 150);  // threshold 초과

        ScriptOutput output = executor.execute("logging-demo", script, input);

        assertTrue(output.isSuccess());
        assertEquals("Hello, Groovy", output.getResult());

        System.out.println("\n[Script Output]");
        System.out.println("Result: " + output.getResult());
        System.out.println("=".repeat(60) + "\n");
    }

    @Test
    @DisplayName("Groovy 스크립트에서 예외 로깅 테스트")
    void testExceptionLogging() throws IOException {
        String script = loadScript("scripts/logging-demo.groovy");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Groovy Exception Logging Demo");
        System.out.println("=".repeat(60));

        ScriptInput input = new ScriptInput()
            .put("name", "Test")
            .put("throwError", true);

        ScriptOutput output = executor.execute("logging-demo-error", script, input);

        assertTrue(output.isSuccess());  // 스크립트 자체는 성공 (catch로 처리됨)

        System.out.println("\n[Script completed with exception handling]");
        System.out.println("=".repeat(60) + "\n");
    }

    @Test
    @DisplayName("인라인 스크립트에서 로깅 테스트")
    void testInlineScriptLogging() {
        String script = """
            log.info("=== Inline Script Start ===")

            def items = ["Apple", "Banana", "Cherry"]

            items.eachWithIndex { item, idx ->
                log.debug("Processing item[{}]: {}", idx, item)
            }

            println "Processed " + items.size() + " items"

            log.info("=== Inline Script End ===")

            return items.size()
            """;

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        Inline Script Logging Demo");
        System.out.println("=".repeat(60));

        ScriptOutput output = executor.execute("inline-logging", script, new ScriptInput());

        assertTrue(output.isSuccess());
        assertEquals(3, output.getResult());

        System.out.println("=".repeat(60) + "\n");
    }

    private String loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
