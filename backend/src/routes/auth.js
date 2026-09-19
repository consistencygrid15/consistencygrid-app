const express = require("express");
const router = express.Router();
const crypto = require("crypto");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const prisma = require("../db");
const authenticate = require("../middleware/auth");

const JWT_SECRET = process.env.JWT_SECRET || "consistencygrid-secret";

function generatePublicToken() {
    return "pub_" + crypto.randomBytes(24).toString("hex");
}

function generateJwt(user) {
    return jwt.sign(
        { id: user.id, email: user.email, publicToken: user.publicToken },
        JWT_SECRET,
        { expiresIn: "90d" }
    );
}

// ─── POST /api/native-auth/email-signup ──────────────────────────────────────────
router.post("/native-auth/email-signup", async (req, res) => {
    try {
        const { email, password, name } = req.body;

        if (!email || !password) {
            return res.status(400).json({
                success: false,
                error: "Email and password are required"
            });
        }

        const normalizedEmail = email.trim().toLowerCase();

        const existing = await prisma.user.findUnique({
            where: { email: normalizedEmail }
        });

        if (existing) {
            return res.status(409).json({
                success: false,
                error: "An account with this email already exists"
            });
        }

        const hashedPassword = await bcrypt.hash(password, 10);
        const publicToken = generatePublicToken();

        const user = await prisma.user.create({
            data: {
                email: normalizedEmail,
                password: hashedPassword,
                name: name ? name.trim() : normalizedEmail.split("@")[0],
                publicToken: publicToken,
                plan: "free"
            }
        });

        const sessionToken = generateJwt(user);

        return res.status(201).json({
            success: true,
            token: user.publicToken,
            sessionToken: sessionToken,
            onboarded: true,
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                plan: user.plan
            }
        });
    } catch (err) {
        console.error("[Signup Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/native-auth/email-login ───────────────────────────────────────────
router.post("/native-auth/email-login", async (req, res) => {
    try {
        const { email, password } = req.body;

        if (!email || !password) {
            return res.status(400).json({
                success: false,
                error: "Email and password are required"
            });
        }

        const normalizedEmail = email.trim().toLowerCase();

        const user = await prisma.user.findUnique({
            where: { email: normalizedEmail }
        });

        if (!user || !user.password) {
            return res.status(401).json({
                success: false,
                error: "Invalid email or password"
            });
        }

        const isValid = await bcrypt.compare(password, user.password);
        if (!isValid) {
            return res.status(401).json({
                success: false,
                error: "Invalid email or password"
            });
        }

        const sessionToken = generateJwt(user);

        return res.json({
            success: true,
            token: user.publicToken,
            sessionToken: sessionToken,
            onboarded: true,
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                plan: user.plan
            }
        });
    } catch (err) {
        console.error("[Login Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/auth/native/google ────────────────────────────────────────────────
router.post("/auth/native/google", async (req, res) => {
    try {
        const { email, name, idToken } = req.body;

        if (!email) {
            return res.status(400).json({ success: false, error: "Email is required" });
        }

        const normalizedEmail = email.trim().toLowerCase();

        let user = await prisma.user.findUnique({
            where: { email: normalizedEmail }
        });

        if (!user) {
            user = await prisma.user.create({
                data: {
                    email: normalizedEmail,
                    name: name || normalizedEmail.split("@")[0],
                    publicToken: generatePublicToken(),
                    plan: "free"
                }
            });
        }

        const sessionToken = generateJwt(user);

        return res.json({
            success: true,
            token: user.publicToken,
            sessionToken: sessionToken,
            onboarded: true,
            user: {
                id: user.id,
                email: user.email,
                name: user.name,
                plan: user.plan
            }
        });
    } catch (err) {
        console.error("[Google Auth Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/native-auth/refresh ───────────────────────────────────────────────
router.post("/native-auth/refresh", authenticate, async (req, res) => {
    try {
        const user = req.user;
        const sessionToken = generateJwt(user);

        return res.json({
            success: true,
            token: user.publicToken,
            sessionToken: sessionToken
        });
    } catch (err) {
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

// ─── POST /api/native-auth/delete-account ─────────────────────────────────────────
router.post("/native-auth/delete-account", authenticate, async (req, res) => {
    try {
        await prisma.user.delete({
            where: { id: req.user.id }
        });
        return res.json({ success: true, message: "Account deleted successfully" });
    } catch (err) {
        console.error("[Delete Account Error]:", err);
        return res.status(500).json({ success: false, error: "Internal server error" });
    }
});

module.exports = router;
