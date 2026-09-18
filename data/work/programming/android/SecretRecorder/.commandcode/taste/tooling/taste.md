# Tooling Preferences

- Uses pnpm as the package manager. Confidence: 0.85
- Prefers MCP server tools over CLI or search tools. Confidence: 0.85
- When investigating library/API behavior, uses the Context7 MCP server for documentation reading and pairs it with web search rather than relying on local source alone. Confidence: 0.95
- Prefers official, industry-standard testing libraries (JUnit, Mockito, Robolectric, AndroidX Test/Espresso) over custom or reinvented test infrastructure — "don't reinvent anything." Confidence: 0.9
- Considers instrumented tests running on a real device better than JVM-only tests for hardware-dependent behavior (GPS/sensor feeds); pairs them with fast JVM unit tests for pure logic. But when device tests need manual setup (live GPS fix, skip indoors/CI) and the logic is already deterministically covered by JVM tests, prefers deleting the device tests entirely rather than keeping them. Confidence: 0.7
- Prefers deterministic, repeatable tests (e.g., injected/mock GPS fixes) over assertions that depend on real-world variability such as an actual GPS fix or movement; skeptical that real-device conditions are reliable enough for perfect tests. Confidence: 0.8
- Treats the Android Gradle Plugin (AGP) as pinned: explicitly instructs to apply dependency version updates but NOT to upgrade AGP, and accepts that dependencies requiring a newer compileSdk than the pinned AGP supports must be skipped or reverted rather than forcing the AGP bump. Confidence: 0.95
