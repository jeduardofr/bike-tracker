import { Route, Routes } from "react-router-dom";
import DashboardPage from "./pages/DashboardPage";
import HistoryPage from "./pages/HistoryPage";
import InsightsPage from "./pages/InsightsPage";
import LoginPage from "./pages/LoginPage";
import TripPage from "./pages/TripPage";

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<DashboardPage />} />
      <Route path="/history" element={<HistoryPage />} />
      <Route path="/insights" element={<InsightsPage />} />
      <Route path="/trip/:uuid" element={<TripPage />} />
    </Routes>
  );
}
