# Add Premium Subscription and Archive Limit to Decluttr

This plan details the implementation of a premium subscription model for the Decluttr app. The free tier will be limited to 50 cloud-archived apps, and a premium tier (₹99) will unlock unlimited archiving. This will include UI enhancements like a credits bar, a purchase icon, and a professional payment gateway integration.

> [!WARNING] 
> **Important Legal and Compliance Note:** Since this is an Android app distributed via the Google Play Store, and the purchase unlocks a *digital feature* (unlimited app archiving), **Google Play Billing is mandatory**. Using third-party payment gateways like Razorpay, Stripe, or Paytm for this specific digital purchase will result in the app being rejected or banned per Google Play's standard policy. We must use the Google Play Billing Library.

---

## User Review Required

- **Google Play Developer Console Setup**: You will need to create an In-App Product (One-time purchase or Subscription) in your console priced at ₹99.
- **Privacy Policy and Terms of Service**: These must be hosted on a live, publicly accessible website. Users must agree to these before or during checkout. Please confirm if you have a website or GitHub pages instance to host these.
- **Backend Verification**: To prevent piracy or spoofed purchases, the best practice is to verify the Google Play purchase token via a secure backend (e.g., Firebase Cloud Functions). Please confirm if we can add a simple Firebase Cloud Function for this.

---

## Proposed Changes

### Domain & Data Layer (Billing Integration)
To properly handle app limits and purchases, we need a billing integration using the Google Play Billing Library.

#### [NEW] `com.tool.decluttr.data.repository.BillingRepositoryImpl.kt`
- Implements `BillingClientStateListener`, `PurchasesUpdatedListener`.
- Connects to `BillingClient`.
- Queries for the ₹99 product details and handles the purchase flow.
- Verifies the purchase and persists the premium state in the local DB/Preferences and optionally synchronizes the user's `isPremium` status with Firebase Firestore.

#### [NEW] `com.tool.decluttr.domain.repository.BillingRepository.kt`
- Interfaces for `isPremiumUser: Flow<Boolean>`, `buyPremium()`, and `getPremiumPrice()`.

#### [MODIFY] `com.tool.decluttr.domain.usecase.ArchiveAndUninstallUseCase.kt`
- Inject the `AppRepository` and `BillingRepository`.
- Before adding apps to the archive, check the current count in the repository against the user's tier.
- If `!isPremium` and current cloud apps count + new requested apps > 50, throw an `ArchiveLimitExceededException` (or return an error result) instead of archiving.

---

### Presentation Layer (UI Updates)
Enhance the existing Archiving view and present the Pay Page modal.

#### [MODIFY] `com.tool.decluttr.presentation.screens.dashboard.ArchiveFragment.kt`
- Add an observe on the `BillingRepository` to see the current credits (`X/50`) and user tier.
- Add an observer on the limits so archiving fails gracefully with a prompt to upgrade when trying to archive the 51st app.

#### [MODIFY] `res/layout/fragment_archive.xml`
- **Purchase Icon**: Add a small Crown or "PRO" icon at the top right of the archive page, next to the navigation bar. 
- **Credits Bar**: Add a small horizontal `ProgressBar` and `TextView` in the category bar section displaying "10/50 Archives Used". If the user is premium, hide this or display "Unlimited".

#### [NEW] `com.tool.decluttr.presentation.screens.paywall.PaywallBottomSheet.kt`
- A sleek, professional bottom sheet dialog serving as the "Pay Page".
- **Benefits Section**: Features a list of Premium benefits (e.g., Unlimited Cloud Saves, Priority Support, No Limits).
- **Pay Button**: "Unlock Unlimited for ₹99".
- **Legalities Footnotes**: Links for "Terms & Conditions" and "Privacy Policy" at the bottom of the paywall modal, styled as small hyperlinked text.

#### [NEW] `res/layout/bottom_sheet_paywall.xml`
- Modern layout using Material Design 3 guidelines (glassmorphism/surface colors, appealing typography and gradients) for the paywall.

---

## Payment Gateway Implementation Strategy

As stated in the Legalities section, we will implement **Google Play Billing**.

1. **Add Dependency**: `implementation("com.android.billingclient:billing:6.1.0")` to `app/build.gradle.kts`.
2. **Setup In-App Products**: Register a one-time product ID (e.g., `premium_unlimited_archiving`) in Google Play Console.
3. **App Initialization**: When the app starts, initialize `BillingClient` and query the user's active purchases to restore `isPremium` status if they log in from a different device.
4. **Purchase Flow**: When the user clicks the "Pay ₹99" button on the Paywall:
   - Call `billingClient.launchBillingFlow(activity, billingFlowParams)`.
   - The OS takes over and presents the Google Play standard purchase overlay.
   - On success, `onPurchasesUpdated` triggers.
   - We **Acknowledge** the purchase via `billingClient.acknowledgePurchase(...)` and mark the user as a Premium member in Firestore (`users/{uid}/isPremium: true`).

## Open Questions

1. **Authentication State**: If a user is not signed in to Firebase, should the 50 app limit still apply locally? Our current design syncs local + remote.
2. **Firebase Cloud Functions**: Would you like to implement backend purchase validation using Firebase Cloud Functions (recommended for production security) or stick to standard client-side acknowledgement for the MVP?
3. **Legal Hosting**: Do you have a domain to host the T&C and Privacy Policy HTML pages, or should I generate template markdown files for you to host?

## Verification Plan

### Automated/Unit Tests
- Stub `BillingClient` to return success and test `BillingRepositoryImpl` acknowledging flow.
- Mock the limit (`49 apps`) and verify the 51st app throws a UI error and shows the Paywall.

### Manual Verification
- Add test accounts to the Google Play Console license testers list.
- Run the app, click the crown icon -> opens Paywall.
- Click Pay -> Completes without real charges via Play Store Test card.
- Verify the "Credits Bar" disappears and state changes to Premium.
- Verify attempting to archive 50 apps works, but 51 apps shows Paywall.
