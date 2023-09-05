# kotlin-ice-adapter

A structural rewrite of the [java-ice-adapter](https://github.com/FAForever/java-ice-adapter) in Kotlin (on the JVM). It still follows the same
protocol and uses the same ice4j library as the original.

This implementation focuses on
* component-oriented architecture with strict boundaries
* testability (unit- & integration tests)
* safety from race-conditions by emphasizing synchronization, null-safety and immutability
