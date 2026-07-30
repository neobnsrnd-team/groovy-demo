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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.codehaus.groovy.syntax.Types.*;

/**
 * 보안이 강화된 Groovy 스크립트 실행기
 * SecureASTCustomizer를 사용하여 위험한 코드 실행을 방지
 *
 * 사용법:
 * - execute(name, source, input): 기본 보안 (모든 위험 작업 차단)
 * - execute(name, source, input, options): 선택적 보안 완화
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

    // 차단된 리시버 (기본)
    private static final List<String> BASE_DISALLOWED_RECEIVERS = Arrays.asList(
        "groovy.lang.GroovyShell",
        "groovy.lang.GroovyClassLoader",
        "groovy.util.Eval",
        "javax.script.ScriptEngine"
    );

    public SecureGroovyScriptExecutor(@Nullable JdbcTemplate jdbcTemplate) {
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

        // 허용된 토큰 (연산자)
        secureCustomizer.setTokensWhitelist(Arrays.asList(
            PLUS, MINUS, MULTIPLY, DIVIDE, MOD, POWER,
            PLUS_EQUAL, MINUS_EQUAL, MULTIPLY_EQUAL, DIVIDE_EQUAL,
            PLUS_PLUS, MINUS_MINUS,
            COMPARE_EQUAL, COMPARE_NOT_EQUAL,
            COMPARE_LESS_THAN, COMPARE_LESS_THAN_EQUAL,
            COMPARE_GREATER_THAN, COMPARE_GREATER_THAN_EQUAL,
            COMPARE_TO,
            LOGICAL_AND, LOGICAL_OR, NOT,
            BITWISE_AND, BITWISE_OR, BITWISE_XOR, BITWISE_NEGATION,
            EQUAL,
            KEYWORD_IN, KEYWORD_INSTANCEOF,
            LEFT_SQUARE_BRACKET,
            QUESTION, COLON
        ));

        // 기본 차단 리시버
        secureCustomizer.setReceiversBlackList(BASE_DISALLOWED_RECEIVERS);

        // 패키지 정의 금지
        secureCustomizer.setPackageAllowed(false);

        // 메서드 정의 허용
        secureCustomizer.setMethodDefinitionAllowed(true);

        // 클로저 허용
        secureCustomizer.setClosuresAllowed(true);

        config.addCompilationCustomizers(importCustomizer, secureCustomizer);

        return new GroovyClassLoader(getClass().getClassLoader(), config);
    }

    /**
     * 기본 보안 모드로 스크립트 실행 (모든 위험 작업 차단)
     */
    public ScriptOutput execute(String scriptName, String scriptSource, ScriptInput input) {
        return execute(scriptName, scriptSource, input, ExecutionOptions.defaults());
    }

    /**
     * 옵션 기반 스크립트 실행
     */
    public ScriptOutput execute(String scriptName, String scriptSource, ScriptInput input,
                                ExecutionOptions options) {
        try {
            // 옵션 기반 보안 검사
            validateScriptSource(scriptSource, options);

            Class<?> scriptClass = getOrCompileScript(scriptName, scriptSource);
            Script script = (Script) scriptClass.getDeclaredConstructor().newInstance();

            Binding binding = createBinding(scriptName, input, options);

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
     * 바인딩 생성 (옵션에 따라 변수 바인딩)
     */
    private Binding createBinding(String scriptName, ScriptInput input, ExecutionOptions options) {
        Binding binding = new Binding();
        binding.setVariable("input", input);
        binding.setVariable("output", new ScriptOutput());
        binding.setVariable("log", LoggerFactory.getLogger("SecureGroovyScript." + scriptName));

        // 옵션에 따른 추가 바인딩
        if (options.isAllowDb()) {
            if (jdbcTemplate == null) {
                throw new IllegalStateException("JdbcTemplate is not available");
            }
            binding.setVariable("db", jdbcTemplate);
        }

        // input 파라미터 개별 바인딩
        if (input != null && input.getParameters() != null) {
            input.getParameters().forEach(binding::setVariable);
        }

        return binding;
    }

    /**
     * 스크립트 소스 사전 검증 (옵션 기반)
     */
    private void validateScriptSource(String scriptSource, ExecutionOptions options) {
        List<PatternCheck> checks = new ArrayList<>();

        // 항상 차단되는 패턴
        checks.add(new PatternCheck("@Grab", "External dependency loading (@Grab)"));
        checks.add(new PatternCheck("GroovyShell", "GroovyShell usage"));
        checks.add(new PatternCheck("GroovyClassLoader", "GroovyClassLoader usage"));
        checks.add(new PatternCheck("Eval\\.", "Eval usage"));

        // System 접근 (allowSystemAccess가 false일 때 차단)
        if (!options.isAllowSystemAccess()) {
            checks.add(new PatternCheck("System\\.exit", "System.exit()"));
            checks.add(new PatternCheck("System\\.getenv", "System.getenv()"));
            checks.add(new PatternCheck("System\\.getProperty", "System.getProperty()"));
            checks.add(new PatternCheck("System\\.setProperty", "System.setProperty()"));
            checks.add(new PatternCheck("Runtime\\.getRuntime", "Runtime.getRuntime()"));
            checks.add(new PatternCheck("getRuntime\\(\\)", "getRuntime()"));
        }

        // Process 실행 (allowProcessExecution가 false일 때 차단)
        if (!options.isAllowProcessExecution()) {
            checks.add(new PatternCheck("\\.execute\\s*\\(", "String.execute()"));
            checks.add(new PatternCheck("ProcessBuilder", "ProcessBuilder"));
            checks.add(new PatternCheck("exec\\s*\\(", "exec()"));
        }

        // Thread 접근 (allowThreadAccess가 false일 때 차단)
        if (!options.isAllowThreadAccess()) {
            checks.add(new PatternCheck("Thread\\.", "Thread access"));
            checks.add(new PatternCheck("new\\s+Thread", "Thread creation"));
        }

        // Reflection 접근 (allowReflection이 false일 때 차단)
        if (!options.isAllowReflection()) {
            checks.add(new PatternCheck("Class\\.forName", "Class.forName()"));
            checks.add(new PatternCheck("getClass\\(\\)\\.getClassLoader", "getClassLoader()"));
            checks.add(new PatternCheck("\\.getDeclaredMethod", "getDeclaredMethod()"));
            checks.add(new PatternCheck("\\.getDeclaredField", "getDeclaredField()"));
            checks.add(new PatternCheck("import\\s+java\\.lang\\.reflect\\.", "java.lang.reflect import"));
        }

        // File 접근 (allowFileAccess가 false일 때 차단)
        if (!options.isAllowFileAccess()) {
            checks.add(new PatternCheck("new\\s+File\\s*\\(", "File creation"));
            checks.add(new PatternCheck("new\\s+FileInputStream", "FileInputStream"));
            checks.add(new PatternCheck("new\\s+FileOutputStream", "FileOutputStream"));
            checks.add(new PatternCheck("new\\s+FileReader", "FileReader"));
            checks.add(new PatternCheck("new\\s+FileWriter", "FileWriter"));
            checks.add(new PatternCheck("Files\\.", "java.nio.file.Files"));
            checks.add(new PatternCheck("Paths\\.", "java.nio.file.Paths"));
            checks.add(new PatternCheck("import\\s+java\\.io\\.", "java.io import"));
            checks.add(new PatternCheck("import\\s+java\\.nio\\.file\\.", "java.nio.file import"));
        }

        // Network 접근 (allowNetworkAccess가 false일 때 차단)
        if (!options.isAllowNetworkAccess()) {
            checks.add(new PatternCheck("new\\s+URL\\s*\\(", "URL creation"));
            checks.add(new PatternCheck("new\\s+Socket\\s*\\(", "Socket creation"));
            checks.add(new PatternCheck("new\\s+ServerSocket", "ServerSocket"));
            checks.add(new PatternCheck("import\\s+java\\.net\\.", "java.net import"));
        }

        // DB 접근 (allowDb가 false일 때 차단)
        if (!options.isAllowDb()) {
            checks.add(new PatternCheck("\\bdb\\.", "DB access"));
        }

        // 패턴 검사 실행
        for (PatternCheck check : checks) {
            if (scriptSource.matches("(?s).*" + check.pattern + ".*")) {
                throw new SecurityException(check.description + " is not allowed. " +
                    "Use appropriate ExecutionOptions to enable this feature.");
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

    /**
     * 패턴 검사 정보
     */
    private record PatternCheck(String pattern, String description) {}
}
