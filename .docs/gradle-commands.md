# Useful Gradle Commands

Run all commands from the project root. Use `./gradlew` (not `gradle`) to use the project's bundled Gradle version.

## Build & Run

| Command | Description |
|---------|-------------|
| `./gradlew bootRun` | Start the Spring Boot app locally |
| `./gradlew build` | Compile, run tests, and produce the JAR |
| `./gradlew clean` | Delete the `build/` directory |
| `./gradlew clean build` | Full rebuild from scratch |

## Testing

| Command | Description |
|---------|-------------|
| `./gradlew test` | Run all tests |
| `./gradlew test --tests "com.prachaudhari.ledgr.SomeTest"` | Run a specific test class |
| `./gradlew test --tests "*.SomeTest.methodName"` | Run a single test method |

## Dependencies

| Command | Description |
|---------|-------------|
| `./gradlew dependencies` | Print the full dependency tree |
| `./gradlew dependencies --configuration runtimeClasspath` | Show only runtime dependencies |
| `./gradlew dependencyInsight --dependency spring-boot` | Find where a specific dependency comes from |

## Packaging

| Command | Description |
|---------|-------------|
| `./gradlew bootJar` | Build an executable fat JAR (includes all dependencies) |
| `./gradlew jar` | Build a plain JAR (no embedded server) |

## Info & Debugging

| Command | Description |
|---------|-------------|
| `./gradlew tasks` | List all available tasks |
| `./gradlew bootRun --debug-jvm` | Start the app with remote debug on port 5005 |
| `./gradlew build --scan` | Generate a build scan for performance analysis |