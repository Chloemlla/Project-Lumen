from pathlib import Path
p = Path('backend/src/store/privileged_control.rs')
t = p.read_text(encoding='utf-8')
t = t.replace(
'''        let id = Uuid::new_v4().to_string();
        let request_pipeline = pipeline.clone();
        let request_surface_attached = surface_attached;
        self.vision_stream_frames
            .insert_one(
                VisionStreamFrameRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    session_id: session_id.clone(),
                    device_installation_id,
                    received_at: now,
                    exclusive_access: request.exclusive_access,
                    no_surface_preview: request.no_surface_preview,
                    pipeline: request_pipeline.clone(),
                    surface_attached: request_surface_attached,
                    payload: request,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        session.frames_uploaded = session.frames_uploaded.saturating_add(1);
        session.frames_captured = session.frames_captured.max(session.frames_uploaded);
        session.last_heartbeat_at = now;
        session.exclusive_held = true;
        session.surface_detached = !request_surface_attached;
''',
'''        let id = Uuid::new_v4().to_string();
        let request_pipeline = pipeline.clone();
        let request_surface_attached = surface_attached;
        let exclusive_access = request.exclusive_access;
        let no_surface_preview = request.no_surface_preview;
        self.vision_stream_frames
            .insert_one(
                VisionStreamFrameRecord {
                    id: id.clone(),
                    user_id: user_id.to_owned(),
                    session_id: session_id.clone(),
                    device_installation_id,
                    received_at: now,
                    exclusive_access,
                    no_surface_preview,
                    pipeline: request_pipeline.clone(),
                    surface_attached: request_surface_attached,
                    payload: request,
                },
                None,
            )
            .await
            .map_err(database_error)?;

        session.frames_uploaded = session.frames_uploaded.saturating_add(1);
        session.frames_captured = session.frames_captured.max(session.frames_uploaded);
        session.last_heartbeat_at = now;
        session.exclusive_held = exclusive_access;
        session.surface_detached = !request_surface_attached;
'''
)
p.write_text(t, encoding='utf-8')
print('store exclusive fix ok')
