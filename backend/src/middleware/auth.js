const prisma = require("../db");

/**
 * Authentication Middleware
 * 
 * Supports:
 *  - Header: "Authorization: Bearer <token>"
 *  - Query: "?token=<token>"
 *  - Cookie: "publicToken=<token>"
 * 
 * Injects `req.user` if valid, otherwise returns 401 Unauthorized.
 */
async function authenticate(req, res, next) {
    try {
        const authHeader = req.headers["authorization"] || req.headers["Authorization"];
        let token = null;

        if (authHeader && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7).trim();
        } else if (req.query.token) {
            token = req.query.token;
        } else if (req.headers["cookie"]) {
            const match = req.headers["cookie"].match(/publicToken=([^;]+)/);
            if (match) token = match[1];
        }

        if (!token) {
            return res.status(401).json({
                success: false,
                error: "Authentication token required"
            });
        }

        const user = await prisma.user.findUnique({
            where: { publicToken: token }
        });

        if (!user) {
            return res.status(401).json({
                success: false,
                error: "Invalid or expired token"
            });
        }

        req.user = user;
        req.token = token;
        next();
    } catch (err) {
        console.error("[Auth Middleware Error]:", err);
        return res.status(500).json({
            success: false,
            error: "Authentication server error"
        });
    }
}

module.exports = authenticate;
