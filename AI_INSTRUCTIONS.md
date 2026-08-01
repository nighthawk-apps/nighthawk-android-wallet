# AI Coding Instructions

When generating or editing Kotlin and Android code in this repository, strictly adhere to the following rules:

1. **Comments**: Never add inline comments for obvious logic. Always use a new line for comments when necessary.
2. **KDoc**: Use the standard KDoc `/** ... */` format for classes and public functions, instead of single-line comments.
3. **Strings**: Prefer Kotlin string templates (`"Value is $value"`) over string concatenation.
4. **Imports**: Keep imports clean; group standard library imports first, followed by third-party libraries, then local project packages.
5. **Types**: Rely on Kotlin type inference where obvious, but always specify return types for public API methods and complex functions.
6. **Nullability**: Prefer using safe calls (`?.`) and the elvis operator (`?:`) instead of explicit null checks where idiomatic.
7. **Scope Functions**: Use Kotlin scope functions (`let`, `apply`, `run`, `also`, `with`) idiomatically but avoid deep nesting to maintain readability.
8. **Immutability**: Prefer `val` over `var` whenever possible. Use immutable collections (e.g., `listOf`, `mapOf`) unless mutability is explicitly required.
