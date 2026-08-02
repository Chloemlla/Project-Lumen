package com.projectlumen.app.app

import com.projectlumen.app.core.security.DeviceSecurityScanner

/**
 * ViewModel state holders for the device security scan feature.
 *
 * This is used by [DeviceSecurityScanCard] and managed by [ProjectLumenViewModel].
 */
data class SecurityScanUiState(
    val scanState: DeviceSecurityScanState = DeviceSecurityScanState.Idle,
    val scanner: DeviceSecurityScanner? = null,
)