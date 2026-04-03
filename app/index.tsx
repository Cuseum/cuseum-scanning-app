import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";
import { Redirect } from "expo-router";
import { deviceStore } from "../src/store/deviceStore";

export default function Index() {
  const [destination, setDestination] = useState<"/home" | "/pair" | null>(
    null
  );

  useEffect(() => {
    deviceStore.load().then((config) => {
      setDestination(config ? "/home" : "/pair");
    });
  }, []);

  if (!destination) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return <Redirect href={destination} />;
}
