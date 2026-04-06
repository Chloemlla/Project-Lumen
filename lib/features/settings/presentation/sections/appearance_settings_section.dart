import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class AppearanceSettingsSection extends StatelessWidget {
  const AppearanceSettingsSection({
    super.key,
    required this.settings,
    required this.onOpenTemplates,
  });

  final AppSettings settings;
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
            onChanged: (_) {},
            subtitle: 'v1 先保留入口，后续接时间段控制。',
          ),
          const SizedBox(height: 8),
          Text('当前模板 ID: ${settings.activeTipTemplateId ?? 1}'),
        ],
      ),
    );
  }
}
