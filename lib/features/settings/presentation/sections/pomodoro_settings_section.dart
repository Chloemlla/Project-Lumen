import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_slider_tile.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class PomodoroSettingsSection extends StatelessWidget {
  const PomodoroSettingsSection({
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
          const AppSectionTitle('番茄设置'),
          AppSwitchTile(
            title: '启用番茄模式',
            value: settings.pomodoroEnabled,
            onChanged: (value) => controller.updatePomodoro(enabled: value),
          ),
          AppSliderTile(
            title: '专注时长',
            value: settings.pomodoroWorkMinutes.toDouble(),
            min: 15,
            max: 60,
            divisions: 9,
            labelBuilder: (value) => '${value.round()} 分钟',
            onChanged: (value) {
              controller.updatePomodoro(workMinutes: value.round());
            },
          ),
          AppSliderTile(
            title: '短休息',
            value: settings.pomodoroShortBreakMinutes.toDouble(),
            min: 3,
            max: 15,
            divisions: 12,
            labelBuilder: (value) => '${value.round()} 分钟',
            onChanged: (value) {
              controller.updatePomodoro(shortBreakMinutes: value.round());
            },
          ),
          AppSliderTile(
            title: '长休息',
            value: settings.pomodoroLongBreakMinutes.toDouble(),
            min: 10,
            max: 30,
            divisions: 10,
            labelBuilder: (value) => '${value.round()} 分钟',
            onChanged: (value) {
              controller.updatePomodoro(longBreakMinutes: value.round());
            },
          ),
          AppSwitchTile(
            title: '交互模式',
            value: settings.pomodoroInteractiveMode,
            onChanged: (value) =>
                controller.updatePomodoro(interactiveMode: value),
          ),
        ],
      ),
    );
  }
}
