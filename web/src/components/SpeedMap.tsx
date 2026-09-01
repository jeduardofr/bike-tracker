import { useMemo } from "react";
import { CircleMarker, MapContainer, Tooltip } from "react-leaflet";
import { LatLngBounds } from "leaflet";
import BasemapTiles from "./BasemapTiles";
import { FAST_COLOR, NEUTRAL_COLOR, SLOW_COLOR, speedRampColor } from "../lib/colors";
import type { SpeedBin } from "../types";

interface Props {
  bins: SpeedBin[];
  medianKmh: number;
}

export default function SpeedMap({ bins, medianKmh }: Props) {
  const bounds = useMemo(() => {
    const b = new LatLngBounds([]);
    bins.forEach(([lat, lng]) => b.extend([lat, lng]));
    return b.isValid() ? b.pad(0.05) : null;
  }, [bins]);

  // ±60% of the median saturates the ramp
  const scale = Math.max(1, medianKmh * 0.6);

  return bounds === null ? null : (
    <>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
        <div className="legend">
          <span>slower</span>
          <span
            style={{
              width: 120,
              height: 8,
              borderRadius: 4,
              background: `linear-gradient(90deg, ${SLOW_COLOR}, ${NEUTRAL_COLOR}, ${FAST_COLOR})`
            }}
          />
          <span>faster · median {medianKmh.toFixed(0)} km/h</span>
        </div>
      </div>
      <div className="map-wrap">
        <MapContainer bounds={bounds} scrollWheelZoom={true} preferCanvas={true}>
          <BasemapTiles />
          {bins.map(([lat, lng, kmh], i) => (
            <CircleMarker
              key={i}
              center={[lat, lng]}
              radius={4}
              pathOptions={{
                color: speedRampColor((kmh - medianKmh) / scale),
                fillColor: speedRampColor((kmh - medianKmh) / scale),
                fillOpacity: 0.75,
                weight: 0
              }}
            >
              <Tooltip>{kmh.toFixed(1)} km/h (median here)</Tooltip>
            </CircleMarker>
          ))}
        </MapContainer>
      </div>
    </>
  );
}
