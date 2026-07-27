import { createWriteStream, existsSync, mkdirSync, renameSync } from "node:fs";
import { dirname } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { fromFile } from "geotiff";

// Copernicus GLO-30 (30 m) tile covering Guadalajara — AWS open data, no auth.
const DEM_URL =
  "https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_N20_00_W104_00_DEM/Copernicus_DSM_COG_10_N20_00_W104_00_DEM.tif";

// Only the metro area is kept in memory (~10 MB instead of ~52 MB)
const CROP = { west: -103.6, east: -103.1, south: 20.5, north: 20.9 };

interface Dem {
  data: Float32Array;
  width: number;
  height: number;
  west: number; // geographic coords of the crop's top-left pixel edge
  north: number;
  resX: number;
  resY: number;
}

let dem: Dem | null = null;
let loading: Promise<void> | null = null;
let failed = false;

function demPath(): string {
  return process.env.DEM_PATH ?? "./dem/glo30_n20w104.tif";
}

async function download(path: string): Promise<void> {
  mkdirSync(dirname(path), { recursive: true });
  console.log(`downloading GLO-30 DEM tile to ${path}…`);
  const res = await fetch(DEM_URL);
  if (!res.ok || res.body === null) throw new Error(`DEM download failed: ${res.status}`);
  const tmp = `${path}.tmp`;
  await pipeline(Readable.fromWeb(res.body as never), createWriteStream(tmp));
  renameSync(tmp, path);
  console.log("DEM tile downloaded");
}

async function load(): Promise<void> {
  const path = demPath();
  if (!existsSync(path)) await download(path);

  const tiff = await fromFile(path);
  const image = await tiff.getImage();
  const [west, south, east, north] = image.getBoundingBox();
  const fullW = image.getWidth();
  const fullH = image.getHeight();
  const resX = (east - west) / fullW;
  const resY = (north - south) / fullH;

  const x0 = Math.max(0, Math.floor((CROP.west - west) / resX));
  const x1 = Math.min(fullW, Math.ceil((CROP.east - west) / resX));
  const y0 = Math.max(0, Math.floor((north - CROP.north) / resY));
  const y1 = Math.min(fullH, Math.ceil((north - CROP.south) / resY));

  const rasters = await image.readRasters({ window: [x0, y0, x1, y1] });
  dem = {
    data: rasters[0] as Float32Array,
    width: x1 - x0,
    height: y1 - y0,
    west: west + x0 * resX,
    north: north - y0 * resY,
    resX,
    resY
  };
  console.log(`DEM ready: ${dem.width}x${dem.height} @30m covering the metro crop`);
}

export async function ensureDem(): Promise<boolean> {
  if (dem !== null) return true;
  if (failed) return false;
  loading ??= load().catch((e) => {
    failed = true;
    console.warn("DEM unavailable, falling back to Open-Meteo cache:", e);
  });
  await loading;
  return dem !== null;
}

/** Bilinear sample; null outside the crop. Call ensureDem() first. */
export function sampleDem(lat: number, lng: number): number | null {
  if (dem === null) return null;
  // pixel-is-area: value sits at the pixel center
  const fx = (lng - dem.west) / dem.resX - 0.5;
  const fy = (dem.north - lat) / dem.resY - 0.5;
  const x0 = Math.floor(fx);
  const y0 = Math.floor(fy);
  if (x0 < 0 || y0 < 0 || x0 >= dem.width - 1 || y0 >= dem.height - 1) return null;
  const tx = fx - x0;
  const ty = fy - y0;
  const at = (x: number, y: number) => dem!.data[y * dem!.width + x];
  const v =
    at(x0, y0) * (1 - tx) * (1 - ty) +
    at(x0 + 1, y0) * tx * (1 - ty) +
    at(x0, y0 + 1) * (1 - tx) * ty +
    at(x0 + 1, y0 + 1) * tx * ty;
  return v < -1000 ? null : v;
}
