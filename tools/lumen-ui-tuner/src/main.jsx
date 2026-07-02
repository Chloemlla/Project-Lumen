import React, { useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import { Download, FolderOpen, RotateCcw, Save, Smartphone } from "lucide-react";
import sharedTokens from "../../../design/lumen-ui-tokens.json";
import { ElementInspector } from "./components/ElementInspector.jsx";
import { PhonePreview } from "./components/PhonePreview.jsx";
import { TokenControl } from "./components/TokenControl.jsx";
import { TokenOutput } from "./components/TokenOutput.jsx";
import { defaultTokens } from "./defaultTokens.js";
import { cloneTokens, controlGroups, readPath, stableJson, updatePath } from "./tokenSchema.js";
import { buildMetrics, mergeTokens } from "./tokenUtils.js";
import "../styles.css";

function App() {
  const [tokens, setTokens] = useState(() => mergeTokens(defaultTokens, sharedTokens));
  const [fileHandle, setFileHandle] = useState(null);
  const [lastAction, setLastAction] = useState("Loaded shared defaults");
  const [selectedElement, setSelectedElement] = useState("primaryTitle");
  const fileInputRef = useRef(null);
  const metrics = useMemo(() => buildMetrics(tokens), [tokens]);

  function updateToken(control, rawValue) {
    const value = control.kind === "text" || control.kind === "color"
      ? rawValue
      : Number(rawValue);
    setTokens((current) => updatePath(current, control.path, value));
    setLastAction(`${control.label}: ${value}${control.unit || ""}`);
  }

  function updateTokenByPath(path, value, label = path) {
    setTokens((current) => updatePath(current, path, value));
    setLastAction(`${label}: ${value}`);
  }

  async function openTokenFile() {
    if ("showOpenFilePicker" in window) {
      const [handle] = await window.showOpenFilePicker({
        types: [{ description: "Lumen UI tokens", accept: { "application/json": [".json"] } }],
        multiple: false,
      });
      const file = await handle.getFile();
      await loadTokenText(await file.text());
      setFileHandle(handle);
      setLastAction(`Opened ${file.name}`);
      return;
    }

    fileInputRef.current?.click();
  }

  async function importTokenFile(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    await loadTokenText(await file.text());
    setFileHandle(null);
    setLastAction(`Imported ${file.name}`);
    event.target.value = "";
  }

  async function loadTokenText(text) {
    const parsed = JSON.parse(text);
    setTokens(mergeTokens(defaultTokens, parsed));
  }

  async function saveTokenFile() {
    const json = stableJson(tokens);
    if (fileHandle?.createWritable) {
      const writable = await fileHandle.createWritable();
      await writable.write(json);
      await writable.close();
      setLastAction("Saved to opened token file");
      return;
    }

    if ("showSaveFilePicker" in window) {
      const handle = await window.showSaveFilePicker({
        suggestedName: "lumen-ui-tokens.json",
        types: [{ description: "Lumen UI tokens", accept: { "application/json": [".json"] } }],
      });
      const writable = await handle.createWritable();
      await writable.write(json);
      await writable.close();
      setFileHandle(handle);
      setLastAction("Saved token file");
      return;
    }

    downloadJson(json);
  }

  function downloadJson(json = stableJson(tokens)) {
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "lumen-ui-tokens.json";
    anchor.click();
    URL.revokeObjectURL(url);
    setLastAction("Downloaded token JSON");
  }

  function resetTokens() {
    setTokens(cloneTokens(defaultTokens));
    setFileHandle(null);
    setLastAction("Reset to built-in defaults");
  }

  return (
    <main className="app-shell">
      <aside className="control-panel">
        <div className="panel-title">
          <div>
            <p className="eyebrow">Project Lumen</p>
            <h1>UI Tuner</h1>
          </div>
          <Smartphone aria-hidden="true" />
        </div>

        <div className="toolbar">
          <button type="button" onClick={openTokenFile}><FolderOpen size={17} /> Open</button>
          <button type="button" onClick={saveTokenFile}><Save size={17} /> Save</button>
          <button type="button" onClick={() => downloadJson()}><Download size={17} /> Export</button>
          <button type="button" onClick={resetTokens} className="ghost"><RotateCcw size={17} /> Reset</button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            onChange={importTokenFile}
            hidden
          />
        </div>

        <p className="status-line">{lastAction}</p>

        {controlGroups.map((group) => (
          <section className="control-group" key={group.title}>
            <h2>{group.title}</h2>
            {group.controls.map((control) => (
              <TokenControl
                key={control.path}
                control={control}
                value={readPath(tokens, control.path)}
                onChange={(value) => updateToken(control, value)}
              />
            ))}
          </section>
        ))}
      </aside>

      <section className="preview-panel">
        <div className="preview-header">
          <div>
            <p className="eyebrow">Live preview</p>
            <h2>Top bar alignment</h2>
          </div>
          <div className="alignment-readout">
            <span>Primary {metrics.primaryTitleStartDp}dp</span>
            <span>Secondary {metrics.secondaryTitleStartDp}dp</span>
            <strong>{metrics.deltaDp > 0 ? "-" : "+"}{Math.abs(metrics.deltaDp)}dp</strong>
          </div>
        </div>

        <div className="device-row">
          <PhonePreview
            tokens={tokens}
            metrics={metrics}
            selectedElement={selectedElement}
            onSelectElement={setSelectedElement}
            onMoveTitle={updateTokenByPath}
            onEditText={updateTokenByPath}
          />
          <div className="inspector-column">
            <ElementInspector selectedElement={selectedElement} tokens={tokens} metrics={metrics} />
            <TokenOutput tokens={tokens} />
          </div>
        </div>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
