package springware.groovydemo.dto;

import java.util.HashMap;
import java.util.Map;

public class ScriptOutput {

    private boolean success;
    private Object result;
    private String errorMessage;
    private Map<String, Object> data = new HashMap<>();

    public ScriptOutput() {
    }

    public static ScriptOutput success(Object result) {
        ScriptOutput output = new ScriptOutput();
        output.setSuccess(true);
        output.setResult(result);
        return output;
    }

    public static ScriptOutput error(String errorMessage) {
        ScriptOutput output = new ScriptOutput();
        output.setSuccess(false);
        output.setErrorMessage(errorMessage);
        return output;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public ScriptOutput put(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
