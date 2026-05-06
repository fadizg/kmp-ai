// SPM wrapper target — its only job is to declare a dependency on both
// the KmpAI binary target (Swift bindings) and the llama binary target
// (the underlying llama.cpp dylib that KmpAI links against at runtime).
//
// Consumers depend on `.product(name: "KmpAI", package: "kmp-ai")` and
// transitively get llama.framework embedded in their .app bundle.
//
// `import KmpAI` in consumer code resolves to the binary target's module,
// not this file — this wrapper deliberately re-exports nothing.
@_exported import KmpAI
