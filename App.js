import React, { useEffect } from "react";
import { Linking, AsyncStorage } from "react-native";
import HomeScreen from "./src/screens/HomeScreen";

export default function App() {
  useEffect(() => {
    // Handle deep links when app is already running
    const subscription = Linking.addEventListener("url", handleDeepLink);
    
    return () => {
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    // Handle deep link when app is launched from URL
    Linking.getInitialURL().then((url) => {
      if (url != null) {
        handleDeepLink({ url });
      }
    });
  }, []);

  const handleDeepLink = ({ url }) => {
    console.log("[DeepLink] Received:", url);
    
    // Check if it's payment success link
    if (!url.includes("consistencygrid://payment-success")) {
      return;
    }

    // Extract query parameters
    const params = {};
    const queryString = url.split("?")?.[1];
    
    if (queryString) {
      queryString.split("&").forEach((pair) => {
        const [key, value] = pair.split("=");
        params[decodeURIComponent(key)] = decodeURIComponent(value);
      });
    }

    console.log("[DeepLink] Parsed params:", {
      plan: params.plan,
      hasToken: !!params.token,
    });

    // Save subscription token
    if (params.token) {
      try {
        AsyncStorage.multiSet([
          ["subscription_token", params.token],
          ["user_plan", params.plan || "pro_yearly"],
          ["subscription_status", "active"],
          ["is_premium", "true"],
          [
            "subscription_expiry",
            params.expiryDate || new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
          ],
        ]);
        
        console.log("[DeepLink]  Subscription saved successfully");
      } catch (error) {
        console.error("[DeepLink] Error saving subscription:", error);
      }
    }
  };

  return <HomeScreen />;
}
