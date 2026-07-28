package springware.groovydemo.service;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import springware.groovydemo.dto.ScriptInput;
import springware.groovydemo.dto.ScriptOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GroovyScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(GroovyScriptExecutor.class);

    private final GroovyClassLoader groovyClassLoader;
    private final Map<String, Class<?>> scriptCache;
    private final JdbcTemplate jdbcTemplate;

    public GroovyScriptExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.groovyClassLoader = new GroovyClassLoader(getClass().getClassLoader());
        this.scriptCache = new ConcurrentHashMap<>();
    }

    /**
     * Execute a Groovy script with the given input.
     * The script is compiled and cached for performance.
     *
     * @param scriptName unique name/key for caching
     * @param scriptSource Groovy script source code
     * @param input input parameters for the script
     * @return ScriptOutput containing the result or error
     */
    public ScriptOutput execute(String scriptName, String scriptSource, ScriptInput input) {
        return execute(scriptName, scriptSource, input, null);
    }

    /**
     * Execute a Groovy script with additional bindings.
     *
     * @param scriptName unique name/key for caching
     * @param scriptSource Groovy script source code
     * @param input input parameters for the script
     * @param extraBindings additional variables to bind (can be null)
     * @return ScriptOutput containing the result or error
     */
    public ScriptOutput execute(String scriptName, String scriptSource, ScriptInput input,
                                Map<String, Object> extraBindings) {
        try {
            Class<?> scriptClass = getOrCompileScript(scriptName, scriptSource);
            Script script = (Script) scriptClass.getDeclaredConstructor().newInstance();

            Binding binding = new Binding();
            binding.setVariable("input", input);
            binding.setVariable("output", new ScriptOutput());
            binding.setVariable("db", jdbcTemplate);  // DB access
            binding.setVariable("log", LoggerFactory.getLogger("GroovyScript." + scriptName));  // Logger

            // Bind individual parameters for convenience
            if (input != null && input.getParameters() != null) {
                input.getParameters().forEach(binding::setVariable);
            }

            // Bind extra variables
            if (extraBindings != null) {
                extraBindings.forEach(binding::setVariable);
            }

            script.setBinding(binding);
            Object result = script.run();

            // Check if script set output variable
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

    /**
     * Get compiled script from cache or compile and cache it.
     */
    private Class<?> getOrCompileScript(String scriptName, String scriptSource) {
        return scriptCache.computeIfAbsent(scriptName, key ->
            groovyClassLoader.parseClass(scriptSource, scriptName + ".groovy")
        );
    }

    /**
     * Clear a specific script from cache (useful when script is updated).
     */
    public void clearCache(String scriptName) {
        scriptCache.remove(scriptName);
    }

    /**
     * Clear all cached scripts.
     */
    public void clearAllCache() {
        scriptCache.clear();
    }

    /**
     * Check if a script is cached.
     */
    public boolean isCached(String scriptName) {
        return scriptCache.containsKey(scriptName);
    }

    /**
     * Get the number of cached scripts.
     */
    public int getCacheSize() {
        return scriptCache.size();
    }
}
