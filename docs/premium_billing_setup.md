# Premium Billing Setup (Production)

## 1) Google Play Console

1. Open **Monetize > Products > In-app products**.
2. Create one-time product:
   - Product ID: `decluttr_premium_upgrade`
   - Price: `INR 99`
   - Type: non-consumable (one-time unlock)
3. Add test accounts in **License testing**.
4. Publish product (at least to internal testing track).

## 2) Firebase Functions deployment

1. Install dependencies:
   - `cd functions`
   - `npm install`
2. Ensure service account used by Functions has Android Publisher API access.
3. Deploy:
   - `firebase deploy --only functions:verifyPremiumPurchase`

## 3) Firestore security notes

- Entitlement documents are stored at:
  - `users/{uid}/entitlements/archive`
- Write path should only be trusted server-side (Cloud Function).
- App reads entitlement; server verifies purchase token.

## 4) Hosting legal pages

1. Keep legal pages in `public/`.
2. Deploy:
   - `firebase deploy --only hosting`
3. Confirm:
   - `https://decluttr-3c299.web.app/privacy-policy.html`
   - `https://decluttr-3c299.web.app/terms-and-conditions.html`

## 5) Release checklist

- Verify purchase on clean install and restored install.
- Verify free-plan limit at `50/50`.
- Verify paywall opens on limit hit and from archive/settings.
- Verify legal links open from settings and paywall.
- Confirm GitHub Actions release signing secrets (`RELEASE_*`) are configured before shipping AABs.
- Keep the release upload keystore backed up securely (password manager + offline copy).
