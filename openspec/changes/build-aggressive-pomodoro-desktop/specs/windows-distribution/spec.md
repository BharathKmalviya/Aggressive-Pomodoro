# Spec Delta

## Purpose

Defines the first public download experience and verifiable release assets for users installing the desktop application on Windows.

## ADDED Requirements

### Requirement: Windows release artifacts
Each public Windows release SHALL provide a versioned installer that includes the runtime needed to launch the application, a checksum file for the installer, release notes, and clear download and installation instructions. Users SHALL NOT need to install a separate Java runtime.

#### Scenario: Clean Windows installation
- **WHEN** a user installs a published Windows release on a supported clean Windows system
- **THEN** the app launches without a separate Java installation and reports the release version

#### Scenario: Verify download
- **WHEN** a user compares the installer to its published checksum
- **THEN** the checksum file identifies the exact versioned installer and allows integrity verification

### Requirement: Release validation
The release process SHALL build and check the application and its installer on Windows before publishing, and SHALL keep release assets tied to an immutable version tag. An unsuccessful validation SHALL prevent publication.

#### Scenario: Packaging failure
- **WHEN** the Windows package job fails
- **THEN** no release with that version is published as a successful downloadable release

### Requirement: Clear platform scope
The public project documentation SHALL identify Windows as the first supported download platform and describe source-build support separately from tested release support.

#### Scenario: Non-Windows visitor
- **WHEN** a visitor reads the download instructions from macOS or Linux
- **THEN** the page states that no native package is currently published for that platform

### Requirement: Clear source-available terms
The public release and repository SHALL display the applicable noncommercial license terms, preserve the licensor's required notice, and provide a public route for requesting separate commercial permission. The project SHALL describe itself as source available rather than open source while commercial use requires permission.

#### Scenario: Commercial user reviews terms
- **WHEN** a prospective commercial user reads the repository or release page
- **THEN** they can identify the noncommercial terms and how to request commercial permission before using the software commercially
