import {Composition} from "remotion";
import {ProductAnimation} from "./ProductAnimation";
import {totalDurationInFrames} from "./data/androidDemoState";

export const videoFps = 30;
export const videoWidth = 1920;
export const videoHeight = 1080;

export function Root() {
  return (
    <Composition
      id="LumenAndroidProductAnimation"
      component={ProductAnimation}
      durationInFrames={totalDurationInFrames}
      fps={videoFps}
      width={videoWidth}
      height={videoHeight}
    />
  );
}
