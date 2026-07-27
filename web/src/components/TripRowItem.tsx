import { Link } from "react-router-dom";
import { tripLabel } from "../lib/category";
import { weekdayColor } from "../lib/colors";
import { formatDuration, formatKm, formatTime } from "../lib/format";
import { RAINY_MM, WINDY_KMH, type Trip, type TripWeather } from "../types";

interface Props {
  trip: Trip;
  weather?: TripWeather;
}

export default function TripRowItem({ trip, weather }: Props) {
  return (
    <Link to={`/trip/${trip.uuid}`} className="trip-row">
      <span className="bar" style={{ background: weekdayColor(new Date(trip.startTime)) }} />
      <span className="main">
        <div className="title">{tripLabel(trip)}</div>
        <div className="sub">
          {formatTime(trip.startTime)}
          {trip.endTime ? ` · ${formatDuration((trip.endTime - trip.startTime) / 1000)}` : ""} ·{" "}
          {formatKm(trip.distanceMeters)} km
          {weather === undefined
            ? ""
            : ` · ${weather.tempC.toFixed(0)}°C` +
              (weather.precipMm >= RAINY_MM ? " 🌧" : "") +
              (weather.windKmh >= WINDY_KMH ? " 💨" : "")}
        </div>
      </span>
      <span className="speed">{trip.averageSpeedKmh.toFixed(1)} km/h</span>
    </Link>
  );
}
