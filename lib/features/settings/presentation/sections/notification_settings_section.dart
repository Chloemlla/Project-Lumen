import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class NotificationSettingsSection extends StatelessWidget {
  const NotificationSettingsSection({
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
          const AppSectionTitle('通知设置'),
          AppSwitchTile(
            title: '预提醒通知',
            value: settings.preAlertEnabled,
            onChanged: (value) =>
                controller.updateReminder(preAlertEnabled: value),
          ),
          AppSwitchTile(
            title: '到点自动开始休息',
            value: settings.timeoutAutoBreak,
            onChanged: (value) =>
                controller.updateReminder(timeoutAutoBreak: value),
          ),
          Text(
            '关闭预提醒后，只保留正式休息提醒。',
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}
