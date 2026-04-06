import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:project_lumen/features/settings/application/settings_controller.dart';
import 'package:project_lumen/features/settings/presentation/sections/appearance_settings_section.dart';
import 'package:project_lumen/features/settings/presentation/sections/general_settings_section.dart';
import 'package:project_lumen/features/settings/presentation/sections/notification_settings_section.dart';
import 'package:project_lumen/features/settings/presentation/sections/pomodoro_settings_section.dart';
import 'package:project_lumen/features/settings/presentation/sections/reminder_settings_section.dart';
import 'package:project_lumen/features/settings/presentation/sections/sound_settings_section.dart';
import 'package:project_lumen/shared/widgets/app_scaffold.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsControllerProvider);
    final controller = ref.read(settingsControllerProvider.notifier);

    return AppScaffold(
      title: '设置',
      location: '/settings',
      actions: [
        IconButton(
          onPressed: () => context.go('/about'),
          icon: const Icon(Icons.help_outline_rounded),
        ),
      ],
      body: ListView(
        children: [
          GeneralSettingsSection(settings: settings, controller: controller),
          const SizedBox(height: 16),
          ReminderSettingsSection(settings: settings, controller: controller),
          const SizedBox(height: 16),
          PomodoroSettingsSection(settings: settings, controller: controller),
          const SizedBox(height: 16),
          AppearanceSettingsSection(
            settings: settings,
            onOpenTemplates: () => context.go('/templates'),
          ),
          const SizedBox(height: 16),
          SoundSettingsSection(settings: settings),
          const SizedBox(height: 16),
          NotificationSettingsSection(settings: settings),
        ],
      ),
    );
  }
}
