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

### Requirement: Version-driven publication
A push to `main` SHALL check the application version and publish a Windows release when that version has no existing tag or release, after all release validation succeeds. A push without a new version SHALL not publish another release. The published tag SHALL identify the commit that passed validation.

#### Scenario: New version on main
- **WHEN** `main` contains a new semantic version and its release checks pass
- **THEN** a matching `vMAJOR.MINOR.PATCH` release appears with the MSI, checksum, MIT License, and that version's notes

#### Scenario: Ordinary push
- **WHEN** a push to `main` retains an already published version
- **THEN** no new tag or release is created

#### Scenario: Conflicting tag
- **WHEN** the version tag already exists without a matching published release
- **THEN** automation stops rather than moving that tag to a different commit

### Requirement: Clear platform scope
The public project documentation SHALL identify Windows as the first supported download platform and describe source-build support separately from tested release support.

#### Scenario: Non-Windows visitor
- **WHEN** a visitor reads the download instructions from macOS or Linux
- **THEN** the page states that no native package is currently published for that platform

### Requirement: Clear MIT terms
The repository, public release assets, and Windows installer SHALL include the MIT License with its copyright notice. Documentation SHALL identify the project as open source and state that the license permits commercial use when its notice condition is followed.

#### Scenario: Commercial user reviews terms
- **WHEN** a prospective commercial user reads the repository or release page
- **THEN** they can identify the MIT permission to use the software commercially and the obligation to retain the copyright and permission notices
