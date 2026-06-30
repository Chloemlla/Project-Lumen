package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.PremiumFeature
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.TipTemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

internal class ProjectLumenTemplatesFeatureEntry(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val tipTemplateRepository: TipTemplateRepository,
) {
    fun selectTemplate(templateId: Long) {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            val template = tipTemplateRepository.get(templateId) ?: return@launch
            if (template.isPremium && !canUse(settings, PremiumFeature.PRO_TEMPLATES)) return@launch
            settingsRepository.update { it.copy(activeTipTemplateId = templateId) }
        }
    }

    fun updateTemplateSystemBackground(template: TipTemplateEntity, backgroundValue: String, primaryColor: String) {
        scope.launch {
            tipTemplateRepository.upsert(
                template.copy(
                    backgroundType = TemplateBackgroundType.SYSTEM.name,
                    backgroundValue = backgroundValue,
                    primaryColor = primaryColor,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateTemplateImage(template: TipTemplateEntity, imagePath: String) {
        scope.launch {
            tipTemplateRepository.upsert(
                template.copy(
                    imagePath = imagePath,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateTemplateContent(
        template: TipTemplateEntity,
        titleText: String,
        subtitleText: String,
        showSkipButton: Boolean,
    ) {
        scope.launch {
            tipTemplateRepository.upsert(
                template.copy(
                    titleText = titleText,
                    subtitleText = subtitleText,
                    showSkipButton = showSkipButton,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateTemplateCountdownStyle(template: TipTemplateEntity, countdownStyle: String) {
        scope.launch {
            val layoutJson = runCatching {
                JSONObject(template.layoutJson.takeIf { it.isNotBlank() } ?: "{}")
            }.getOrElse { JSONObject() }
                .put("countdownStyle", countdownStyle)
                .toString()
            tipTemplateRepository.upsert(
                template.copy(
                    layoutJson = layoutJson,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun seedDefaultTemplates() {
        val nowMillis = System.currentTimeMillis()
        DefaultTipTemplates.create(nowMillis).forEach { template ->
            val existing = tipTemplateRepository.get(template.id)
            if (existing == null) {
                tipTemplateRepository.upsert(template)
            } else if (existing.isBuiltin) {
                tipTemplateRepository.upsert(
                    existing.copy(
                        isPremium = template.isPremium,
                        sortOrder = template.sortOrder,
                        updatedAt = nowMillis,
                    ),
                )
            }
        }
    }

    private fun canUse(settings: AppSettingsEntity, feature: PremiumFeature): Boolean {
        val tier = PlanTier.entries.firstOrNull { it.name == settings.planTier } ?: PlanTier.FREE
        return when (feature) {
            PremiumFeature.PRO_TEMPLATES,
            PremiumFeature.ADVANCED_STATISTICS,
            PremiumFeature.LOCAL_BACKUP,
            PremiumFeature.MULTIPLE_REMINDER_PLANS,
            PremiumFeature.ADVANCED_EXPORT -> tier >= PlanTier.PRO
            PremiumFeature.CLOUD_SYNC -> tier >= PlanTier.PLUS
        }
    }
}
