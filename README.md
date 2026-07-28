# Spring Boot + Groovy Integration Demo

Spring Boot 애플리케이션에서 Groovy 스크립트를 동적으로 실행하는 데모 프로젝트입니다.

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.3.0 |
| Groovy | 5.0.4 |
| Gradle | 8.8 |
| H2 Database | (in-memory) |

## 주요 기능

- ✅ Groovy 스크립트 동적 실행
- ✅ 컴파일된 스크립트 캐싱 (ConcurrentHashMap)
- ✅ JdbcTemplate을 통한 DB 연동
- ✅ SLF4J 로깅 지원
- ✅ 보안 샌드박스 (SecureASTCustomizer)
- ✅ Bulk Insert (1000건 단위 배치)

---

## 프로젝트 구조

```
src/main/java/springware/groovydemo/
├── dto/
│   ├── ScriptInput.java        # 스크립트 입력 파라미터
│   ├── ScriptOutput.java       # 스크립트 실행 결과
│   └── Product.java            # 상품 DTO
├── service/
│   ├── GroovyScriptExecutor.java       # 기본 스크립트 실행기
│   ├── SecureGroovyScriptExecutor.java # 보안 강화 실행기
│   └── BulkInsertService.java          # Bulk Insert 서비스
└── GroovyDemoApplication.java

src/main/resources/
├── scripts/                    # Groovy 스크립트 파일
│   ├── calculate-discount.groovy
│   ├── validate-order.groovy
│   ├── db-select-products.groovy
│   ├── db-insert-product.groovy
│   ├── db-update-stock.groovy
│   ├── db-calculate-inventory-value.groovy
│   ├── db-bulk-price-update.groovy
│   └── logging-demo.groovy
├── data/
│   └── sample-products.csv
├── schema.sql
└── application.properties
```

---

## 1. 기본 사용법

### GroovyScriptExecutor

```java
@Autowired
private GroovyScriptExecutor executor;

// 스크립트 실행
String script = """
    def a = input.get("a") as Integer
    def b = input.get("b") as Integer
    return a + b
    """;

ScriptInput input = new ScriptInput()
    .put("a", 10)
    .put("b", 20);

ScriptOutput output = executor.execute("add-script", script, input);

if (output.isSuccess()) {
    System.out.println("Result: " + output.getResult());  // 30
}
```

### 바인딩된 변수

스크립트 내에서 선언 없이 사용 가능한 변수들:

| 변수명 | 타입 | 설명 |
|--------|------|------|
| `input` | ScriptInput | 입력 파라미터 |
| `output` | ScriptOutput | 출력 데이터 |
| `db` | JdbcTemplate | DB 접근 |
| `log` | Logger | 로깅 |

### 변수 주입 메커니즘

```java
// GroovyScriptExecutor.java
Binding binding = new Binding();
binding.setVariable("input", input);
binding.setVariable("output", new ScriptOutput());  // ← 자동 생성
binding.setVariable("db", jdbcTemplate);
binding.setVariable("log", logger);

script.setBinding(binding);  // 스크립트에 바인딩 연결
script.run();
```

**Groovy 변수 해석 순서:**
```
1. 로컬 변수에서 찾기        → 없음
2. 스크립트 필드에서 찾기     → 없음
3. Binding에서 찾기          → 있음! (주입된 객체)
```

---

## 2. 스크립트 캐싱

### Thread-Safe 구현

```java
private final Map<String, Class<?>> scriptCache = new ConcurrentHashMap<>();

private Class<?> getOrCompileScript(String scriptName, String scriptSource) {
    return scriptCache.computeIfAbsent(scriptName, key ->
        groovyClassLoader.parseClass(scriptSource, scriptName + ".groovy")
    );
}
```

- `ConcurrentHashMap` 사용으로 동시 접근 안전
- `computeIfAbsent()`로 원자적 연산 보장
- 동일 스크립트 중복 컴파일 방지

### 성능 비교 (Groovy 5.0.4)

| 항목 | Cache 미사용 | Cache 사용 |
|------|-------------|-----------|
| 평균 실행 시간 | 13.691 ms | 0.075 ms |
| **Speed Up** | - | **183x 빠름** |

---

## 3. DB 연동 (JdbcTemplate)

### SELECT 예제

```groovy
// db-select-products.groovy
def category = input.get("category")
def limit = input.get("limit") ?: 10

def products = db.queryForList(
    "SELECT * FROM product WHERE category = ? LIMIT ?",
    category, limit
)

output.put("count", products.size())
output.put("products", products)
return products
```

### INSERT 예제

```groovy
// db-insert-product.groovy
def code = input.get("code")
def name = input.get("name")
def price = input.get("price") as BigDecimal

// 중복 체크
def existing = db.queryForList("SELECT id FROM product WHERE code = ?", code)
if (!existing.isEmpty()) {
    output.put("success", false)
    output.put("error", "Product already exists: " + code)
    return false
}

db.update(
    "INSERT INTO product (code, name, price) VALUES (?, ?, ?)",
    code, name, price
)

output.put("success", true)
return true
```

### Bulk Insert (BulkInsertService)

```java
// 1000건 단위 배치 처리
BulkInsertResult result = bulkInsertService.bulkInsert(products, 1000);

// 결과: 100,000건 기준
// - Insert Duration: 454 ms
// - Speed: 220,264 records/sec
```

---

## 4. 로깅

### SLF4J Logger 사용 (권장)

```groovy
log.debug("Debug: value = {}", value)
log.info("Processing started")
log.warn("Threshold exceeded: {}", value)
log.error("Error occurred", exception)
```

### println / System.out

```groovy
println "Debug message"
System.out.println("Standard output")
System.err.println("Error output")
```

### 로그 레벨 설정 (application.properties)

```properties
# 모든 Groovy 스크립트 DEBUG
logging.level.GroovyScript=DEBUG

# 특정 스크립트만
logging.level.GroovyScript.my-script=DEBUG
```

---

## 5. 보안 (Sandbox)

### SecureGroovyScriptExecutor

3단계 보안 레이어:

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 1: 소스코드 사전 검증 (정규식)                          │
│  - System.exit, Runtime.exec, File, URL 등 패턴 차단         │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: Import 제한 (ImportCustomizer)                    │
│  - 허용: java.util.*, java.math.*, java.time.*             │
│  - 차단: java.io.*, java.net.*, java.lang.reflect.*        │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: AST 제한 (SecureASTCustomizer)                    │
│  - 허용된 연산자만 사용 가능                                   │
│  - 차단된 리시버 호출 불가                                    │
└─────────────────────────────────────────────────────────────┘
```

### 차단되는 작업

| 위협 | 예시 코드 | 결과 |
|------|----------|------|
| 시스템 종료 | `System.exit(0)` | ❌ 차단 |
| 프로세스 실행 | `"cmd".execute()` | ❌ 차단 |
| 파일 접근 | `new File("/etc/passwd")` | ❌ 차단 |
| 네트워크 | `new URL("http://...")` | ❌ 차단 |
| 리플렉션 | `Class.forName("...")` | ❌ 차단 |
| 외부 의존성 | `@Grab('...')` | ❌ 차단 |

### 허용되는 작업

```groovy
// 기본 연산
def result = a + b * 2

// 컬렉션
def list = [1, 2, 3].findAll { it > 1 }

// BigDecimal
def total = price * quantity

// 날짜
def today = LocalDate.now()

// 클로저
def square = { it * it }
```

### 사용 예시

```java
@Autowired
SecureGroovyScriptExecutor secureExecutor;

ScriptOutput output = secureExecutor.executeSecure("script-name", script, input);

if (!output.isSuccess()) {
    // Security violation 또는 에러
    log.error("Failed: {}", output.getErrorMessage());
}
```

### 주의사항

> **SecureASTCustomizer 한계**: 정적 분석 기반이므로 동적 우회 가능성 존재.
> 프로덕션에서는 [Jenkins Groovy Sandbox](https://github.com/jenkinsci/groovy-sandbox) 같은 런타임 인터셉터 추가 권장.

---

## 6. 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests "GroovyScriptExecutorTest"
./gradlew test --tests "GroovyScriptPerformanceTest"
./gradlew test --tests "GroovyDbIntegrationTest"
./gradlew test --tests "SecureGroovyScriptTest"
./gradlew test --tests "BulkInsertServiceTest"
```

---

## 7. 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# H2 Console 접속 (http://localhost:8080/h2-console)
# JDBC URL: jdbc:h2:mem:testdb
# Username: sa
# Password: (empty)
```

---

## 참고 자료

- [Apache Groovy Documentation](https://groovy-lang.org/documentation.html)
- [Groovy 5.0 Release Notes](https://groovy-lang.org/releasenotes/groovy-5.0.html)
- [SecureASTCustomizer API](https://docs.groovy-lang.org/latest/html/api/org/codehaus/groovy/control/customizers/SecureASTCustomizer.html)
- [Spring JdbcTemplate](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/core/JdbcTemplate.html)
