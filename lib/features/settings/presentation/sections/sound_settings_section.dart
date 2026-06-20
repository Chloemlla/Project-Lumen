import 'package:flutter/material.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/domain/models/app_settings.dart';
import 'package:project_lumen/shared/widgets/app_card.dart';
import 'package:project_lumen/shared/widgets/app_section_title.dart';
import 'package:project_lumen/shared/widgets/app_switch_tile.dart';

class SoundSettingsSection extends StatelessWidget {
  const SoundSettingsSection({
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
          const AppSectionTitle('声音设置'),
          AppSwitchTile(
            title: '休息结束音效',
            value: settings.soundEnabled,
            onChanged: (value) =>
                controller.updateSound(soundEnabled: value),
          ),
          AppSwitchTile(
            title: '预提醒音效',
            value: settings.preAlertSoundEnabled,
            onChanged: (value) =>
                controller.updateSound(preAlertSoundEnabled: value),
          ),
          AppSwitchTile(
            title: '番茄开始音效',
            value: settings.pomodoroWorkStartSoundEnabled,
            onChanged: (value) => controller.updateSound(
              pomodoroWorkStartSoundEnabled: value,
            ),
          ),
          AppSwitchTile(
            title: '番茄结束音效',
            value: settings.pomodoroWorkEndSoundEnabled,
            onChanged: (value) => controller.updateSound(
              pomodoroWorkEndSoundEnabled: value,
            ),
          ),
        ],
      ),
    );
  }
}
