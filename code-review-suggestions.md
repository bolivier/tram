# Tram Codebase Review - Improvement Suggestions

**Date:** 2025-11-14

This document contains suggestions for improving the Tram codebase focused on code quality, organization, and architecture without changing behavior.

---

## Overview

The Tram codebase demonstrates thoughtful architectural decisions with clean separation of concerns. This review analyzed:

- 25 source files
- 22 test files
- Overall architecture and organization

The following improvements are organized by category, from code quality to minor cleanups.

---

## 1. Code Quality & Consistency

### Naming Consistency

**Issue: Namespace naming inconsistency**

- Location: `src/cli/tram_cli/`
- Problem: Uses underscores while the rest of the codebase uses dashes (`tram-config`)
- Recommendation: Rename `tram_cli` → `tram-cli` for consistency

**Issue: Mixed terminology**

- Location: `src/main/tram/impl/router.clj:63-64`
- Problem: Schema name `HandlEntrySchema` has typo
- Recommendation: Rename to `HandlerEntrySchema`

### Documentation Gaps

**Missing docstrings** for important functions:

- `tram.associations/lookup-join-table` (line 26)
- `tram.associations/belongs-to!` (line 37)
- `tram.impl.router/route?` (line 72)
- `tram.impl.router/map-routes` (line 171)
- `tram.utils/map-keys` and `tram.utils/map-vals` (lines 3-7)

**Unresolved TODO**

- Location: `src/main/tram/rendering/template_renderer.clj:4`
- Content: `TODO revisit how this works. It seems not that good.`
- Recommendation: Address or remove this TODO

### Code Duplication

**Issue: Repetitive case statements**

- Location: `src/main/tram/db.clj:42-49`
- Current code:
  ```clojure
  (case type
    "json"   (json/parse-string value true)
    "jsonb"  (json/parse-string value true)
    "citext" (str value)
    value)
  ```
- Suggested improvement:
  ```clojure
  (case type
    ("json" "jsonb") (json/parse-string value true)
    "citext" (str value)
    value)
  ```

### Magic Values

**Issue: HTTP status codes as literals**

- Locations: `src/main/tram/routes.clj:141` and elsewhere
- Recommendation: Define constants:
  ```clojure
  (def HTTP-STATUS-GATEWAY-TIMEOUT 504)
  (def HTTP-STATUS-REDIRECT 303)
  (def HTTP-STATUS-MOVED-PERMANENTLY 301)
  ```

**Issue: Hardcoded path strings**

- Locations: `src/main/tram/routes.clj:80,96`
- Problem: "/assets" is hardcoded in multiple places
- Recommendation: Extract to a constant

---

## 2. Architecture & Design

### Global Mutable State

**Issue: `*associations*` atom design**

- Location: `src/main/tram/associations.clj:23-24`
- Current code:
  ```clojure
  (defonce ^:dynamic *associations* (atom {}))
  ```
- Problems:
  - Global state makes testing harder (visible in tests resetting it)
  - Dynamic vars containing atoms is unusual - pick one or the other
  - Difficult to reason about state changes
- Recommendation: Either:
  1. Make it a parameter passed through functions, OR
  2. Use a proper dynamic var binding strategy without the atom

### Protocol Extensions Side Effects

**Issue: Database protocol extensions on namespace load**

- Location: `src/main/tram/db.clj:51-85`
- Problem: Extends protocols in main namespace, causing side effects on require
- Recommendation: Move to dedicated `tram.db.postgres-extensions` namespace that users explicitly require

### Tight Coupling

**Issue: Compile-time namespace coupling**

- Location: `src/main/tram/impl/router.clj:90-93`
- Problem: `get-automagic-template-symbol` uses `*ns*` (compile-time namespace) to resolve templates, creating tight coupling
- Recommendation: Make template resolution requirements more explicit

### Error Handling Pattern Inconsistency

**Issue: Inconsistent exception handling**

- Compare:
  - `src/main/tram/associations.clj:31-35` - catches and checks message with regex
  - `src/main/tram/db.clj:162` - catches and silently ignores with println
- Recommendation: Establish consistent error handling patterns, prefer ex-info with data for program-readable errors

---

## 3. Organization & Structure

### Mixed Concerns

**Issue: db.clj has too many responsibilities**

- Location: `src/main/tram/db.clj`
- Mixes:
  - Database connection/querying (lines 42-146)
  - Migration management (lines 148-176)
  - Seeder creation (lines 177-213)
- Recommendation: Split into:
  - `tram.db.core`
  - `tram.db.migrations`
  - `tram.db.seeders`

### Namespace Organization

**Issue: `bb_compatible` vs `main` split unclear**

- Location: `src/bb_compatible/` and `src/main/`
- Problem: The architectural split isn't immediately clear to new contributors
- Recommendations:
  - Add README files in each directory explaining the purpose
  - Consider: Is there value in keeping language.clj and utils.clj in bb_compatible when they're used everywhere?

### Test Organization

**Issue: Test fixtures location**

- Location: `test/main/tram/test_fixtures.clj`
- Problem: Mixed with test files
- Recommendation: Create `test/fixtures/` directory for shared test data/helpers

### Import Patterns

**Issue: Large import-vars blocks**

- Location: `src/main/tram/db.clj:87-146`
- Problem: 60 lines of imports make it unclear what Tram adds vs what's re-exported from Toucan2
- Recommendation: Document which functions are Tram-specific vs re-exports, possibly group them

---

## 4. Error Handling & Robustness

### Error Messages

**Issue: Cryptic error message**

- Location: `src/main/tram/impl/router.clj:76`
- Current code:
  ```clojure
  (throw (ex-info "broke" {:issue "Route is missing name key" ...}))
  ```
- Recommendation: Use more descriptive message like "Route is missing required :name key"

**Issue: Multi-line string concatenation**

- Location: `src/main/tram/rendering/template_renderer.clj:86-98`
- Current: Multiple string concatenations for error message
- Recommendation: Use format string:
  ```clojure
  (format "Route (%s) does not have a valid template.\n\nExpected to find template called `%s` at: %s"
          (:uri request)
          (get-name template ctx)
          (get-namespace template ctx))
  ```

### Missing Validation

**Issue: Silent nil returns**

- Location: `src/main/tram/html.clj:23-24`
- Problem: `make-path` returns `nil` when params are missing (see test line 20), might fail silently
- Recommendation: Consider failing fast or logging warnings

### Exception Handling

**Issue: Swallowed exceptions**

- Location: `src/main/tram/db.clj:160-163`
- Current code:
  ```clojure
  (try (init-migrations)
       (catch Exception _ (println "Skipping initialize")))
  ```
- Problem: Catches all exceptions without logging what went wrong
- Recommendation: At minimum, log the exception details

---

## 5. Performance Considerations

### String Operations

**Issue: Repeated string operations**

- Location: `src/main/tram/language.clj:76-90`
- Problem: `ns-ize` and `file-ize` do multiple string replacements and are likely called frequently
- Recommendation: Consider memoization if profiling shows it's a bottleneck

### Unnecessary Work

**Issue: Full tree walk for route expansion**

- Location: `src/main/tram/html.clj:60-61`
- Problem: Uses `clojure.walk/prewalk` which walks entire tree
- Impact: Could be expensive for large hiccup structures
- Recommendation: Consider `postwalk` or targeted walk that only processes maps

### Inefficient Query Pattern

**Issue: Potential N+1 queries**

- Location: `src/main/tram/associations.clj:156-157`
- Problem: Default hydration case:
  ```clojure
  (t2/select (keyword "models" (dc/pluralize (name attribute)))
             (get instance (keyword (str (name attribute) "-id"))))
  ```
- Impact: Called per-instance during hydration, could lead to N+1 queries
- Recommendation: Document that Toucan2's `batched-hydrate` should be preferred for collections

---

## 6. Testing Improvements

### Test Coverage

**Missing tests for error paths**

- Most tests cover happy paths. Notable gaps:
  - Template resolution failures
  - Error handling in interceptors
  - Edge cases in language utilities (empty strings, nil values)

### Test Helpers

**Issue: Repeated test setup**

- Location: `src/main/tram/associations_test.clj:10-72`
- Current: Significant setup code
- Note: Good use of fixtures, but consider extracting helper functions for common setup patterns

### Test Organization

**Issue: Comment blocks in tests**

- Location: `test/main/tram/associations_test.clj:74-100`
- Problem: Contains commented REPL code
- Recommendation: Move to separate `dev/scratch.clj` file or remove

---

## 7. Configuration & Maintainability

### Configuration Patterns

**Issue: Environment detection documentation**

- Location: `src/bb_compatible/tram/tram_config.clj`
- Problem: Reads TRAM_ENV from system properties without clear documentation
- Recommendation: Document this clearly and consider validating the value

### Dependency Management

**Issue: Custom forks without clear documentation**

- Location: `deps.edn:42-44, 78-79`
- Custom git deps:
  - `io.github.bolivier/huff` - Comment says "using my own version unti malli is upgraded upstream" (typo: "unti" → "until")
  - `io.github.bolivier/declensia` - No explanation provided
- Recommendation: Track upstream issues, document why forks are needed

### Code Generation

**Issue: Acknowledged formatting issues**

- Location: `src/main/tram/db.clj:192`
- Comment: "Currently the autocreated formatting is sloppy, but it is mostly right."
- Recommendation: If this is important, consider using `rewrite-clj` more extensively or zprint templates

---

## 8. Type Safety & Validation

### Schema Definitions

**Issue: Unused schemas**

- Location: `src/main/tram/impl/router.clj:36-61`
- Problem: Defines Malli schemas but they're not consistently used for validation
- Recommendation: Either validate route data with these schemas OR remove them if they're just documentation

### Dynamic Typing Risks

**Issue: Runtime type checks**

- Location: `src/main/tram/impl/router.clj:95-103`
- Current: Handler entry coercion uses runtime type checks
- Note: This is necessary in Clojure, but consider using Malli schemas for better error messages at route definition time

---

## 9. Minor Cleanups

### Unused Code

**Issue: Undefined spec reference**

- Location: `src/main/tram/routes.clj:250`
- Problem: References `::spec` but it's not defined

### Formatting

**Issue: Inconsistent blank lines**

- Problem: Some namespaces have multiple blank lines between functions, others have one
- Recommendation: Run zprint consistently across the codebase

### Comments

**Issue: Typo in comment**

- Location: `src/main/tram/language.clj:10`
- Current: "databaes lang utils"
- Fix: "database lang utils"

---

## Summary Priority Recommendations

### High Priority

1. **Fix global `*associations*` pattern** - Makes testing difficult and state management unclear
2. **Split db.clj** - Too many responsibilities in one namespace
3. **Consistent error handling** - Establish patterns for the framework
4. **Fix namespace naming** - `tram_cli` → `tram-cli`
5. **Document bb_compatible vs main split** - Clarify architecture

### Medium Priority

6. Add missing docstrings to public API functions
7. Extract magic constants (HTTP codes, paths)
8. Move protocol extensions to explicit namespace
9. Improve error messages (especially "broke" and template errors)
10. Address the template renderer TODO or remove it

### Low Priority

11. Performance optimizations (memoization, walk strategies)
12. Test coverage for error paths
13. Clean up test comments and REPL code
14. Consolidate string operations
15. Formatting consistency

---

## Conclusion

The Tram codebase is well-structured overall with thoughtful architectural decisions. These suggestions are refinements that would improve maintainability, clarity, and robustness. The framework shows promise as a Rails-inspired solution for Clojure web development.

Consider addressing high-priority items first as they have the most significant impact on code maintainability and developer experience.
