# Shared zprint runner. Prefers a native zprint binary (fast); otherwise falls
# back to the copy Tram already puts on the classpath, so no manual install is
# required.
run_zprint() {
  if command -v zprint >/dev/null 2>&1; then
    zprint "$@"
  else
    echo "zprint not found on PATH; using 'clojure -M -m zprint.main' (slower to start)." >&2
    echo "For faster formatting, install the native binary: https://github.com/kkinnear/zprint/blob/main/doc/getting/README.md" >&2
    clojure -M -m zprint.main "$@"
  fi
}

# Every Clojure and EDN source file zprint owns, as a newline-separated list.
zprint_targets() {
  for dir in src test dev bin/dev; do
    [ -d "$dir" ] && find "$dir" -type f \( -name '*.clj' -o -name '*.cljc' \)
  done
  for file in build.clj deps.edn tests.edn tram.edn; do
    [ -f "$file" ] && echo "$file"
  done
}
