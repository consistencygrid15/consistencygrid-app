const express = require("express");
const router = express.Router();
const prisma = require("../db");
const authenticate = require("../middleware/auth");

// ─── POST /api/device-token ────────────────────────────────────────────────────
router.post("/device-token", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { token, platform = "android" } = req.body;

        if (!token) {
            return res.status(400).json({ success: false, error: "Token is required" });
        }

        await prisma.deviceToken.upsert({
            where: {
                userId_token: {
                    userId: user.id,
                    token: token
                }
            },
            update: {
                platform: platform,
                updatedAt: new Date()
            },
            create: {
                userId: user.id,
                token: token,
                platform: platform
            }
        });

        return res.json({ success: true });
    } catch (err) {
        console.error("[Device Token Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/telemetry/wallpaper-update ─────────────────────────────────────────
router.post("/telemetry/wallpaper-update", authenticate, async (req, res) => {
    try {
        // Simple telemetry logging
        return res.json({ success: true });
    } catch (err) {
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

module.exports = router;
