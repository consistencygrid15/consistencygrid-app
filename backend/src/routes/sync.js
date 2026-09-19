const express = require("express");
const router = express.Router();
const prisma = require("../db");
const authenticate = require("../middleware/auth");

// ─── 1. HABITS SYNC: POST /api/mobile/habits/sync ───────────────────────────────
router.post("/mobile/habits/sync", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { upserts = [], deletes = [] } = req.body;

        const upsertedResults = [];

        // 1. Process Upserts (creates & updates)
        for (const habit of upserts) {
            const { localId, serverId, title, scheduledTime, isActive } = habit;
            if (!title) continue;

            if (serverId) {
                const existing = await prisma.habit.findFirst({
                    where: { id: serverId, userId: user.id }
                });

                if (existing) {
                    await prisma.habit.update({
                        where: { id: serverId },
                        data: {
                            title: title.trim(),
                            scheduledTime: scheduledTime || null,
                            isActive: isActive !== undefined ? Boolean(isActive) : true
                        }
                    });
                    upsertedResults.push({ localId, serverId, status: "updated" });
                } else {
                    const created = await prisma.habit.create({
                        data: {
                            userId: user.id,
                            title: title.trim(),
                            scheduledTime: scheduledTime || null,
                            isActive: isActive !== undefined ? Boolean(isActive) : true
                        }
                    });
                    upsertedResults.push({ localId, serverId: created.id, status: "created" });
                }
            } else {
                const created = await prisma.habit.create({
                    data: {
                        userId: user.id,
                        title: title.trim(),
                        scheduledTime: scheduledTime || null,
                        isActive: isActive !== undefined ? Boolean(isActive) : true
                    }
                });
                upsertedResults.push({ localId, serverId: created.id, status: "created" });
            }
        }

        // 2. Process Deletes (soft-delete)
        let deletedCount = 0;
        if (deletes.length > 0) {
            const valid = await prisma.habit.findMany({
                where: { id: { in: deletes }, userId: user.id },
                select: { id: true }
            });
            const idsToDelete = valid.map(h => h.id);

            if (idsToDelete.length > 0) {
                await prisma.habit.updateMany({
                    where: { id: { in: idsToDelete } },
                    data: { isActive: false }
                });
                deletedCount = idsToDelete.length;
            }
        }

        return res.json({
            success: true,
            upserted: upsertedResults,
            deletedCount: deletedCount
        });
    } catch (err) {
        console.error("[Habits Sync Error]:", err);
        return res.status(500).json({ success: false, error: err.message || "Internal server error" });
    }
});

// ─── 2. HABIT LOGS (TICKS): POST /api/mobile/habits/tick ────────────────────────
router.post("/mobile/habits/tick", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { logs = [] } = req.body;

        if (!Array.isArray(logs) || logs.length === 0) {
            return res.json({ success: true, message: "No logs provided", count: 0 });
        }

        const synced = [];

        for (const log of logs) {
            const { habitId, date, done } = log;
            if (!habitId || !date || done === undefined) continue;

            const logDate = new Date(date);
            if (isNaN(logDate.getTime())) continue;

            // Check if habit belongs to user
            const habit = await prisma.habit.findFirst({
                where: { id: habitId, userId: user.id }
            });
            if (!habit) continue;

            const existing = await prisma.habitLog.findFirst({
                where: {
                    userId: user.id,
                    habitId: habitId,
                    date: logDate
                }
            });

            if (existing) {
                await prisma.habitLog.update({
                    where: { id: existing.id },
                    data: { done: Boolean(done) }
                });
            } else {
                await prisma.habitLog.create({
                    data: {
                        userId: user.id,
                        habitId: habitId,
                        date: logDate,
                        done: Boolean(done)
                    }
                });
            }

            synced.push({ habitId, date, status: "synced" });
        }

        return res.json({
            success: true,
            count: synced.length,
            synced: synced
        });
    } catch (err) {
        console.error("[Habits Tick Error]:", err);
        return res.status(500).json({ success: false, error: err.message || "Internal server error" });
    }
});

// ─── 3. GOALS SYNC: POST /api/mobile/goals/sync ─────────────────────────────────
router.post("/mobile/goals/sync", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { upserts = [], deletes = [] } = req.body;

        const upserted = [];

        // 1. Process Deletions
        let deletedCount = 0;
        if (deletes.length > 0) {
            const valid = await prisma.goal.findMany({
                where: { id: { in: deletes }, userId: user.id },
                select: { id: true }
            });
            const idsToDelete = valid.map(g => g.id);

            if (idsToDelete.length > 0) {
                await prisma.goal.deleteMany({
                    where: { id: { in: idsToDelete } }
                });
                deletedCount = idsToDelete.length;
            }
        }

        // 2. Process Upserts
        for (const g of upserts) {
            const { localId, serverId, title, category, progress, isCompleted, isPinned, subGoals } = g;
            if (!title) continue;

            const subGoalsStr = subGoals ? JSON.stringify(subGoals) : "[]";
            const goalData = {
                title: title.trim(),
                category: category || "General",
                progress: progress !== undefined ? Number(progress) : 0,
                isCompleted: isCompleted !== undefined ? Boolean(isCompleted) : false,
                isPinned: isPinned !== undefined ? Boolean(isPinned) : false,
                subGoalsJson: subGoalsStr
            };

            if (serverId) {
                const existing = await prisma.goal.findFirst({
                    where: { id: serverId, userId: user.id }
                });

                if (existing) {
                    await prisma.goal.update({
                        where: { id: serverId },
                        data: goalData
                    });
                    upserted.push({ localId, serverId, status: "updated" });
                } else {
                    const created = await prisma.goal.create({
                        data: { userId: user.id, ...goalData }
                    });
                    upserted.push({ localId, serverId: created.id, status: "created" });
                }
            } else {
                const created = await prisma.goal.create({
                    data: { userId: user.id, ...goalData }
                });
                upserted.push({ localId, serverId: created.id, status: "created" });
            }
        }

        return res.json({
            success: true,
            upserted: upserted,
            deleted: deletedCount
        });
    } catch (err) {
        console.error("[Goals Sync Error]:", err);
        return res.status(500).json({ success: false, error: err.message || "Internal server error" });
    }
});

// ─── 4. GET GOALS: GET /api/goals ────────────────────────────────────────────────
router.get("/goals", authenticate, async (req, res) => {
    try {
        const goals = await prisma.goal.findMany({
            where: { userId: req.user.id },
            orderBy: [{ isPinned: "desc" }, { createdAt: "desc" }]
        });

        const mapped = goals.map(g => ({
            id: g.id,
            title: g.title,
            category: g.category,
            progress: g.progress,
            isCompleted: g.isCompleted,
            isPinned: g.isPinned,
            subGoals: (() => {
                try { return JSON.parse(g.subGoalsJson); } catch (e) { return []; }
            })()
        }));

        return res.json({ success: true, goals: mapped });
    } catch (err) {
        console.error("[Get Goals Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── 5. REMINDERS SYNC: POST /api/mobile/reminders/sync ─────────────────────────
router.post("/mobile/reminders/sync", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const { upserts = [], deletes = [] } = req.body;

        const upsertedResults = [];

        // Safe Transaction
        await prisma.$transaction(async (tx) => {
            // Process Upserts
            for (const item of upserts) {
                const {
                    localId,
                    serverId,
                    title,
                    description,
                    startDate,
                    endDate,
                    startTime,
                    endTime,
                    isFullDay,
                    priority,
                    markerColor
                } = item;

                if (!title) continue;

                // Safe Date parsing — prevents Invalid Date crash!
                const safeStartDate = startDate && !isNaN(new Date(startDate).getTime())
                    ? new Date(startDate)
                    : new Date();
                const safeEndDate = endDate && !isNaN(new Date(endDate).getTime())
                    ? new Date(endDate)
                    : safeStartDate;

                const reminderData = {
                    title: title.trim(),
                    description: description || null,
                    startDate: safeStartDate,
                    endDate: safeEndDate,
                    startTime: startTime || null,
                    endTime: endTime || null,
                    isFullDay: isFullDay !== undefined ? Boolean(isFullDay) : true,
                    priority: priority !== undefined ? Number(priority) : 1,
                    markerColor: markerColor || "#FF7A00",
                    isActive: true
                };

                if (serverId) {
                    const existing = await tx.reminder.findFirst({
                        where: { id: serverId, userId: user.id }
                    });

                    if (existing) {
                        const updated = await tx.reminder.update({
                            where: { id: serverId },
                            data: reminderData
                        });
                        upsertedResults.push({ localId, serverId: updated.id, status: "updated" });
                    } else {
                        const created = await tx.reminder.create({
                            data: { userId: user.id, ...reminderData }
                        });
                        upsertedResults.push({ localId, serverId: created.id, status: "created" });
                    }
                } else {
                    const created = await tx.reminder.create({
                        data: { userId: user.id, ...reminderData }
                    });
                    upsertedResults.push({ localId, serverId: created.id, status: "created" });
                }
            }

            // Process Deletes safely
            if (deletes.length > 0) {
                await tx.reminder.deleteMany({
                    where: {
                        id: { in: deletes },
                        userId: user.id
                    }
                });
            }
        });

        return res.json({
            success: true,
            upserted: upsertedResults,
            deletedCount: deletes.length
        });
    } catch (err) {
        console.error("[Reminders Sync Error]:", err);
        return res.status(500).json({ success: false, error: err.message || "Internal server error" });
    }
});

module.exports = router;
