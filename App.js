import React, { useEffect } from "react";
import { Linking, View, Text, StyleSheet } from "react-native";

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
    if (!url || !url.includes("consistencygrid://")) {
      return;
    }

    const params = {};
    const queryString = url.split("?")?.[1];
    if (queryString) {
      queryString.split("&").forEach((pair) => {
        const [key, value] = pair.split("=");
        if (key) {
          params[decodeURIComponent(key)] = value ? decodeURIComponent(value) : "";
        }
      });
    }

    console.log("[DeepLink] Parsed params:", params);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Consistency Grid Native App</Text>
      <Text style={styles.subtitle}>Native Kotlin & Jetpack Compose Active</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#0A0A0C",
  },
  title: {
    color: "#FFFFFF",
    fontSize: 20,
    fontWeight: "bold",
  },
  subtitle: {
    color: "#8E8E93",
    fontSize: 14,
    marginTop: 8,
  },
});

