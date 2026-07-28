// 로깅 데모 스크립트
def name = input.get("name") ?: "World"

// 1. SLF4J Logger 사용 (권장)
log.debug("Debug level: Processing name = {}", name)
log.info("Info level: Starting script execution")
log.warn("Warn level: This is a warning message")
log.error("Error level: This is an error message")

// 2. println 사용 (stdout)
println "[println] Hello, " + name

// 3. System.out 직접 사용
System.out.println("[System.out] Processing...")

// 4. 조건부 로깅
def value = input.get("value") ?: 0
if (value > 100) {
    log.warn("Value {} exceeds threshold 100", value)
}

// 5. 예외 로깅
try {
    if (input.get("throwError")) {
        throw new RuntimeException("Test exception")
    }
} catch (Exception e) {
    log.error("Exception caught: {}", e.getMessage(), e)
}

output.put("message", "Logging demo completed")
output.setResult("Hello, " + name)

return "Hello, " + name
