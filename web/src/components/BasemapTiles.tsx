import { useEffect, useState } from "react";
import { TileLayer } from "react-leaflet";
import { api } from "../api";

// CARTO raster basemaps require an API key (?key=…), free up to 5M tiles/month.
// The key is fetched once per session from the server rather than baked into the bundle.
let cached: Promise<string | null> | null = null;
function cartoKey(): Promise<string | null> {
  cached ??= api
    .config()
    .then((c) => c.cartoApiKey)
    .catch(() => null);
  return cached;
}

const ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>';

export default function BasemapTiles() {
  const [key, setKey] = useState<string | null | undefined>(undefined);
  useEffect(() => {
    let cancelled = false;
    void cartoKey().then((k) => {
      if (!cancelled) setKey(k);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  // wait for the key so we never request unkeyed (watermarked) tiles first
  return key === undefined ? null : (
    <TileLayer
      url={
        key === null
          ? "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          : `https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png?key=${encodeURIComponent(key)}`
      }
      attribution={ATTRIBUTION}
    />
  );
}
