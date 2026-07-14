# Shared clj-kondo runner. Prefers a native clj-kondo binary (fast); otherwise
# falls back to the :clj-kondo deps alias so no manual install is required.
run_kondo() {
  if command -v clj-kondo >/dev/null 2>&1; then
    clj-kondo "$@"
  else
    echo "clj-kondo not found on PATH; using 'clojure -M:clj-kondo' (first run downloads it)." >&2
    echo "For faster linting, install the native binary: https://github.com/clj-kondo/clj-kondo/blob/master/doc/install.md" >&2
    clojure -M:clj-kondo "$@"
  fi
}
