import React from "react";
import { FileJson } from "lucide-react";
import { stableJson, topBarKotlinPreview } from "../tokenSchema.js";

export function TokenOutput({ tokens }) {
  return (
    <div className="token-output">
      <div className="output-card">
        <h3><FileJson size={17} /> JSON</h3>
        <pre>{stableJson(tokens)}</pre>
      </div>
      <div className="output-card">
        <h3>Kotlin top bar defaults</h3>
        <pre>{topBarKotlinPreview(tokens)}</pre>
      </div>
    </div>
  );
}
