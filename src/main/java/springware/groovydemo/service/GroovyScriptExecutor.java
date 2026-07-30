package springware.groovydemo.service;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy 스크립트 실행기 (보안 제약 없음)
 *
 * 사용법:
 * - execute(name, source, input): 기본 실행 (DB 접근 불가)
 * - execute(name, source, input, options): 옵션 기반 실행
 */
@Service
public class GroovyScriptExecutor {

    private final GroovyClassLoader groovyClassLoader;
    private final Map<String, Class<?>> scriptCache;
    private final JdbcTemplate jdbcTemplate;

    public GroovyScriptExecutor(@Nullable JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.groovyClassLoader = new GroovyClassLoader(getClass().getClassLoader());
        this.scriptCache = new ConcurrentHashMap<>();
    }

    /**
     * 기본 옵션으로 스크립트 실행 (DB 접근 불가)
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
            Class<?> scriptClass = getOrCompileScript(scriptName, scriptSource);
            Script script = (Script) scriptClass.getDeclaredConstructor().newInstance();

            Binding binding = new Binding();
            binding.setVariable("input", input);
            binding.setVariable("output", new ScriptOutput());
            binding.setVariable("log", LoggerFactory.getLogger("GroovyScript." + scriptName));

            // DB 접근 (옵션에 따라)
            if (options.isAllowDb()) {
                if (jdbcTemplate == null) {
                    return ScriptOutput.error("JdbcTemplate is not available");
                }
                binding.setVariable("db", jdbcTemplate);
            }

            // input 파라미터 개별 바인딩
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
        } catch (Exception e) {
            return ScriptOutput.error(e.getMessage());
        }
    }

    private Class<?> getOrCompileScript(String scriptName, String scriptSource) {
        return scriptCache.computeIfAbsent(scriptName, key ->
            groovyClassLoader.parseClass(scriptSource, scriptName + ".groovy")
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
