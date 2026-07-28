package springware.groovydemo.dto;

import java.util.HashMap;
import java.util.Map;

public class ScriptInput {

    private Map<String, Object> parameters = new HashMap<>();

    public ScriptInput() {
    }

    public ScriptInput(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public ScriptInput put(String key, Object value) {
        this.parameters.put(key, value);
        return this;
    }

    public Object get(String key) {
        return this.parameters.get(key);
    }
}
