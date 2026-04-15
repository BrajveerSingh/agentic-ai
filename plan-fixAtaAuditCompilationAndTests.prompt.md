# Plan: Fix `ata-audit` Compilation & Test Failures

Analysis reveals **3 distinct layers of problems** across compilation, test logic, and plan-vs-implementation gaps. The core blocker is an architectural misuse of `@EntityScan` in a non-Boot library module. Once unblocked, a secondary test logic bug would cause one test to fail.

---

## Issues Found

### 🔴 Issue 1 (Blocker): `@EntityScan` in a library module — `AuditModule.java`

`@EntityScan` from `org.springframework.boot.autoconfigure.domain` is a **Spring Boot application-level annotation**, designed for `@SpringBootApplication` entry-point classes — **not** for `@Configuration` classes in library sub-modules. The `ata-audit` module is a plain library, not a Boot app. This causes `package org.springframework.boot.autoconfigure.domain does not exist` at compile time, which is why no `surefire-reports` directory exists in `ata-audit/target/` — **zero tests ever ran**.

Adding `spring-boot-autoconfigure` explicitly to `pom.xml` (the previous fix attempt) is the wrong solution — it treats the symptom, not the cause.

### 🟡 Issue 2 (Test Logic Bug): Null `toolCallId` in `AuditServiceTest`

In `AuditServiceTest.shouldLogToolCallStartAndComplete()` (line 109–130):
```java
ToolCallEntity savedToolCall = new ToolCallEntity(UUID.randomUUID(), toolName, ...);
// savedToolCall.getToolCallId() → NULL (JPA @GeneratedValue never fires outside persistence)

auditService.logToolCallComplete(savedToolCall.getToolCallId(), ...); // passes NULL
// → toolCallRepository.findById(null) → Mockito's any(UUID.class) won't match null
// → orElseThrow() fires → IllegalArgumentException
```

### 🟡 Issue 3 (Plan Gap): Hardcoded version in `pom.xml`

`<version>4.0.5</version>` on the `spring-boot-autoconfigure` dependency is hardcoded instead of `${spring-boot.version}`. Since the BOM already manages this, the explicit dependency block is entirely redundant and should be removed.

### 🔵 Issue 4 (Plan vs Implementation Gap): Missing immutability test

The plan's Phase 2 test suite specifies:
```java
assertThatThrownBy(() -> auditService.deleteAuditTrail(app.getId()))
    .isInstanceOf(UnsupportedOperationException.class);
```
`AuditService` has no `deleteAuditTrail` method and `AuditServiceTest` has no immutability assertion. The design intent is correct (no delete methods on repositories), but the explicit test verifying this is absent.

---

## Steps

### Step 1 — Remove `@EntityScan` from `AuditModule.java`

Strip the annotation and its import entirely from:
```
ata-audit/src/main/java/com/bank/ata/audit/AuditModule.java
```
Entity scanning is handled by Spring Boot auto-config in `ata-app` when JPA entities are on the classpath. A plain `@Configuration` library module must not carry Boot application-level scanning annotations.

**Before:**
```java
import org.springframework.boot.autoconfigure.domain.EntityScan;
// ...
@EntityScan(basePackages = "com.bank.ata.audit.entity")
public class AuditModule { ... }
```

**After:**
```java
// import removed
// annotation removed
public class AuditModule { ... }
```

### Step 2 — Remove the redundant `spring-boot-autoconfigure` dependency from `ata-audit/pom.xml`

Remove the entire explicit block:
```xml
<!-- Spring Boot Autoconfigure  ← REMOVE THIS BLOCK -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <version>4.0.5</version>
</dependency>
```
It is already transitively provided by `spring-boot-starter-data-jpa` and managed by the BOM. This block is the leftover of the incorrect prior fix attempt.

### Step 3 — Fix null `toolCallId` in `AuditServiceTest`

Use `org.springframework.test.util.ReflectionTestUtils` to inject a pre-determined UUID into the `toolCallId` field of the manually constructed `ToolCallEntity` before passing it to the mock and service call.

**In `AuditServiceTest.shouldLogToolCallStartAndComplete()`:**

```java
// After constructing savedToolCall:
ToolCallEntity savedToolCall = new ToolCallEntity(UUID.randomUUID(), toolName, "[\"CUST001\"]");
UUID knownToolCallId = UUID.randomUUID();
ReflectionTestUtils.setField(savedToolCall, "toolCallId", knownToolCallId);

// ...mocks stay the same...

// When - Complete: use the known ID, not savedToolCall.getToolCallId()
auditService.logToolCallComplete(knownToolCallId, new TestResult(720, "GOOD"), 150L);
```

Add `import org.springframework.test.util.ReflectionTestUtils;` to the test class.

### Step 4 — Add the missing immutability test to `AuditServiceTest`

Add a test asserting that `AuditService` exposes no delete operation, satisfying the Phase 2 immutability acceptance criterion from the plan:

```java
@Test
@DisplayName("AuditService should not expose any delete operations (immutability)")
void shouldNotExposeDeleteOperations() {
    // Verify no deleteAuditTrail or any delete method exists on AuditService
    boolean hasDelete = java.util.Arrays.stream(AuditService.class.getMethods())
            .anyMatch(m -> m.getName().toLowerCase().contains("delete"));
    assertThat(hasDelete).isFalse();
}
```

### Step 5 — Validate with targeted Maven run

```bash
# Test only the audit module first
mvn test -pl ata-audit

# Then full project test run to confirm no regressions
mvn test
```

Expected outcome:
- `ata-audit`: 6 tests run, 0 failures, 0 errors
- All other modules remain green

---

## Further Considerations

### Entity scanning at app level
Should `AuditTrailAgentApplication.java` in `ata-app` add `@EntityScan("com.bank.ata.audit.entity")` explicitly? Only needed if Spring Boot 4's JPA auto-config doesn't pick up entities from the `ata-audit` jar automatically. With `ddl-auto: create-drop` (H2, dev profile), entities on the classpath are found without explicit annotation. Monitor Hibernate schema creation logs on first run to confirm all four tables (`audit_event`, `reasoning_step`, `tool_call`, `loan_decision`) are created.

### Missing `@DataJpaTest` integration test
The plan (Phase 2) calls for a full persistence integration test verifying real repository queries (`shouldCaptureAllToolCalls`, `shouldCaptureReasoningSteps`). Currently only Mockito unit tests exist. A `@DataJpaTest` with H2 (already on classpath) would validate the actual JPQL query correctness and close this plan gap. This is the next recommended test to add after the compilation fix.

### `AuditContextHolder` thread-safety on Java 25 virtual threads
Java 25 uses virtual threads by default in Spring Boot 4's embedded Tomcat. `ThreadLocal`-based `AuditContextHolder` does not propagate across virtual thread boundaries in structured concurrency patterns. If `AuditTrailAgent.evaluateLoan()` is dispatched onto a virtual thread, the audit context set on the carrier thread may be invisible to the task thread.

**Recommended fix:** Replace `ThreadLocal<AuditContext>` with `ScopedValue<AuditContext>` (finalised in Java 21+, idiomatic in Java 25):

```java
// AuditContextHolder.java
public static final ScopedValue<AuditContext> CONTEXT = ScopedValue.newInstance();

// Caller sets context:
ScopedValue.where(AuditContextHolder.CONTEXT, new AuditContext(sessionId, appId))
           .run(() -> agent.evaluateLoan(application));

// Interceptor reads context:
AuditContextHolder.CONTEXT.orElse(null);
```

