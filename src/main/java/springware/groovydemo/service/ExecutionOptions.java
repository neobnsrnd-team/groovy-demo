package springware.groovydemo.service;

/**
 * Groovy 스크립트 실행 시 보안 옵션 설정
 *
 * 기본적으로 모든 위험한 작업은 차단되며,
 * 필요한 경우 선택적으로 허용할 수 있습니다.
 */
public class ExecutionOptions {

    private final boolean allowDb;
    private final boolean allowFileAccess;
    private final boolean allowNetworkAccess;
    private final boolean allowThreadAccess;
    private final boolean allowReflection;
    private final boolean allowSystemAccess;
    private final boolean allowProcessExecution;

    private ExecutionOptions(Builder builder) {
        this.allowDb = builder.allowDb;
        this.allowFileAccess = builder.allowFileAccess;
        this.allowNetworkAccess = builder.allowNetworkAccess;
        this.allowThreadAccess = builder.allowThreadAccess;
        this.allowReflection = builder.allowReflection;
        this.allowSystemAccess = builder.allowSystemAccess;
        this.allowProcessExecution = builder.allowProcessExecution;
    }

    /**
     * 기본 옵션 (모든 위험한 작업 차단)
     */
    public static ExecutionOptions defaults() {
        return new Builder().build();
    }

    /**
     * DB 접근만 허용하는 옵션
     */
    public static ExecutionOptions withDb() {
        return new Builder().allowDb(true).build();
    }

    /**
     * 모든 작업 허용 (주의: 보안 위험)
     */
    public static ExecutionOptions allowAll() {
        return new Builder()
            .allowDb(true)
            .allowFileAccess(true)
            .allowNetworkAccess(true)
            .allowThreadAccess(true)
            .allowReflection(true)
            .allowSystemAccess(true)
            .allowProcessExecution(true)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isAllowDb() {
        return allowDb;
    }

    public boolean isAllowFileAccess() {
        return allowFileAccess;
    }

    public boolean isAllowNetworkAccess() {
        return allowNetworkAccess;
    }

    public boolean isAllowThreadAccess() {
        return allowThreadAccess;
    }

    public boolean isAllowReflection() {
        return allowReflection;
    }

    public boolean isAllowSystemAccess() {
        return allowSystemAccess;
    }

    public boolean isAllowProcessExecution() {
        return allowProcessExecution;
    }

    @Override
    public String toString() {
        return "ExecutionOptions{" +
            "allowDb=" + allowDb +
            ", allowFileAccess=" + allowFileAccess +
            ", allowNetworkAccess=" + allowNetworkAccess +
            ", allowThreadAccess=" + allowThreadAccess +
            ", allowReflection=" + allowReflection +
            ", allowSystemAccess=" + allowSystemAccess +
            ", allowProcessExecution=" + allowProcessExecution +
            '}';
    }

    /**
     * Builder for ExecutionOptions
     */
    public static class Builder {
        private boolean allowDb = false;
        private boolean allowFileAccess = false;
        private boolean allowNetworkAccess = false;
        private boolean allowThreadAccess = false;
        private boolean allowReflection = false;
        private boolean allowSystemAccess = false;
        private boolean allowProcessExecution = false;

        public Builder allowDb(boolean allow) {
            this.allowDb = allow;
            return this;
        }

        public Builder allowFileAccess(boolean allow) {
            this.allowFileAccess = allow;
            return this;
        }

        public Builder allowNetworkAccess(boolean allow) {
            this.allowNetworkAccess = allow;
            return this;
        }

        public Builder allowThreadAccess(boolean allow) {
            this.allowThreadAccess = allow;
            return this;
        }

        public Builder allowReflection(boolean allow) {
            this.allowReflection = allow;
            return this;
        }

        public Builder allowSystemAccess(boolean allow) {
            this.allowSystemAccess = allow;
            return this;
        }

        public Builder allowProcessExecution(boolean allow) {
            this.allowProcessExecution = allow;
            return this;
        }

        public ExecutionOptions build() {
            return new ExecutionOptions(this);
        }
    }
}
