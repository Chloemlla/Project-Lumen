package com.project.lumen.open;

interface ILumenOpenApi {
    int getEyeFatigueLevel();
    long getContinuousScreenTime();
    boolean isRestingNow();

    void startFocusSession(String tag, long durationMs);
    void stopFocusSession();
    void triggerEyeRelaxation();
}
