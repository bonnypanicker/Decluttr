# Privacy Policy for Decluttr

Last updated: April 5, 2026

Decluttr ("we", "our", "us") helps you review installed apps, archive app context, and uninstall apps more safely. This Privacy Policy explains what data we process, why, and your choices.

By using Decluttr, you agree to this policy.

## 1) Data Controller and Contact

- App: Decluttr
- Contact for privacy questions or data requests: bonnypanicker@outlook.com

## 2) Information We Process

We process different categories of data depending on which features you use.

### A) Account and authentication data (if you sign in)

- Email address
- Authentication credentials (handled by Firebase Authentication)
- Authentication state (signed in/signed out)

Purpose:
- Sign-in, account access, and sync association with your user account.

### B) App inventory and device-app metadata

To power discovery, recommendation, archive, and uninstall flows, Decluttr reads app-level metadata from your device, including:

- Package name (app ID)
- App display name
- Install source signal (for example, Play Store-installed vs sideloaded)
- APK/source file size (used to estimate storage impact)
- Category (when available from Android package metadata)
- App icon snapshot (for archived records)
- Last used timestamp (only when Usage Access is granted)

Purpose:
- Show installed apps, identify rarely used apps, estimate storage that can be freed, archive app context, and support archive UI.

### C) Archive content you create or edit

- Notes
- Tags
- Folder names/grouping
- Archive timestamps and update timestamps
- Archived app size snapshot

Purpose:
- Organize and restore context for apps you archived.

### D) Data shared into Decluttr through Android Share

If you share text (for example a Play Store link) to Decluttr, we process:
- Shared text payload
- Extracted package ID from Play Store URLs

Purpose:
- Archive an app from a shared Play Store link.

### E) Diagnostics and stability data

- Crash and exception reports (via Firebase Crashlytics)
- Local startup debug log in app cache (`startup_debug.log`) for troubleshooting

Purpose:
- Diagnose crashes, improve stability, and fix defects.

### F) Analytics/measurement data

Decluttr includes Firebase Analytics SDK. Depending on your Firebase/Google settings and platform behavior, analytics events and device/app instance metadata may be processed by Google Firebase.

Purpose:
- Product measurement and quality improvements.

## 3) Permissions We Use

Decluttr requests the following Android permissions/features:

- `android.permission.QUERY_ALL_PACKAGES`
  - Why: Decluttr needs broad app inventory visibility to let you review installed apps and select apps for archive/uninstall workflows.
  - Effect if denied/restricted by policy: Discovery and app-list functionality may not work as intended.

- `android.permission.PACKAGE_USAGE_STATS` (Usage Access; user-controlled in system settings)
  - Why: Identify rarely used apps for recommendations.
  - Optional: Yes. Core archive features can still work without it.

- `android.permission.REQUEST_DELETE_PACKAGES`
  - Why: Launch Android uninstall flow when you choose to uninstall apps.
  - Optional: Used only when you explicitly trigger uninstall actions.

- `android.permission.INTERNET`
  - Why: Firebase Authentication, Firestore sync, Crashlytics, Analytics, and external links.

## 4) Where Data Is Stored

### Local on-device storage

Decluttr stores archive records in a local Room database and local preferences/files. Data can include package IDs, names, notes, tags, folder names, icon bytes, and archive metadata.

### Cloud storage (only when signed in)

If you sign in, archive records are synced to Firebase Firestore under your account (user UID namespace). Synced fields may include:

- packageId
- name
- icon (base64-encoded, compressed)
- archivedSizeBytes
- category
- tags
- notes
- archivedAt
- isPlayStoreInstalled
- lastTimeUsed
- folderName
- lastModified

If you are not signed in, cloud sync is not used.

## 5) Backups and Device Transfer

Decluttr is configured to allow Android backup/device-transfer of app data (shared preferences, database, and app files), subject to your Android/Google backup settings.

## 6) How We Use Information

We use data only for legitimate app operations, including:

- Core app functionality (discover, archive, uninstall flows)
- Cloud sync across sessions/devices for signed-in users
- User experience features (folders, notes, archive restoration context)
- Safety, diagnostics, and reliability improvements
- Product analytics/measurement

We do not sell your personal data.

## 7) Sharing and Third Parties

We share/process data with service providers only as needed to operate Decluttr:

- Google Firebase Authentication
- Google Firebase Firestore
- Google Firebase Crashlytics
- Google Firebase Analytics

We may also share data when required by law, legal process, or to protect rights/safety.

## 8) Data Retention

- Local archive data is retained until you delete archive entries, uninstall app data, or otherwise clear app storage.
- On sign-out, local archived data is cleared from device storage by app logic.
- Cloud archive data remains in your signed-in account space unless removed (for example by deleting archived entries while signed in) or via an account/data deletion request.
- Crash/analytics retention is governed by Firebase service retention policies.

## 9) Your Choices and Controls

You can:

- Use core features without granting Usage Access (with reduced recommendations)
- Control uninstall actions (always user-initiated via Android uninstall flow)
- Edit/delete archived items
- Export/import archive data
- Sign out of your account
- Revoke permissions in Android Settings

For data deletion/access requests, contact: bonnypanicker@outlook.com

## 10) Security

We use reasonable safeguards, including:

- TLS/HTTPS transport for network communication
- Android network security configuration disallowing cleartext traffic
- Platform and Firebase security controls

No method of storage or transmission is 100% secure.

## 11) Children’s Privacy

Decluttr is not directed to children under 13, and we do not knowingly collect personal data from children under 13. If you believe a child has provided personal data, contact us for removal.

## 12) International Data Transfers

If you use cloud features, your data may be processed in countries where Google/Firebase infrastructure operates. Applicable safeguards are provided through Google service terms and infrastructure controls.

## 13) Changes to This Policy

We may update this policy over time. We will update the "Last updated" date above when changes are made.

## 14) Google Play Data Safety Alignment Notes

Depending on feature usage, Decluttr may process:

- Personal info: email address (if signed in)
- App activity/app info: installed app metadata, package IDs, usage-derived timestamps (if granted), archive notes/tags/folders
- Diagnostics: crash reports
- Optional analytics/measurement data via Firebase Analytics

Data processing varies by your choices (sign-in, permissions, and feature usage).
