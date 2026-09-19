require("dotenv").config();
const express = require("express");
const cors = require("cors");

const authRoutes = require("./routes/auth");
const syncRoutes = require("./routes/sync");
const wallpaperRoutes = require("./routes/wallpaper");
const reelControllerRoutes = require("./routes/reelController");
const deviceRoutes = require("./routes/device");
const subscriptionRoutes = require("./routes/subscription");

const app = express();
const PORT = process.env.PORT || 5000;

// ─── Middleware ────────────────────────────────────────────────────────────────
app.use(cors());
app.use(express.json({ limit: "10mb" }));
app.use(express.urlencoded({ extended: true }));

// Request Logger
app.use((req, res, next) => {
    const start = Date.now();
    res.on("finish", () => {
        const duration = Date.now() - start;
        console.log(`[${req.method}] ${req.originalUrl} - ${res.statusCode} (${duration}ms)`);
    });
    next();
});

// ─── Health Check ──────────────────────────────────────────────────────────────
app.get("/health", (req, res) => {
    res.json({
        status: "ok",
        service: "consistencygrid-mobile-backend",
        timestamp: new Date().toISOString(),
        uptime: Math.round(process.uptime()) + "s"
    });
});

// ─── Routes Mount ──────────────────────────────────────────────────────────────
app.use("/api", authRoutes);
app.use("/api", syncRoutes);
app.use("/api", wallpaperRoutes);
app.use("/api", reelControllerRoutes);
app.use("/api", deviceRoutes);
app.use("/api", subscriptionRoutes);

// ─── 404 Handler ───────────────────────────────────────────────────────────────
app.use((req, res) => {
    res.status(404).json({
        success: false,
        error: `Endpoint not found: ${req.method} ${req.originalUrl}`
    });
});

// ─── Global Error Handler ──────────────────────────────────────────────────────
app.use((err, req, res, next) => {
    console.error("[Unhandled Server Error]:", err);
    res.status(500).json({
        success: false,
        error: "Internal server error"
    });
});

// ─── Start Server ──────────────────────────────────────────────────────────────
if (process.env.NODE_ENV !== "test") {
    app.listen(PORT, "0.0.0.0", () => {
        console.log(`🚀 ConsistencyGrid Mobile API Server running on port ${PORT}`);
        console.log(`📡 Health Check: http://localhost:${PORT}/health`);
    });
}

module.exports = app;
