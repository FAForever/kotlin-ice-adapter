**Deprecation notice:** Due to the limitations and issues we found with ice4j we switched to the Pion library which is more actively maintained and its focus goes beyond a plain Jitsi support library.

The new repository is here: https://github.com/FAForever/faf-pioneer

# kotlin-ice-adapter

A structural rewrite of the [java-ice-adapter](https://github.com/FAForever/java-ice-adapter) in Kotlin (on the JVM). It still follows the same
protocol and uses the same ice4j library as the original.

This implementation focuses on
* component-oriented architecture with strict boundaries
* testability (unit- & integration tests)
* safety from race-conditions by emphasizing synchronization, null-safety and immutability
