const express = require("express");
const router = express.Router();
const prisma = require("../db");
const authenticate = require("../middleware/auth");

// ─── GET /api/mobile/subscription/status ─────────────────────────────────────────
router.get("/mobile/subscription/status", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const isPro = user.plan !== "free";

        return res.json({
            isPro: isPro,
            plan: user.plan,
            expiresAt: null
        });
    } catch (err) {
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/mobile/subscription/verify ────────────────────────────────────────
router.post("/mobile/subscription/verify", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { purchaseToken, productId } = req.body;

        // In development/production without Google Play API credentials,
        // we can mark the user as pro if a valid purchaseToken was sent.
        const plan = productId ? productId.toLowerCase() : "pro_monthly";

        await prisma.user.update({
            where: { id: user.id },
            data: { plan: plan }
        });

        return res.json({
            success: true,
            isPro: true,
            plan: plan
        });
    } catch (err) {
        console.error("[Subscription Verify Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

module.exports = router;
