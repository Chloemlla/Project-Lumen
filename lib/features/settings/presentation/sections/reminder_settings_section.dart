import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_slider_tile.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class ReminderSettingsSection extends StatelessWidget {
  const ReminderSettingsSection({
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
          const AppSectionTitle('提醒设置'),
          AppSwitchTile(
            title: '启用普通提醒',
            value: settings.reminderEnabled,
            onChanged: (value) => controller.updateReminder(enabled: value),
          ),
          AppSliderTile(
            title: '提醒间隔',
            value: settings.warnIntervalMinutes.toDouble(),
            min: 10,
            max: 90,
            divisions: 16,
            labelBuilder: (value) => '${value.round()} 分钟',
            onChanged: (value) {
              controller.updateReminder(warnIntervalMinutes: value.round());
            },
          ),
          AppSliderTile(
            title: '休息时长',
            value: settings.restDurationSeconds.toDouble(),
            min: 10,
            max: 300,
            divisions: 29,
            labelBuilder: (value) => '${value.round()} 秒',
            onChanged: (value) {
              controller.updateReminder(restDurationSeconds: value.round());
            },
          ),
          AppSwitchTile(
            title: '预提醒',
            value: settings.preAlertEnabled,
            onChanged: (value) =>
                controller.updateReminder(preAlertEnabled: value),
          ),
          AppSwitchTile(
            title: '休息前先询问',
            value: settings.askBeforeBreak,
            onChanged: (value) =>
                controller.updateReminder(askBeforeBreak: value),
          ),
          AppSwitchTile(
            title: '禁用跳过',
            value: settings.disableSkip,
            onChanged: (value) => controller.updateReminder(disableSkip: value),
          ),
        ],
      ),
    );
  }
}
