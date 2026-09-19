const express = require("express");
const router = express.Router();
const prisma = require("../db");
const authenticate = require("../middleware/auth");

/**
 * GET /api/wallpaper-data
 * Aggregates all user data needed by the Android Wallpaper service & Home Widgets:
 * - Active habits and recent logs
 * - Active reminders
 * - Goals
 * - Calculated stats: current streak, total habits, today's completion %
 */
router.get("/wallpaper-data", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const deviceDate = req.query.deviceDate || new Date().toISOString().slice(0, 10);

        // 1. Fetch active habits with logs
        const habits = await prisma.habit.findMany({
            where: { userId: user.id, isActive: true },
            include: {
                logs: {
                    orderBy: { date: "desc" },
                    take: 365 // Last 1 year of logs for grid rendering
                }
            },
            orderBy: { createdAt: "asc" }
        });

        // 2. Fetch active reminders
        const reminders = await prisma.reminder.findMany({
            where: { userId: user.id, isActive: true },
            orderBy: [{ priority: "desc" }, { startDate: "asc" }]
        });

        // 3. Fetch goals
        const goals = await prisma.goal.findMany({
            where: { userId: user.id },
            orderBy: [{ isPinned: "desc" }, { createdAt: "desc" }]
        });

        // 4. Calculate Stats
        const totalHabits = habits.length;
        let todayCompleted = 0;

        const formattedHabits = habits.map(h => {
            const formattedLogs = h.logs.map(l => {
                const dateStr = l.date.toISOString().slice(0, 10);
                if (dateStr === deviceDate && l.done) {
                    todayCompleted++;
                }
                return {
                    date: dateStr,
                    done: l.done
                };
            });

            return {
                id: h.id,
                title: h.title,
                scheduledTime: h.scheduledTime,
                logs: formattedLogs
            };
        });

        const todayPct = totalHabits > 0 ? Math.round((todayCompleted / totalHabits) * 100) : 0;

        // Calculate Streak (consecutive days where at least 1 habit or all habits were completed)
        let streak = 0;
        const checkDate = new Date(deviceDate);
        for (let i = 0; i < 365; i++) {
            const targetDateStr = checkDate.toISOString().slice(0, 10);
            let anyDone = false;
            for (const h of habits) {
                if (h.logs.some(l => l.date.toISOString().slice(0, 10) === targetDateStr && l.done)) {
                    anyDone = true;
                    break;
                }
            }
            if (anyDone) {
                streak++;
                checkDate.setDate(checkDate.getDate() - 1);
            } else {
                // If today has no ticks yet, don't break streak immediately if yesterday was done
                if (i === 0) {
                    checkDate.setDate(checkDate.getDate() - 1);
                    continue;
                }
                break;
            }
        }

        const formattedReminders = reminders.map(r => ({
            id: r.id,
            title: r.title,
            description: r.description,
            startDate: r.startDate.toISOString().slice(0, 10),
            endDate: r.endDate.toISOString().slice(0, 10),
            startTime: r.startTime,
            endTime: r.endTime,
            isFullDay: r.isFullDay,
            priority: r.priority,
            markerColor: r.markerColor
        }));

        const formattedGoals = goals.map(g => ({
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

        return res.json({
            success: true,
            user: {
                id: user.id,
                name: user.name || "",
                email: user.email,
                plan: user.plan
            },
            settings: {
                plan: user.plan
            },
            stats: {
                streak: streak,
                totalHabits: totalHabits,
                todayCompletionPercentage: todayPct
            },
            data: {
                habits: formattedHabits,
                reminders: formattedReminders,
                goals: formattedGoals
            }
        });
    } catch (err) {
        console.error("[Wallpaper Data Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

module.exports = router;
