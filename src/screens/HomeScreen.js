import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { NativeModules, Linking } from "react-native";

const { WallpaperModule } = NativeModules;

export default function HomeScreen() {
  const [url, setUrl] = useState("");
  const [savedUrl, setSavedUrl] = useState("");
  const [lastUpdated, setLastUpdated] = useState("Never");

  useEffect(() => {
    (async () => {
      const u = await AsyncStorage.getItem("WALLPAPER_URL");
      const t = await AsyncStorage.getItem("LAST_UPDATED");
      if (u) setSavedUrl(u);
      if (t) setLastUpdated(t);
    })();
  }, []);

  const saveUrl = async () => {
    const clean = url.trim();

    if (!clean.startsWith("http")) {
      Alert.alert("Invalid URL", "URL must start with http/https");
      return;
    }

    await AsyncStorage.setItem("WALLPAPER_URL", clean);
    await WallpaperModule.saveUrl(clean);

    setSavedUrl(clean);
    Alert.alert("Saved ✅", "Wallpaper URL saved");
  };

  const enableLive = async () => {
    try {
      await WallpaperModule.enableLiveWallpaper();
      Alert.alert("Enabled ✅", "Auto update started");
    } catch (e) {
      Alert.alert("Error", e.message);
    }
  };

  const disableLive = async () => {
    try {
      await WallpaperModule.disableLiveWallpaper();
      Alert.alert("Disabled ✅", "Auto update stopped");
    } catch (e) {
      Alert.alert("Error", e.message);
    }
  };

  const updateNow = async () => {
    try {
      await WallpaperModule.updateNow();
      const now = new Date().toLocaleString();
      await AsyncStorage.setItem("LAST_UPDATED", now);
      setLastUpdated(now);
      Alert.alert("Updated ✅", "Wallpaper update started");
    } catch (e) {
      Alert.alert("Error", e.message);
    }
  };

  const copyUrl = async () => {
    if (!savedUrl) return Alert.alert("No URL", "Save URL first");
    await navigator.clipboard.writeText(savedUrl);
    Alert.alert("Copied ✅", "URL copied for MacroDroid");
  };

  const openPreview = () => {
    if (!savedUrl) return Alert.alert("No URL", "Save URL first");
    Linking.openURL(savedUrl);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>ConsistencyGrid Wallpaper</Text>

      <Text style={styles.label}>Paste Wallpaper PNG URL</Text>
      <TextInput
        style={styles.input}
        placeholder="https://domain.com/w/token/image.png"
        value={url}
        onChangeText={setUrl}
      />

      <TouchableOpacity style={styles.btnOrange} onPress={saveUrl}>
        <Text style={styles.btnText}>Save URL ✅</Text>
      </TouchableOpacity>

      <View style={styles.card}>
        <Text style={styles.small}>Saved URL:</Text>
        <Text style={styles.saved}>{savedUrl || "Not saved yet"}</Text>

        <Text style={styles.small}>Last Updated:</Text>
        <Text style={styles.saved}>{lastUpdated}</Text>
      </View>

      <TouchableOpacity style={styles.btnBlack} onPress={enableLive}>
        <Text style={styles.btnText}>Enable Live ✅</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.btnOutline} onPress={updateNow}>
        <Text style={styles.btnOutlineText}>Update Now 🔄</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.btnOutline} onPress={disableLive}>
        <Text style={styles.btnOutlineText}>Disable ❌</Text>
      </TouchableOpacity>

      <TouchableOpacity style={styles.btnOutline} onPress={openPreview}>
        <Text style={styles.btnOutlineText}>Open Preview 🔗</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 18, backgroundColor: "#fffaf1" },
  title: { fontSize: 22, fontWeight: "800", marginBottom: 16 },
  label: { fontSize: 13, fontWeight: "700", marginBottom: 6 },
  input: {
    backgroundColor: "#fff",
    borderWidth: 1,
    borderColor: "#ddd",
    padding: 12,
    borderRadius: 12,
    marginBottom: 12,
  },
  btnOrange: {
    backgroundColor: "#f97316",
    padding: 14,
    borderRadius: 12,
    alignItems: "center",
  },
  btnBlack: {
    backgroundColor: "#000",
    padding: 14,
    borderRadius: 12,
    alignItems: "center",
    marginTop: 12,
  },
  btnText: { color: "#fff", fontWeight: "800" },
  btnOutline: {
    borderWidth: 1,
    borderColor: "#111",
    padding: 14,
    borderRadius: 12,
    alignItems: "center",
    marginTop: 10,
  },
  btnOutlineText: { color: "#111", fontWeight: "800" },
  card: {
    backgroundColor: "#fff",
    padding: 14,
    borderRadius: 14,
    marginTop: 14,
    borderWidth: 1,
    borderColor: "#eee",
  },
  small: { fontSize: 12, color: "#555", marginTop: 6 },
  saved: { fontSize: 12, color: "#111", marginTop: 4 },
});
