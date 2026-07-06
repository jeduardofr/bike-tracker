import { useMemo } from "react";
import { MapContainer, Polyline, TileLayer } from "react-leaflet";
import { LatLngBounds } from "leaflet";

export interface MapLine {
  positions: [number, number][];
  color: string;
  dashed: boolean;
}

interface Props {
  lines: MapLine[];
}

export default function TripMap({ lines }: Props) {
  const bounds = useMemo(() => {
    const b = new LatLngBounds([]);
    lines.forEach((line) => line.positions.forEach((p) => b.extend(p)));
    return b.isValid() ? b.pad(0.08) : null;
  }, [lines]);

  return bounds === null ? (
    <div className="loading">No route data</div>
  ) : (
    <div className="map-wrap">
      <MapContainer bounds={bounds} scrollWheelZoom={true} preferCanvas={true}>
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>'
        />
        {lines.map((line, i) => (
          <Polyline
            key={i}
            positions={line.positions}
            pathOptions={{
              color: line.color,
              weight: 3.5,
              opacity: 0.95,
              dashArray: line.dashed ? "6 8" : undefined
            }}
          />
        ))}
      </MapContainer>
    </div>
  );
}
