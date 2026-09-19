const express = require("express");
const router = express.Router();
const prisma = require("../db");
const authenticate = require("../middleware/auth");

// ─── POST /api/mobile/reel-controller/sync ──────────────────────────────────────
router.post("/mobile/reel-controller/sync", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { isFeatureEnabled, isBlockModeEnabled, isHardBlockEnabled, apps = [] } = req.body;

        // 1. Upsert Global Config
        await prisma.reelGlobalConfig.upsert({
            where: { userId: user.id },
            update: {
                isFeatureEnabled: isFeatureEnabled !== undefined ? Boolean(isFeatureEnabled) : true,
                isBlockModeEnabled: isBlockModeEnabled !== undefined ? Boolean(isBlockModeEnabled) : false,
                isHardBlockEnabled: isHardBlockEnabled !== undefined ? Boolean(isHardBlockEnabled) : false
            },
            create: {
                userId: user.id,
                isFeatureEnabled: isFeatureEnabled !== undefined ? Boolean(isFeatureEnabled) : true,
                isBlockModeEnabled: isBlockModeEnabled !== undefined ? Boolean(isBlockModeEnabled) : false,
                isHardBlockEnabled: isHardBlockEnabled !== undefined ? Boolean(isHardBlockEnabled) : false
            }
        });

        // 2. Upsert Per-App Settings
        for (const app of apps) {
            const { pkg, reelLimit, timeLimit, isEnabled } = app;
            if (!pkg) continue;

            await prisma.reelApp.upsert({
                where: {
                    userId_pkg: {
                        userId: user.id,
                        pkg: pkg
                    }
                },
                update: {
                    reelLimit: reelLimit !== undefined ? Number(reelLimit) : 30,
                    timeLimit: timeLimit !== undefined ? Number(timeLimit) : 20,
                    isEnabled: isEnabled !== undefined ? Boolean(isEnabled) : true
                },
                create: {
                    userId: user.id,
                    pkg: pkg,
                    reelLimit: reelLimit !== undefined ? Number(reelLimit) : 30,
                    timeLimit: timeLimit !== undefined ? Number(timeLimit) : 20,
                    isEnabled: isEnabled !== undefined ? Boolean(isEnabled) : true
                }
            });
        }

        return res.json({ success: true });
    } catch (err) {
        console.error("[Reel Controller Sync Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── GET /api/mobile/reel-controller ────────────────────────────────────────────
router.get("/mobile/reel-controller", authenticate, async (req, res) => {
    try {
        const user = req.user;

        const globalConfig = await prisma.reelGlobalConfig.findUnique({
            where: { userId: user.id }
        });

        const apps = await prisma.reelApp.findMany({
            where: { userId: user.id }
        });

        return res.json({
            success: true,
            data: {
                isFeatureEnabled: globalConfig ? globalConfig.isFeatureEnabled : true,
                isBlockModeEnabled: globalConfig ? globalConfig.isBlockModeEnabled : false,
                isHardBlockEnabled: globalConfig ? globalConfig.isHardBlockEnabled : false,
                apps: apps.map(a => ({
                    pkg: a.pkg,
                    reelLimit: a.reelLimit,
                    timeLimit: a.timeLimit,
                    isEnabled: a.isEnabled
                }))
            }
        });
    } catch (err) {
        console.error("[Get Reel Controller Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

module.exports = router;
