# Spec Delta

## Purpose

Defines the first public download experience and verifiable release assets for users installing the desktop application on Windows.

## ADDED Requirements

### Requirement: User-controlled updates and repository access
About SHALL show the installed version and a link to the public GitHub repository. The app SHALL check for updates only on user request, compare numeric semantic versions, and offer only a newer stable Windows release from this repository. Offline, rate-limited, malformed, and missing-asset responses SHALL show a recoverable explanation without affecting the timer.

#### Scenario: Check an installed current version
- **WHEN** the latest stable version is no newer than the running app
- **THEN** the app reports it is up to date and offers no downgrade

#### Scenario: Open repository
- **WHEN** the user activates the repository link in About
- **THEN** the fixed public project URL opens in the default browser or a visible browser error is shown

### Requirement: Verified download and deliberate installation
The update flow SHALL show download progress, support cancellation and retry, validate the exact versioned MSI against its SHA-256 checksum, and reject unexpected download locations or malformed release metadata. Installation SHALL require explicit user confirmation. Before opening the interactive Windows installer, the app SHALL reverify the file, save and back up local state, and keep persistence usable if preparation or launch fails. Windows packages SHALL preserve the published product UpgradeCode.

#### Scenario: Cancel or corrupt download
- **WHEN** the user cancels a download or its size/hash is invalid
- **THEN** partial data cannot be installed and the UI offers a retry

#### Scenario: Install a verified update
- **WHEN** the user confirms installation of a verified newer release
- **THEN** the app saves and backs up its local state, launches the Windows installer, and exits with installation/relaunch under the user's control

#### Scenario: Install preparation fails
- **WHEN** saving, backup, checksum verification, or installer launch fails
- **THEN** the app stays open, explains the failure, and preserves timer and persistence operation

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
