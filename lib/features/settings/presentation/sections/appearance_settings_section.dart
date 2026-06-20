import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class AppearanceSettingsSection extends StatelessWidget {
  const AppearanceSettingsSection({
    super.key,
    required this.settings,
    required this.controller,
    required this.onOpenTemplates,
  });

  final AppSettings settings;
  final SettingsController controller;
  final VoidCallback onOpenTemplates;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppSectionTitle(
            '外观设置',
            action: TextButton(
              onPressed: onOpenTemplates,
              child: const Text('模板中心'),
            ),
          ),
          AppSwitchTile(
            title: '自动深色模式',
            value: settings.useAutoDarkWindow,
            onChanged: (value) =>
                controller.updateAppearance(useAutoDarkWindow: value),
            subtitle: '根据系统时间窗口自动切换深色外观。',
          ),
          const SizedBox(height: 8),
          Text('当前模板 ID: ${settings.activeTipTemplateId ?? 1}'),
        ],
      ),
    );
  }
}
