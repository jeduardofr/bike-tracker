interface Props {
  label: string;
  value: string;
  unit?: string;
}

export default function StatTile({ label, value, unit }: Props) {
  return (
    <div className="tile">
      <div className="value">
        {value}
        {unit ? <span className="unit">{unit}</span> : null}
      </div>
      <div className="label">{label}</div>
    </div>
  );
}
