import 'package:flutter/material.dart';
import 'package:project_lumen/core/enums/app_theme_mode.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class GeneralSettingsSection extends StatelessWidget {
  const GeneralSettingsSection({
    super.key,
    required this.settings,
    required this.controller,
  });

  final AppSettings settings;
  final SettingsController controller;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AppSectionTitle('通用设置'),
          DropdownButtonFormField<String>(
            initialValue: settings.languageCode,
            items: const [
              DropdownMenuItem(value: 'zh', child: Text('中文')),
              DropdownMenuItem(value: 'en', child: Text('English')),
            ],
            onChanged: (value) {
              if (value != null) {
                controller.setLanguage(value);
              }
            },
            decoration: const InputDecoration(labelText: '语言'),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<AppThemeMode>(
            initialValue: settings.themeMode,
            items: const [
              DropdownMenuItem(value: AppThemeMode.system, child: Text('跟随系统')),
              DropdownMenuItem(value: AppThemeMode.light, child: Text('浅色')),
              DropdownMenuItem(value: AppThemeMode.dark, child: Text('深色')),
            ],
            onChanged: (value) {
              if (value != null) {
                controller.setThemeMode(value);
              }
            },
            decoration: const InputDecoration(labelText: '主题'),
          ),
          const SizedBox(height: 12),
          AppSwitchTile(
            title: '记录统计',
            value: settings.statsEnabled,
            onChanged: (_) {},
            subtitle: 'v1 中统计默认启用，后续可接持久化开关。',
          ),
        ],
      ),
    );
  }
}
