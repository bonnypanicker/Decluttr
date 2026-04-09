# Decluttr Firebase Functions

## verifyPremiumPurchase

Callable function used by the Android app to verify one-time Google Play purchases and grant premium entitlement.

### Inputs
- `purchaseToken` (string)
- `productId` (string)
- `packageName` (string)

### Output
- `verified` (boolean)
- `isPremium` (boolean)
- `message` (string)
- `verifiedAt` (number, epoch millis)
- `productId` (string)

### Firestore write
`users/{uid}/entitlements/archive`

### Prerequisites
- Android Publisher API enabled.
- Service account has Play Developer API access.
