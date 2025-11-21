
# ShreddR 🗑️

A secure file deletion utility built with Java 25, Spring Boot and JavaFX. Permanently erases sensitive files by overwriting data patterns before deletion to reduce recoverability.

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Packaging](#packaging)
- [Project Structure](#project-structure)
- [Warning](#warning)
- [License](#license)

## Features

- Multi-pass overwriting algorithms: Zeros, Ones, Random Data
- Modern JavaFX GUI integrated with Spring Boot
- Non-blocking I/O: long-running tasks run on background threads
- Native packaging support for standalone installers

## Tech Stack

- Java 25
- Spring Boot 3.4
- JavaFX 23
- Maven

## Prerequisites

- JDK 23 or 25 installed and configured on `PATH`
- Maven installed and added to `PATH`

## Getting Started

1. Clone the repository
    git clone https://github.com/yourusername/shreddr.git
    cd shreddr

2. Build the project
    mvn clean install

3. Run in development mode
    mvn spring-boot:run

Note: Ensure `pom.xml` configures JavaFX modules correctly for runtime.

## Packaging

Create a standalone installer (bundles a JRE so end-users don't need Java):

- Use a JDK that provides `jpackage` (JDK 17+).
- Build the package:
    mvn package

Find the generated installer in `target/dist` or `target/jpackage` depending on plugin configuration.

## Project Structure

`src/main/java/com/shreddr/`
- `ShreddRApplication.java` — Entry point (JavaFX + Spring Boot)
- `StageInitializer.java` — Loads UI on app start
- `StageReadyEvent.java` — Custom event for Stage availability
- `controller/`
  - `MainController.java` — UI logic (Spring injected)

## Warning

Files deleted with ShreddR are permanently destroyed and cannot be recovered. Use with extreme caution.

## License

MIT
