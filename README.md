ShreddR 🗑️

Forensic-Grade Secure File Deletion Utility

ShreddR is a desktop application built with Java 17 LTS, Spring Boot, and JavaFX. It goes beyond standard file deletion by using multi-pass overwrite algorithms to ensure that sensitive data is permanently destroyed and unrecoverable by forensic tools.

🚀 Features

Phase 1: The Core Engine (Current Release)

Drag-and-Drop Interface: Easily select files or folders for destruction.

Forensic Erasure: Implements a robust 3-pass overwrite strategy:

Pass 1: Overwrite with Zeros (0x00).

Pass 2: Overwrite with Ones (0xFF).

Pass 3: Overwrite with Cryptographically Secure Pseudo-Random Numbers (CSPRNG).

Metadata Obfuscation: Renames files to random UUIDs before unlinking to hide file names from filesystem logs.

Non-Blocking I/O: Heavy operations run on background threads to keep the UI responsive.

Phase 2: Secure System Cleaner (Roadmap)

Automated Cache Detection: Scans for sensitive cache files in:

Browsers: Chrome, Firefox, Edge (History, Cookies, Cache).

Dev Tools: VS Code, IntelliJ, Docker.

System: Windows Temp, Prefetch, Recycle Bin.

Smart Locking: Detects if target applications are open to prevent file lock errors.

🛠️ Technology Stack

Language: Java 17 LTS (Selected for enterprise stability).

Core Framework: Spring Boot 3.4.x (Dependency Injection & Service Management).

GUI: JavaFX 21.x LTS.

Monitoring: Sentry (Error tracking and performance monitoring).

Build Tool: Apache Maven.

⚡ Getting Started

Prerequisites

JDK 17 installed.

Maven installed and added to PATH.

Installation & Build

Clone the repository

git clone [https://github.com/yourusername/shreddr.git](https://github.com/yourusername/shreddr.git)
cd shreddr


Run in Development Mode

mvn spring-boot:run


Build Executable (.exe)
To create a standalone installer that bundles the Java runtime:

mvn package


The output installer will be located in the target/ directory.

⚠️ Data Destruction Warning

Please Use With Caution.

Files processed by ShreddR are permanently destroyed. Unlike the Windows Recycle Bin, there is no "Restore" function. Once the shredding process begins, the data is overwritten at the binary level and cannot be recovered by data recovery software.

🤝 Contributing

Contributions are welcome! Please look at the Issues tab for Phase 2 tasks regarding cache detection logic.

📄 License

Distributed under the MIT License. See LICENSE for more information.