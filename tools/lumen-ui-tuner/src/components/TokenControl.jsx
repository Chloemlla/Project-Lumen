import React from "react";

export function TokenControl({ control, value, onChange }) {
  if (control.kind === "text") {
    return (
      <label className="control text-control">
        <span>{control.label}</span>
        <input value={value} onChange={(event) => onChange(event.target.value)} />
      </label>
    );
  }

  if (control.kind === "color") {
    return (
      <label className="control color-control">
        <span>{control.label}</span>
        <input value={value} type="color" onChange={(event) => onChange(event.target.value)} />
        <code>{value}</code>
      </label>
    );
  }

  return (
    <label className="control slider-control">
      <span>{control.label}</span>
      <input
        type="range"
        min={control.min}
        max={control.max}
        step={control.step}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
      <output>{value}{control.unit}</output>
    </label>
  );
}
