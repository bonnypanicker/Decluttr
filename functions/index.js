const crypto = require("crypto");
const admin = require("firebase-admin");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {google} = require("googleapis");

admin.initializeApp();

const PLAY_SCOPE = ["https://www.googleapis.com/auth/androidpublisher"];

exports.verifyPremiumPurchase = onCall(
  {
    region: "asia-south1",
    enforceAppCheck: false,
  },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "User must be authenticated.");
    }

    const data = request.data || {};
    const purchaseToken = String(data.purchaseToken || "").trim();
    const productId = String(data.productId || "").trim();
    const packageName = String(data.packageName || "").trim();

    if (!purchaseToken || !productId || !packageName) {
      throw new HttpsError(
          "invalid-argument",
          "purchaseToken, productId, and packageName are required.",
      );
    }

    try {
      const auth = new google.auth.GoogleAuth({scopes: PLAY_SCOPE});
      const androidpublisher = google.androidpublisher({
        version: "v3",
        auth,
      });

      const purchaseResponse = await androidpublisher.purchases.products.get({
        packageName,
        productId,
        token: purchaseToken,
      });

      const purchaseData = purchaseResponse.data || {};
      const purchaseState = Number(purchaseData.purchaseState ?? -1);
      if (purchaseState !== 0) {
        const now = Date.now();
        const purchaseTokenHash = crypto
            .createHash("sha256")
            .update(purchaseToken)
            .digest("hex");

        await admin
            .firestore()
            .collection("users")
            .doc(request.auth.uid)
            .collection("entitlements")
            .doc("archive")
            .set(
                {
                  isPremium: false,
                  productId,
                  source: "PLAY_BILLING",
                  purchaseTokenHash,
                  verifiedAt: now,
                  revokedAt: now,
                  revocationReason: `purchaseState=${purchaseState}`,
                  updatedAt: admin.firestore.FieldValue.serverTimestamp(),
                },
                {merge: true},
            );

        return {
          verified: false,
          isPremium: false,
          message: `Invalid purchase state: ${purchaseState}`,
          verifiedAt: now,
          productId,
        };
      }

      const acknowledgementState = Number(purchaseData.acknowledgementState ?? 0);
      if (acknowledgementState !== 1) {
        await androidpublisher.purchases.products.acknowledge({
          packageName,
          productId,
          token: purchaseToken,
          requestBody: {
            developerPayload: "decluttr_verify_premium",
          },
        });
      }

      const now = Date.now();
      const purchaseTokenHash = crypto
          .createHash("sha256")
          .update(purchaseToken)
          .digest("hex");

      await admin
          .firestore()
          .collection("users")
          .doc(request.auth.uid)
          .collection("entitlements")
          .doc("archive")
          .set(
              {
                isPremium: true,
                planType: "ONE_TIME",
                productId,
                source: "PLAY_BILLING",
                purchaseTokenHash,
                verifiedAt: now,
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              },
              {merge: true},
          );

      return {
        verified: true,
        isPremium: true,
        message: "Purchase verified.",
        verifiedAt: now,
        productId,
      };
    } catch (error) {
      console.error("verifyPremiumPurchase failed", error);
      throw new HttpsError(
          "internal",
          "Purchase verification failed. Please retry in a moment.",
      );
    }
  },
);

exports.revokePremiumEntitlement = onCall(
  {
    region: "asia-south1",
    enforceAppCheck: false,
  },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "User must be authenticated.");
    }

    const now = Date.now();
    await admin
        .firestore()
        .collection("users")
        .doc(request.auth.uid)
        .collection("entitlements")
        .doc("archive")
        .set(
            {
              isPremium: false,
              revokedAt: now,
              revocationReason: "client_restore_no_owned_purchase",
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            },
            {merge: true},
        );

    return {ok: true, revokedAt: now};
  },
);
