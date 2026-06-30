use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceAnalysisFrameUploadRequest {
    pub device_installation_id: String,
    pub captured_at: i64,
    pub frame: CameraFramePayload,
    #[serde(default)]
    pub faces: Vec<FaceAnalysisFace>,
    pub processing: Option<FaceAnalysisProcessingMetrics>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CameraFramePayload {
    pub format: String,
    pub encoding: String,
    pub width: i32,
    pub height: i32,
    pub rotation_degrees: i32,
    pub byte_size: i32,
    pub data_base64: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceAnalysisFace {
    pub tracking_id: Option<i32>,
    pub bounding_box: FaceBoundingBox,
    pub head_euler_angle_x: Option<f32>,
    pub head_euler_angle_y: Option<f32>,
    pub head_euler_angle_z: Option<f32>,
    #[serde(default)]
    pub landmarks: Vec<FaceTopologyPoint>,
    #[serde(default)]
    pub contours: Vec<FaceTopologyPoint>,
    pub feature_point_count: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceBoundingBox {
    pub left: i32,
    pub top: i32,
    pub right: i32,
    pub bottom: i32,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceTopologyPoint {
    pub group: String,
    pub index: i32,
    pub x: f32,
    pub y: f32,
    pub z: Option<f32>,
    pub confidence: Option<f32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceAnalysisProcessingMetrics {
    pub frame_conversion_millis: i64,
    pub ml_kit_inference_millis: i64,
    pub upload_queued_at: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FaceAnalysisFrameUploadResponse {
    pub accepted: bool,
    pub id: String,
    pub received_at: i64,
}
