package springware.groovydemo.service;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.codehaus.groovy.syntax.Types.*;

/**
 * 보안이 강화된 Groovy 스크립트 실행기
 * SecureASTCustomizer를 사용하여 위험한 코드 실행을 방지
 */
@Service
public class SecureGroovyScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(SecureGroovyScriptExecutor.class);

    private final GroovyClassLoader secureClassLoader;
    private final Map<String, Class<?>> scriptCache;
    private final JdbcTemplate jdbcTemplate;

    // 허용된 import 패키지
    private static final List<String> ALLOWED_STAR_IMPORTS = Arrays.asList(
        "java.util",
        "java.math"
    );

    // 허용된 클래스
    private static final List<String> ALLOWED_IMPORTS = Arrays.asList(
        "java.lang.Math",
        "java.lang.String",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Boolean",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        "java.time.LocalDate",
        "java.time.LocalDateTime"
    );

    // 허용된 리시버 (메서드 호출 대상)
    private static final List<String> ALLOWED_RECEIVERS = Arrays.asList(
        // 기본 타입
        "java.lang.Object",
        "java.lang.String",
        "java.lang.Number",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Boolean",
        "java.lang.Math",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        // 컬렉션
        "java.util.List",
        "java.util.ArrayList",
        "java.util.Map",
        "java.util.HashMap",
        "java.util.LinkedHashMap",
        "java.util.Set",
        "java.util.HashSet",
        "java.util.Collection",
        "java.util.Collections",
        "java.util.Arrays",
        // 날짜
        "java.time.LocalDate",
        "java.time.LocalDateTime",
        // Groovy
        "groovy.lang.Closure",
        "groovy.lang.GString",
        "groovy.lang.Range",
        // 커스텀 DTO
        "springware.groovydemo.dto.ScriptInput",
        "springware.groovydemo.dto.ScriptOutput",
        "springware.groovydemo.dto.Product"
    );

    // 차단된 리시버 (절대 호출 불가)
    private static final List<String> DISALLOWED_RECEIVERS = Arrays.asList(
        "java.lang.System",
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.Thread",
        "java.lang.ThreadGroup",
        "java.lang.Class",
        "java.lang.ClassLoader",
        "java.lang.reflect.Method",
        "java.lang.reflect.Field",
        "java.lang.reflect.Constructor",
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.FileReader",
        "java.io.FileWriter",
        "java.nio.file.Files",
        "java.nio.file.Paths",
        "java.net.URL",
        "java.net.URLConnection",
        "java.net.Socket",
        "java.net.ServerSocket",
        "groovy.lang.GroovyShell",
        "groovy.lang.GroovyClassLoader",
        "groovy.util.Eval",
        "javax.script.ScriptEngine"
    );

    public SecureGroovyScriptExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.scriptCache = new ConcurrentHashMap<>();
        this.secureClassLoader = createSecureClassLoader();
    }

    private GroovyClassLoader createSecureClassLoader() {
        CompilerConfiguration config = new CompilerConfiguration();

        // 1. Import 제한
        ImportCustomizer importCustomizer = new ImportCustomizer();
        ALLOWED_STAR_IMPORTS.forEach(importCustomizer::addStarImports);
        ALLOWED_IMPORTS.forEach(importCustomizer::addImports);

        // 2. AST 보안 설정
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();

        // 허용된 토큰 (연산자) - 주요 연산자만 whitelist
        secureCustomizer.setTokensWhitelist(Arrays.asList(
            // 산술 연산
            PLUS, MINUS, MULTIPLY, DIVIDE, MOD, POWER,
            PLUS_EQUAL, MINUS_EQUAL, MULTIPLY_EQUAL, DIVIDE_EQUAL,
            PLUS_PLUS, MINUS_MINUS,
            // 비교 연산
            COMPARE_EQUAL, COMPARE_NOT_EQUAL,
            COMPARE_LESS_THAN, COMPARE_LESS_THAN_EQUAL,
            COMPARE_GREATER_THAN, COMPARE_GREATER_THAN_EQUAL,
            COMPARE_TO,
            // 논리 연산
            LOGICAL_AND, LOGICAL_OR, NOT,
            // 비트 연산
            BITWISE_AND, BITWISE_OR, BITWISE_XOR, BITWISE_NEGATION,
            // 할당
            EQUAL,
            // 기타
            KEYWORD_IN, KEYWORD_INSTANCEOF,
            LEFT_SQUARE_BRACKET,  // 배열/맵 접근
            QUESTION, COLON        // 삼항 연산자
        ));

        // 차단된 리시버 설정
        secureCustomizer.setReceiversBlackList(DISALLOWED_RECEIVERS);

        // 패키지 정의 금지
        secureCustomizer.setPackageAllowed(false);

        // 메서드 정의 허용 (헬퍼 함수)
        secureCustomizer.setMethodDefinitionAllowed(true);

        // 클로저 허용
        secureCustomizer.setClosuresAllowed(true);

        config.addCompilationCustomizers(importCustomizer, secureCustomizer);

        return new GroovyClassLoader(getClass().getClassLoader(), config);
    }

    /**
     * 보안 모드로 스크립트 실행
     */
    public ScriptOutput executeSecure(String scriptName, String scriptSource, ScriptInput input) {
        try {
            // 추가 보안 검사 (컴파일 전)
            validateScriptSource(scriptSource);

            Class<?> scriptClass = getOrCompileScript(scriptName, scriptSource);
            Script script = (Script) scriptClass.getDeclaredConstructor().newInstance();

            Binding binding = new Binding();
            binding.setVariable("input", input);
            binding.setVariable("output", new ScriptOutput());
            binding.setVariable("db", jdbcTemplate);
            binding.setVariable("log", LoggerFactory.getLogger("SecureGroovyScript." + scriptName));

            if (input != null && input.getParameters() != null) {
                input.getParameters().forEach(binding::setVariable);
            }

            script.setBinding(binding);
            Object result = script.run();

            Object outputVar = binding.getVariable("output");
            if (outputVar instanceof ScriptOutput scriptOutput) {
                scriptOutput.setSuccess(true);
                if (scriptOutput.getResult() == null) {
                    scriptOutput.setResult(result);
                }
                return scriptOutput;
            }

            return ScriptOutput.success(result);

        } catch (SecurityException e) {
            log.error("Security violation in script '{}': {}", scriptName, e.getMessage());
            return ScriptOutput.error("Security violation: " + e.getMessage());
        } catch (Exception e) {
            log.error("Script execution error '{}': {}", scriptName, e.getMessage());
            return ScriptOutput.error(e.getMessage());
        }
    }

    /**
     * 스크립트 소스 사전 검증 (정규식 기반 블랙리스트)
     */
    private void validateScriptSource(String scriptSource) {
        String[] dangerousPatterns = {
            "System\\.exit",
            "Runtime\\.getRuntime",
            "\\.execute\\s*\\(",
            "ProcessBuilder",
            "Class\\.forName",
            "getClass\\(\\)\\.getClassLoader",
            "Thread\\.currentThread",
            "new\\s+File\\s*\\(",
            "new\\s+URL\\s*\\(",
            "new\\s+Socket\\s*\\(",
            "GroovyShell",
            "GroovyClassLoader",
            "Eval\\.",
            "getRuntime\\(\\)",
            "exec\\s*\\(",
            "@Grab",           // 외부 의존성 다운로드 방지
            "import\\s+java\\.io\\.",
            "import\\s+java\\.net\\.",
            "import\\s+java\\.lang\\.reflect\\."
        };

        String upperSource = scriptSource;
        for (String pattern : dangerousPatterns) {
            if (upperSource.matches("(?s).*" + pattern + ".*")) {
                throw new SecurityException("Dangerous pattern detected: " + pattern);
            }
        }
    }

    private Class<?> getOrCompileScript(String scriptName, String scriptSource) {
        return scriptCache.computeIfAbsent(scriptName, key ->
            secureClassLoader.parseClass(scriptSource, scriptName + ".groovy")
        );
    }

    public void clearCache(String scriptName) {
        scriptCache.remove(scriptName);
    }

    public void clearAllCache() {
        scriptCache.clear();
    }

    public boolean isCached(String scriptName) {
        return scriptCache.containsKey(scriptName);
    }

    public int getCacheSize() {
        return scriptCache.size();
    }
}
