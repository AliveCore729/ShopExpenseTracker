const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// 1. Send Notification when Money is Received
exports.sendTransferNotification = functions.firestore
  .document("user_transfers/{transferId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const toUserId = data.toUserId;
    const fromName = data.fromUserName;
    const amount = data.amount;

    // 1. Get the Receiver's Token
    const userDoc = await admin.firestore().collection("users").doc(toUserId).get();
    const fcmToken = userDoc.data().fcmToken;

    if (!fcmToken) {
      console.log("No token found for user:", toUserId);
      return;
    }

    // 2. Create the Message
    const payload = {
      notification: {
        title: "Money Received! 💰",
        body: `${fromName} sent you ₹${amount}`,
      },
      token: fcmToken,
    };

    // 3. Send it
    return admin.messaging().send(payload);
  });

// 2. Send Notification when Grocery Item is Added
exports.sendGroceryNotification = functions.firestore
  .document("grocery_items/{itemId}")
  .onCreate((snap, context) => {
    const data = snap.data();
    const itemName = data.name;
    const addedBy = data.addedBy;

    // Send to "grocery_updates" topic (everyone subscribes to this)
    const payload = {
      notification: {
        title: "Grocery List Updated 🛒",
        body: `${addedBy} added: ${itemName}`,
      },
      topic: "grocery_updates",
    };

    return admin.messaging().send(payload);
  });